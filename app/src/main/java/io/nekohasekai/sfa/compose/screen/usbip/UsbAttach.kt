package io.nekohasekai.sfa.compose.screen.usbip

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.nekohasekai.sfa.usbip.USBIPManager

private const val ACTION_USB_PERMISSION = "io.nekohasekai.sfa.action.USB_PERMISSION"

@Composable
fun rememberUsbAttacher(serverTag: String): (UsbDevice) -> Unit {
    val context = LocalContext.current
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    DisposableEffect(serverTag) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(received: Context, intent: Intent) {
                    if (intent.action != ACTION_USB_PERMISSION) return
                    val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        USBIPManager.attach(received, serverTag, device)
                    }
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    return remember(serverTag) {
        { device: UsbDevice ->
            if (usbManager.hasPermission(device)) {
                USBIPManager.attach(context, serverTag, device)
            } else {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                        flags,
                    )
                usbManager.requestPermission(device, pendingIntent)
            }
        }
    }
}
