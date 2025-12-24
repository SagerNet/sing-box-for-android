package io.nekohasekai.sfa.vendor

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService

class RootPackageManagerService : RootService() {

    private val binder = object : IRootPackageManager.Stub() {
        override fun getInstalledPackages(flags: Int): List<PackageInfo> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(flags)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
