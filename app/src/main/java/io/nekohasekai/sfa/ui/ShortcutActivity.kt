package io.nekohasekai.sfa.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.bg.ServiceConnection
import io.nekohasekai.sfa.constant.Status

class ShortcutActivity :
    Activity(),
    ServiceConnection.Callback {
    companion object {
        private const val SHORTCUT_TOGGLE = "toggle"
    }

    private val connection = ServiceConnection(this, this, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            setResult(
                RESULT_OK,
                ShortcutManagerCompat.createShortcutResultIntent(
                    this,
                    ShortcutInfoCompat.Builder(this, SHORTCUT_TOGGLE)
                        .setIntent(Intent(this, ShortcutActivity::class.java).setAction(Intent.ACTION_MAIN))
                        .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                        .setShortLabel(getString(R.string.action_toggle))
                        .build(),
                ),
            )
            finish()
            return
        }
        val keyguardManager = getSystemService<KeyguardManager>()!!
        if (!keyguardManager.isKeyguardLocked) {
            toggle()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        toggle()
                    }

                    override fun onDismissCancelled() {
                        finish()
                    }

                    override fun onDismissError() {
                        finish()
                    }
                },
            )
        } else {
            finish()
        }
    }

    private fun toggle() {
        ShortcutManagerCompat.reportShortcutUsed(this, SHORTCUT_TOGGLE)
        connection.connect()
    }

    override fun onServiceStatusChanged(status: Status) {
        when (status) {
            Status.Started -> BoxService.stop()
            Status.Stopped -> BoxService.start()
            else -> {}
        }
        finish()
    }

    override fun onDestroy() {
        connection.disconnect()
        super.onDestroy()
    }
}
