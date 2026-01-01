package io.nekohasekai.sfa.vendor

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService

class RootPackageManagerService : RootService() {

    private val binder = object : IRootPackageManager.Stub() {
        override fun getInstalledPackages(flags: Int, offset: Int, limit: Int): List<PackageInfo> {
            val allPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(flags)
            }
            val endIndex = minOf(offset + limit, allPackages.size)
            if (offset >= allPackages.size) {
                return emptyList()
            }
            return allPackages.subList(offset, endIndex)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
