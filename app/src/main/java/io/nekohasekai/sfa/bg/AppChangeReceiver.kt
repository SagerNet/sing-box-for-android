package io.nekohasekai.sfa.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ui.profileoverride.PerAppProxyActivity

class AppChangeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AppChangeReceiver"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Log.d(TAG, "onReceive: ${intent.action}")
        checkUpdate(intent)
    }

    private fun checkUpdate(intent: Intent) {
        if (!Settings.perAppProxyEnabled) {
            Log.d(TAG, "per app proxy disabled")
            return
        }
        if (!Settings.perAppProxyManagedMode) {
            Log.d(TAG, "managed mode disabled")
            return
        }
        val packageName = intent.dataString?.substringAfter("package:")
        if (packageName.isNullOrBlank()) {
            Log.d(TAG, "missing package name in intent")
            return
        }
        val isChinaApp = PerAppProxyActivity.scanChinaPackage(packageName)
        Log.d(TAG, "scan china app result for $packageName: $isChinaApp")
        if (isChinaApp) {
            Settings.perAppProxyManagedList += packageName
            Log.d(TAG, "added to managed list")
        } else {
            Settings.perAppProxyManagedList -= packageName
            Log.d(TAG, "removed from managed list")
        }
    }
}
