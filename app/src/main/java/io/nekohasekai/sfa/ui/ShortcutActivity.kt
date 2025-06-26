package io.nekohasekai.sfa.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.bg.ServiceConnection
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Status

class ShortcutActivity : Activity(), ServiceConnection.Callback {
    private val connection = ServiceConnection(this, this, false)
    private var pendingAction: String? = null

    private data class Shortcut(val label: String, val action: String, val icon: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            Intent.ACTION_CREATE_SHORTCUT -> showShortcutDialog()
            Action.QUICK_TOGGLE, Action.QUICK_START, Action.QUICK_STOP -> {
                pendingAction = intent.action
                connection.connect()
                reportShortcutUsed()
            }

            else -> finish()
        }
    }

    private fun showShortcutDialog() {
        val shortcuts = listOf(
            Shortcut(getString(R.string.quick_toggle), Action.QUICK_TOGGLE, R.mipmap.ic_launcher),
            Shortcut(getString(R.string.quick_start), Action.QUICK_START, R.mipmap.ic_launcher),
            Shortcut(getString(R.string.quick_stop), Action.QUICK_STOP, R.mipmap.ic_launcher)
        )

        val labels = shortcuts.map { it.label }.toTypedArray()

        val itemClickListener = { _: android.content.DialogInterface, index: Int ->
            val shortcut = shortcuts[index]
            val shortcutIntent = Intent(this, ShortcutActivity::class.java).apply {
                action = shortcut.action
            }
            val shortcutInfo =
                ShortcutInfoCompat
                    .Builder(this, shortcut.action)
                    .setIntent(shortcutIntent)
                    .setIcon(IconCompat.createWithResource(this, shortcut.icon))
                    .setShortLabel(shortcut.label)
                    .build()
            val resultIntent = ShortcutManagerCompat.createShortcutResultIntent(this, shortcutInfo)
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_shortcuts))
            .setItems(labels, itemClickListener)
            .setOnCancelListener { finish() }
            .show()
    }

    private fun reportShortcutUsed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            pendingAction?.let {
                getSystemService<ShortcutManager>()?.reportShortcutUsed(it)
            }
        }
    }

    override fun onServiceStatusChanged(status: Status) {
        when (pendingAction) {
            Action.QUICK_TOGGLE -> when (status) {
                Status.Started -> BoxService.stop()
                Status.Stopped -> BoxService.start()
                else -> {}
            }

            Action.QUICK_START -> if (status == Status.Stopped) BoxService.start()
            Action.QUICK_STOP -> if (status == Status.Started) BoxService.stop()
        }
        finish()
    }

    override fun onDestroy() {
        connection.disconnect()
        super.onDestroy()
    }
}