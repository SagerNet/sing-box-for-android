package io.nekohasekai.sfa.usbip

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import io.nekohasekai.libbox.USBProviderHandler
import io.nekohasekai.libbox.USBProviderSession
import io.nekohasekai.libbox.USBURBRequest
import io.nekohasekai.libbox.USBURBResponse
import io.nekohasekai.sfa.bg.USBIPService
import io.nekohasekai.sfa.utils.CommandTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

object USBIPManager {
    private val access = Any()
    private val sessions = HashMap<String, ServerSession>()
    private val endErrors = HashMap<String, String>()
    private val counter = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var serviceStarted = false

    private val _state = MutableStateFlow(USBIPProviderState())
    val state: StateFlow<USBIPProviderState> = _state.asStateFlow()

    private class ServerSession(val serverTag: String, val session: USBProviderSession) {
        val bridges = HashMap<String, UsbDeviceBridge>()
        val devices = HashMap<String, ProvidedDevice>()

        @Volatile
        var closed = false
    }

    fun attach(context: Context, serverTag: String, device: UsbDevice) {
        val appContext = context.applicationContext
        scope.launch { attachInternal(appContext, serverTag, device) }
    }

    fun detach(deviceId: String) {
        scope.launch { detachInternal(deviceId) }
    }

    fun detachByDevice(device: UsbDevice) {
        val deviceId =
            synchronized(access) {
                sessions.values.flatMap { it.devices.values }.firstOrNull { it.usbDeviceName == device.deviceName }?.deviceId
            } ?: return
        detach(deviceId)
    }

    fun shutdown() {
        scope.launch {
            val current = synchronized(access) { sessions.values.toList().also { sessions.clear() } }
            for (session in current) {
                session.closed = true
                for (bridge in session.bridges.values) bridge.close()
                runCatching { session.session.close() }
            }
            synchronized(access) { endErrors.clear() }
            publish()
        }
    }

    fun onServiceDestroyed() {
        serviceStarted = false
    }

    private fun attachInternal(context: Context, serverTag: String, device: UsbDevice) {
        val deviceId = "dev-${counter.incrementAndGet()}"
        try {
            val session = ensureSession(serverTag)
            synchronized(access) { session.devices[deviceId] = providedDevice(deviceId, serverTag, device) }
            publish()
            startServiceIfNeeded(context)
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val (bridge, descriptor) =
                UsbDeviceBridge.open(deviceId, serverTag, manager, device) { response -> sendResponse(session, response) }
            val kept =
                synchronized(access) {
                    if (session.devices.containsKey(deviceId)) {
                        session.bridges[deviceId] = bridge
                        true
                    } else {
                        false
                    }
                }
            if (!kept) {
                bridge.close()
                return
            }
            session.session.attachDevice(descriptor)
        } catch (e: Throwable) {
            markError(serverTag, deviceId, e.message ?: "attach failed")
        }
    }

    private fun detachInternal(deviceId: String) {
        val session = synchronized(access) { sessions.values.firstOrNull { it.devices.containsKey(deviceId) } } ?: return
        val bridge = synchronized(access) {
            session.devices.remove(deviceId)
            session.bridges.remove(deviceId)
        }
        runCatching { session.session.detachDevice(deviceId) }
        bridge?.close()
        publish()
        closeSessionIfEmpty(session)
    }

    // The libbox session is opened outside the lock: provideUSBDevices dials the daemon
    // and would otherwise block URB dispatch for already-open sessions.
    private fun ensureSession(serverTag: String): ServerSession {
        synchronized(access) { sessions[serverTag]?.let { return it } }
        val libboxSession = CommandTarget.standaloneClient().provideUSBDevices(handler(serverTag))
        synchronized(access) {
            sessions[serverTag]?.let {
                runCatching { libboxSession.close() }
                return it
            }
            val session = ServerSession(serverTag, libboxSession)
            sessions[serverTag] = session
            return session
        }
    }

    private fun providedDevice(deviceId: String, serverTag: String, device: UsbDevice) = ProvidedDevice(
        deviceId = deviceId,
        serverTag = serverTag,
        label = device.productName ?: formatVidPid(device.vendorId, device.productId),
        vendorId = device.vendorId,
        productId = device.productId,
        state = ProvidedDeviceState.ATTACHING,
        usbDeviceName = device.deviceName,
    )

    private fun handler(serverTag: String): USBProviderHandler = object : USBProviderHandler {
        override fun onReady(deviceID: String, busID: String) = onDeviceReady(serverTag, deviceID, busID)

        override fun onURBRequest(request: USBURBRequest) = dispatchUrb(serverTag, request)

        override fun onAbort(deviceID: String, endpoint: Int) {}

        override fun onError(deviceID: String, message: String) = onProviderError(serverTag, deviceID, message)
    }

    private fun sendResponse(session: ServerSession, response: USBURBResponse) {
        if (session.closed) return
        runCatching { session.session.sendURBResponse(response) }
    }

    private fun dispatchUrb(serverTag: String, request: USBURBRequest) {
        val bridge = synchronized(access) { sessions[serverTag]?.bridges?.get(request.deviceID) } ?: return
        bridge.submit(request)
    }

    private fun onDeviceReady(serverTag: String, deviceId: String, busId: String) {
        synchronized(access) {
            val session = sessions[serverTag] ?: return@synchronized
            val device = session.devices[deviceId] ?: return@synchronized
            session.devices[deviceId] = device.copy(state = ProvidedDeviceState.READY, busId = busId, error = null)
        }
        publish()
    }

    private fun onProviderError(serverTag: String, deviceId: String, message: String) {
        val session = synchronized(access) { sessions[serverTag] } ?: return
        if (session.closed) return
        if (deviceId.isNotEmpty()) {
            markError(serverTag, deviceId, message)
            val bridge = synchronized(access) { session.bridges.remove(deviceId) }
            bridge?.close()
            return
        }
        session.closed = true
        val bridges =
            synchronized(access) {
                endErrors[serverTag] = message
                session.devices.keys.forEach { id ->
                    val device = session.devices.getValue(id)
                    if (device.state != ProvidedDeviceState.ERROR) {
                        session.devices[id] = device.copy(state = ProvidedDeviceState.ERROR, error = message)
                    }
                }
                session.bridges.values.toList().also { session.bridges.clear() }
            }
        for (bridge in bridges) bridge.close()
        runCatching { session.session.close() }
        synchronized(access) { sessions.remove(serverTag) }
        publish()
        stopServiceIfIdle()
    }

    private fun markError(serverTag: String, deviceId: String, message: String) {
        synchronized(access) {
            val session = sessions[serverTag]
            val device = session?.devices?.get(deviceId)
            if (session != null && device != null) {
                session.devices[deviceId] = device.copy(state = ProvidedDeviceState.ERROR, error = message)
            } else {
                endErrors[serverTag] = message
            }
        }
        publish()
    }

    private fun closeSessionIfEmpty(session: ServerSession) {
        val empty =
            synchronized(access) {
                if (session.devices.isEmpty()) {
                    session.closed = true
                    sessions.remove(session.serverTag)
                    endErrors.remove(session.serverTag)
                    true
                } else {
                    false
                }
            }
        if (empty) {
            runCatching { session.session.close() }
            publish()
            stopServiceIfIdle()
        }
    }

    private fun publish() {
        val snapshot =
            synchronized(access) {
                USBIPProviderState(
                    devices = sessions.values.flatMap { it.devices.values }.sortedBy { it.deviceId },
                    endErrors = HashMap(endErrors),
                )
            }
        _state.value = snapshot
    }

    private fun startServiceIfNeeded(context: Context) {
        if (serviceStarted) return
        serviceStarted = true
        ContextCompat.startForegroundService(context, Intent(context, USBIPService::class.java))
    }

    private fun stopServiceIfIdle() {
        val idle = synchronized(access) { sessions.isEmpty() }
        if (idle) serviceStarted = false
    }
}
