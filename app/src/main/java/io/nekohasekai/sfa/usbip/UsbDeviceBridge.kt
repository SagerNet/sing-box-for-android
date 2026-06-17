package io.nekohasekai.sfa.usbip

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.USBDeviceDescriptor
import io.nekohasekai.libbox.USBURBRequest
import io.nekohasekai.libbox.USBURBResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UsbDeviceBridge private constructor(
    val deviceId: String,
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val send: (USBURBResponse) -> Unit,
) {
    private val claimedInterfaces = ArrayList<UsbInterface>()
    private val endpoints = ConcurrentHashMap<Int, UsbEndpoint>()
    private val executors = ConcurrentHashMap<Int, ExecutorService>()

    @Volatile
    private var closed = false

    private fun start(serverTag: String): USBDeviceDescriptor {
        val configuration = if (device.configurationCount > 0) device.getConfiguration(0) else null
        if (configuration != null) {
            claimConfiguration(configuration)
        }
        return buildDescriptor(serverTag, configuration)
    }

    private fun claimConfiguration(configuration: UsbConfiguration) {
        releaseInterfaces()
        endpoints.clear()
        for (index in 0 until configuration.interfaceCount) {
            val iface = configuration.getInterface(index)
            if (connection.claimInterface(iface, true)) {
                claimedInterfaces.add(iface)
                indexEndpoints(iface)
            }
        }
    }

    private fun indexEndpoints(iface: UsbInterface) {
        for (index in 0 until iface.endpointCount) {
            val endpoint = iface.getEndpoint(index)
            endpoints[endpoint.endpointNumber or endpoint.direction] = endpoint
        }
    }

    private fun buildDescriptor(serverTag: String, configuration: UsbConfiguration?): USBDeviceDescriptor {
        val raw = connection.rawDescriptors
        val (busNum, devNum) = parseBusDevice(device.deviceName)
        val descriptor = Libbox.newUSBDeviceDescriptor(serverTag, deviceId)
        descriptor.setBusNum(busNum)
        descriptor.setDevNum(devNum)
        descriptor.setSpeed(inferSpeed(readWord(raw, 2)))
        descriptor.setVendorID(device.vendorId)
        descriptor.setProductID(device.productId)
        descriptor.setBCDDevice(readWord(raw, 12))
        descriptor.setDeviceClass(device.deviceClass)
        descriptor.setDeviceSubClass(device.deviceSubclass)
        descriptor.setDeviceProtocol(device.deviceProtocol)
        descriptor.setConfigurationValue(configuration?.id ?: 0)
        descriptor.setNumConfigurations(device.configurationCount)
        descriptor.setSerial(serialNumber())
        descriptor.setProduct(device.productName ?: "")
        if (configuration != null) {
            val seen = HashSet<Int>()
            for (index in 0 until configuration.interfaceCount) {
                val iface = configuration.getInterface(index)
                if (seen.add(iface.id)) {
                    descriptor.addInterface(iface.interfaceClass, iface.interfaceSubclass, iface.interfaceProtocol)
                }
            }
        }
        return descriptor
    }

    private fun serialNumber(): String = try {
        device.serialNumber ?: ""
    } catch (_: SecurityException) {
        ""
    }

    fun submit(request: USBURBRequest) {
        if (closed) return
        val endpointNumber = request.endpoint and 0x0f
        executors.getOrPut(endpointNumber) { Executors.newSingleThreadExecutor() }
            .execute { execute(request, endpointNumber) }
    }

    private fun execute(request: USBURBRequest, endpointNumber: Int) {
        if (closed) return
        val response = Libbox.newUSBURBResponse(deviceId, request.seq)
        try {
            when {
                endpointNumber == 0 -> executeControl(request, response)
                request.numberOfPackets > 0 -> response.setStatus(URB_EPROTO)
                else -> executeBulk(request, endpointNumber, response)
            }
        } catch (e: Throwable) {
            response.setStatus(URB_EPROTO)
        }
        if (!closed) send(response)
    }

    private fun executeControl(request: USBURBRequest, response: USBURBResponse) {
        val setup = UsbSetup(request.setup)
        if (!setup.directionIn && setup.isStandard && handleManagedControl(setup)) {
            response.setStatus(URB_OK)
            response.setActualLength(0)
            return
        }
        if (setup.directionIn) {
            val buffer = ByteArray(request.transferBufferLength)
            val transferred =
                connection.controlTransfer(
                    setup.requestType,
                    setup.request,
                    setup.value,
                    setup.index,
                    buffer,
                    buffer.size,
                    CONTROL_TIMEOUT_MS,
                )
            applyResult(response, transferred, buffer)
        } else {
            val out = request.outData
            val transferred =
                connection.controlTransfer(
                    setup.requestType,
                    setup.request,
                    setup.value,
                    setup.index,
                    out,
                    out.size,
                    CONTROL_TIMEOUT_MS,
                )
            applyResult(response, transferred, null)
        }
    }

    private fun handleManagedControl(setup: UsbSetup): Boolean {
        when (setup.request) {
            USB_REQUEST_SET_CONFIGURATION -> {
                val configuration = findConfiguration(setup.value and 0xff) ?: return false
                connection.setConfiguration(configuration)
                claimConfiguration(configuration)
                return true
            }
            USB_REQUEST_SET_INTERFACE -> {
                val iface = findInterface(setup.index and 0xff, setup.value and 0xff) ?: return false
                connection.setInterface(iface)
                indexEndpoints(iface)
                return true
            }
        }
        return false
    }

    private fun executeBulk(request: USBURBRequest, endpointNumber: Int, response: USBURBResponse) {
        val direction = if (request.directionIn) USB_DIR_IN else 0
        val endpoint = endpoints[endpointNumber or direction]
        if (endpoint == null) {
            response.setStatus(URB_EPIPE)
            return
        }
        if (request.directionIn) {
            val buffer = ByteArray(request.transferBufferLength)
            val transferred = connection.bulkTransfer(endpoint, buffer, buffer.size, TRANSFER_TIMEOUT_MS)
            applyResult(response, transferred, buffer)
        } else {
            val out = request.outData
            val transferred = connection.bulkTransfer(endpoint, out, out.size, TRANSFER_TIMEOUT_MS)
            applyResult(response, transferred, null)
        }
    }

    private fun applyResult(response: USBURBResponse, transferred: Int, inData: ByteArray?) {
        if (transferred < 0) {
            response.setStatus(URB_EPROTO)
            return
        }
        response.setStatus(URB_OK)
        response.setActualLength(transferred)
        if (inData != null) {
            response.setInData(if (transferred == inData.size) inData else inData.copyOf(transferred))
        }
    }

    private fun findConfiguration(value: Int): UsbConfiguration? {
        for (index in 0 until device.configurationCount) {
            val configuration = device.getConfiguration(index)
            if (configuration.id == value) return configuration
        }
        return null
    }

    private fun findInterface(interfaceNumber: Int, altSetting: Int): UsbInterface? {
        for (index in 0 until device.interfaceCount) {
            val iface = device.getInterface(index)
            if (iface.id == interfaceNumber && iface.alternateSetting == altSetting) return iface
        }
        return null
    }

    private fun releaseInterfaces() {
        for (iface in claimedInterfaces) {
            try {
                connection.releaseInterface(iface)
            } catch (_: Throwable) {
            }
        }
        claimedInterfaces.clear()
    }

    fun close() {
        if (closed) return
        closed = true
        for (executor in executors.values) executor.shutdownNow()
        executors.clear()
        releaseInterfaces()
        try {
            connection.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun open(
            deviceId: String,
            serverTag: String,
            manager: UsbManager,
            device: UsbDevice,
            send: (USBURBResponse) -> Unit,
        ): Pair<UsbDeviceBridge, USBDeviceDescriptor> {
            val connection = manager.openDevice(device) ?: throw IllegalStateException("open device failed")
            val bridge = UsbDeviceBridge(deviceId, device, connection, send)
            try {
                return bridge to bridge.start(serverTag)
            } catch (e: Throwable) {
                bridge.close()
                throw e
            }
        }

        // UsbDevice.getDeviceName() is the usbfs node "/dev/bus/usb/BBB/DDD".
        private fun parseBusDevice(name: String): Pair<Int, Int> {
            val parts = name.split("/")
            val devNum = parts.getOrNull(parts.size - 1)?.toIntOrNull() ?: 0
            val busNum = parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: 0
            return busNum to devNum
        }

        private fun readWord(raw: ByteArray?, offset: Int): Int {
            if (raw == null || raw.size < offset + 2) return 0
            return (raw[offset].toInt() and 0xff) or ((raw[offset + 1].toInt() and 0xff) shl 8)
        }

        private fun inferSpeed(bcdUSB: Int): Int = when {
            (bcdUSB shr 8) and 0xff >= 3 -> 5
            (bcdUSB shr 8) and 0xff >= 2 -> 3
            else -> 2
        }

        private const val CONTROL_TIMEOUT_MS = 5000

        // Block until the endpoint completes; detach closes the fd to unblock waiters.
        private const val TRANSFER_TIMEOUT_MS = 0
    }
}
