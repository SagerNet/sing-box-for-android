package io.nekohasekai.sfa.vendor;

import android.content.pm.PackageInfo;

interface IRootPackageManager {
    List<PackageInfo> getInstalledPackages(int flags, int offset, int limit);
}
