package io.nekohasekai.sfa.vendor

import android.content.pm.PackageInfo
import android.os.ParcelFileDescriptor
import io.nekohasekai.sfa.bg.IShizukuService
import io.nekohasekai.sfa.bg.ParceledListSlice
import java.io.IOException

class ShizukuPrivilegedService : IShizukuService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    override fun getInstalledPackages(flags: Int, userId: Int): ParceledListSlice<PackageInfo> {
        val allPackages = PrivilegedServiceUtils.getInstalledPackages(flags, userId)
        return ParceledListSlice(allPackages)
    }

    override fun installPackage(apk: ParcelFileDescriptor?, size: Long, userId: Int) {
        if (apk == null) throw IOException("APK file descriptor is null")
        PrivilegedServiceUtils.installPackage(apk, size, userId)
    }
}
