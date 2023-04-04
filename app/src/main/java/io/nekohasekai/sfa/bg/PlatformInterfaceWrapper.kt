package io.nekohasekai.sfa.bg

import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.sfa.Application
import java.net.InetSocketAddress

interface PlatformInterfaceWrapper : PlatformInterface {

    override fun autoDetectInterfaceControl(fd: Int) {
    }

    override fun openTun(options: TunOptions): Int {
        error("invalid argument")
    }

    override fun useProcFS(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): Int {
        val uid = Application.connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort)
        )
        if (uid == Process.INVALID_UID) error("android: connection owner not found")
        return uid
    }

    override fun packageNameByUid(uid: Int): String {
        val packages = Application.packageManager.getPackagesForUid(uid)
        if (packages.isNullOrEmpty()) error("android: package not found")
        return packages[0]
    }

    @Suppress("DEPRECATION")
    override fun uidByPackageName(packageName: String): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Application.packageManager.getPackageUid(
                    packageName, PackageManager.PackageInfoFlags.of(0)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Application.packageManager.getPackageUid(packageName, 0)
            } else {
                Application.packageManager.getApplicationInfo(packageName, 0).uid
            }
        } catch (e: PackageManager.NameNotFoundException) {
            error("android: package not found")
        }
    }


}