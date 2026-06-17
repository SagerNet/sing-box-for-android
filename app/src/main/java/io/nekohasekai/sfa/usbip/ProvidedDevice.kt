package io.nekohasekai.sfa.usbip

enum class ProvidedDeviceState { ATTACHING, READY, ERROR }

data class ProvidedDevice(
    val deviceId: String,
    val serverTag: String,
    val label: String,
    val vendorId: Int,
    val productId: Int,
    val state: ProvidedDeviceState,
    val busId: String? = null,
    val error: String? = null,
    val usbDeviceName: String? = null,
)

data class USBIPProviderState(
    val devices: List<ProvidedDevice> = emptyList(),
    val endErrors: Map<String, String> = emptyMap(),
)
