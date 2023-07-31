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

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")
        checkUpdate(context, intent)
    }

    private fun checkUpdate(context: Context, intent: Intent) {
        if (!Settings.perAppProxyEnabled) {
            Log.d(TAG, "per app proxy disabled")
            return
        }
        val perAppProxyUpdateOnChange = Settings.perAppProxyUpdateOnChange
        if (perAppProxyUpdateOnChange == Settings.PER_APP_PROXY_DISABLED) {
            Log.d(TAG, "update on change disabled")
            return
        }
        val packageName = intent.dataString?.substringAfter("package:")
        if (packageName.isNullOrBlank()) {
            Log.d(TAG, "missing package name in intent")
            return
        }
        val isChinaApp = PerAppProxyActivity.scanChinaApps(listOf(packageName)).isNotEmpty()
        Log.d(TAG, "scan china app result for $packageName: $isChinaApp")
        if ((perAppProxyUpdateOnChange == Settings.PER_APP_PROXY_INCLUDE) xor !isChinaApp) {
            Settings.perAppProxyList = Settings.perAppProxyList + packageName
            Log.d(TAG, "added to list")
        } else {
            Settings.perAppProxyList = Settings.perAppProxyList - packageName
            Log.d(TAG, "removed from list")
        }
    }

}