package io.nekohasekai.sfa.usbip

// Linux URB completion status conventions (negative errno) the usbip-server
// translates back to the remote client.
const val URB_OK = 0
const val URB_EPIPE = -32 // stalled endpoint
const val URB_EOVERFLOW = -75 // babble / buffer overrun
const val URB_EPROTO = -71 // transport or protocol failure

// Standard control requests the importing kernel issues during enumeration. Android
// forwards control transfers raw, but SET_CONFIGURATION / SET_INTERFACE must also go
// through UsbDeviceConnection so the host kernel's claimed-interface state stays in sync.
const val USB_REQUEST_SET_CONFIGURATION = 0x09
const val USB_REQUEST_SET_INTERFACE = 0x0b

const val USB_TYPE_STANDARD = 0x00
const val USB_TYPE_MASK = 0x60
const val USB_DIR_IN = 0x80

class UsbSetup(setup: ByteArray) {
    val requestType: Int = setup[0].toInt() and 0xff
    val request: Int = setup[1].toInt() and 0xff
    val value: Int = (setup[2].toInt() and 0xff) or ((setup[3].toInt() and 0xff) shl 8)
    val index: Int = (setup[4].toInt() and 0xff) or ((setup[5].toInt() and 0xff) shl 8)
    val length: Int = (setup[6].toInt() and 0xff) or ((setup[7].toInt() and 0xff) shl 8)

    val directionIn: Boolean get() = requestType and USB_DIR_IN != 0
    val isStandard: Boolean get() = requestType and USB_TYPE_MASK == USB_TYPE_STANDARD
}
