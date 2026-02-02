package io.nekohasekai.sfa.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.screen.profileoverride.PerAppProxyScanner
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppChangeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AppChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")
        if (!Settings.perAppProxyEnabled) {
            Log.d(TAG, "per app proxy disabled")
            return
        }
        if (!Settings.perAppProxyManagedMode) {
            Log.d(TAG, "managed mode disabled")
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescanAllApps()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rescan apps", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_title, Toast.LENGTH_SHORT).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun rescanAllApps() {
        Log.d(TAG, "rescanning all apps")
        val chinaApps = PerAppProxyScanner.scanAllChinaApps()
        Settings.perAppProxyManagedList = chinaApps
        Log.d(TAG, "rescan complete, found ${chinaApps.size} china apps")
    }
}
