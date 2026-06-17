package io.nekohasekai.sfa.compose.screen.usbip

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.USBIPServerStatus
import io.nekohasekai.libbox.USBIPServerStatusHandler
import io.nekohasekai.libbox.USBIPServerStatusSubscription
import io.nekohasekai.libbox.USBIPServerStatusUpdate
import io.nekohasekai.libbox.USBSharedDevice
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.utils.CommandTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class UsbSharedInterfaceData(
    val interfaceClass: Int,
    val interfaceSubClass: Int,
    val interfaceProtocol: Int,
)

data class UsbSharedDeviceData(
    val busId: String,
    val stableId: String,
    val backend: Int,
    val state: Int,
    val deviceId: String,
    val busNum: Int,
    val devNum: Int,
    val speed: Int,
    val vendorId: Int,
    val productId: Int,
    val bcdDevice: Int,
    val deviceClass: Int,
    val deviceSubClass: Int,
    val deviceProtocol: Int,
    val configurationValue: Int,
    val numConfigurations: Int,
    val serial: String,
    val product: String,
    val interfaces: List<UsbSharedInterfaceData>,
) {
    val key: String get() = stableId.ifEmpty { busId }
}

data class UsbipServerData(
    val serverTag: String,
    val devices: List<UsbSharedDeviceData>,
)

data class USBIPStatusState(
    val servers: List<UsbipServerData> = emptyList(),
    val isSubscribed: Boolean = false,
)

class USBIPStatusViewModel : BaseViewModel<USBIPStatusState, Nothing>() {
    private var subscription: USBIPServerStatusSubscription? = null

    override fun createInitialState() = USBIPStatusState()

    fun subscribe() {
        if (currentState.isSubscribed) return
        updateState { copy(isSubscribed = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                subscription =
                    CommandTarget.standaloneClient().subscribeUSBIPServerStatus(
                        object : USBIPServerStatusHandler {
                            override fun onStatusUpdate(status: USBIPServerStatusUpdate) {
                                val servers = convertUpdate(status)
                                viewModelScope.launch {
                                    if (!currentState.isSubscribed) return@launch
                                    updateState { copy(servers = servers) }
                                }
                            }

                            override fun onError(message: String) {
                                viewModelScope.launch {
                                    if (!currentState.isSubscribed) return@launch
                                    updateState { copy(servers = emptyList(), isSubscribed = false) }
                                    subscription = null
                                    sendErrorMessage(message)
                                }
                            }
                        },
                    )
            } catch (_: Exception) {
                viewModelScope.launch {
                    updateState { copy(servers = emptyList(), isSubscribed = false) }
                    subscription = null
                }
            }
        }
    }

    fun cancel() {
        try {
            subscription?.close()
        } catch (_: Exception) {
        }
        subscription = null
        updateState { copy(servers = emptyList(), isSubscribed = false) }
    }

    fun server(tag: String): UsbipServerData? = currentState.servers.firstOrNull { it.serverTag == tag }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    private fun convertUpdate(status: USBIPServerStatusUpdate): List<UsbipServerData> {
        val servers = mutableListOf<UsbipServerData>()
        val iterator = status.servers()
        while (iterator.hasNext()) {
            servers.add(convertServer(iterator.next()))
        }
        return servers
    }

    private fun convertServer(server: USBIPServerStatus): UsbipServerData {
        val devices = mutableListOf<UsbSharedDeviceData>()
        val iterator = server.devices()
        while (iterator.hasNext()) {
            devices.add(convertDevice(iterator.next()))
        }
        return UsbipServerData(serverTag = server.serverTag, devices = devices)
    }

    private fun convertDevice(device: USBSharedDevice): UsbSharedDeviceData {
        val interfaces = mutableListOf<UsbSharedInterfaceData>()
        val iterator = device.interfaces()
        while (iterator.hasNext()) {
            val iface = iterator.next()
            interfaces.add(
                UsbSharedInterfaceData(
                    interfaceClass = iface.interfaceClass,
                    interfaceSubClass = iface.interfaceSubClass,
                    interfaceProtocol = iface.interfaceProtocol,
                ),
            )
        }
        return UsbSharedDeviceData(
            busId = device.busID,
            stableId = device.stableID,
            backend = device.backend,
            state = device.state,
            deviceId = device.deviceID,
            busNum = device.busNum,
            devNum = device.devNum,
            speed = device.speed,
            vendorId = device.vendorID,
            productId = device.productID,
            bcdDevice = device.getBCDDevice(),
            deviceClass = device.deviceClass,
            deviceSubClass = device.deviceSubClass,
            deviceProtocol = device.deviceProtocol,
            configurationValue = device.configurationValue,
            numConfigurations = device.numConfigurations,
            serial = device.serial,
            product = device.product,
            interfaces = interfaces,
        )
    }
}
