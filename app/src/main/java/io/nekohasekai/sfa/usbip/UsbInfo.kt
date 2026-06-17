package io.nekohasekai.sfa.usbip

private val USB_CLASS_NAMES =
    mapOf(
        0x01 to "Audio",
        0x02 to "CDC Control",
        0x03 to "HID",
        0x05 to "Physical",
        0x06 to "Image",
        0x07 to "Printer",
        0x08 to "Mass Storage",
        0x09 to "Hub",
        0x0a to "CDC Data",
        0x0b to "Smart Card",
        0x0d to "Content Security",
        0x0e to "Video",
        0x0f to "Personal Healthcare",
        0x10 to "Audio/Video",
        0x11 to "Billboard",
        0x12 to "USB-C Bridge",
        0xdc to "Diagnostic",
        0xe0 to "Wireless",
        0xef to "Miscellaneous",
        0xfe to "Application Specific",
        0xff to "Vendor Specific",
    )

private fun hex2(value: Int): String = "0x" + (value and 0xff).toString(16).padStart(2, '0')

fun usbClassName(code: Int): String? = if (code == 0) null else USB_CLASS_NAMES[code] ?: hex2(code)

fun usbClassTriplet(cls: Int, sub: Int, proto: Int): String {
    val name = usbClassName(cls) ?: hex2(cls)
    return if (sub > 0 || proto > 0) "$name · ${hex2(sub)} · ${hex2(proto)}" else name
}

private val USB_SPEED_LABELS =
    mapOf(
        1 to "Low Speed",
        2 to "Full Speed",
        3 to "High Speed",
        4 to "Wireless",
        5 to "SuperSpeed",
        6 to "SuperSpeed+",
    )

fun usbSpeedLabel(code: Int): String? = USB_SPEED_LABELS[code]

fun bcdToVersion(bcd: Int): String = "${(bcd shr 8) and 0xff}.${(bcd shr 4) and 0x0f}${bcd and 0x0f}"

fun formatVidPid(vendorId: Int, productId: Int): String {
    fun hex4(value: Int) = (value and 0xffff).toString(16).padStart(4, '0')
    return "${hex4(vendorId)}:${hex4(productId)}"
}

fun usbBackendLabel(backend: Int): String? = when (backend) {
    1 -> "linux-sysfs"
    2 -> "dynamic"
    3 -> "darwin-iokit"
    4 -> "windows-vboxusb"
    else -> null
}
