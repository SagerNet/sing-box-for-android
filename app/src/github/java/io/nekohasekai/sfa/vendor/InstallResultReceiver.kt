package io.nekohasekai.sfa.vendor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import io.nekohasekai.sfa.update.UpdateState

class InstallResultReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_INSTALL_COMPLETE = "io.nekohasekai.sfa.INSTALL_COMPLETE"
        private const val TAG = "InstallResultReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_COMPLETE) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        Log.d(TAG, "Install result: status=$status, message=$message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.d(TAG, "Installation successful")
                UpdateState.setInstallStatus(UpdateState.InstallStatus.Success)
            }
            else -> {
                Log.e(TAG, "Installation failed: $status - $message")
                UpdateState.setInstallStatus(UpdateState.InstallStatus.Failed(message ?: "Unknown error"))
            }
        }
    }
}
