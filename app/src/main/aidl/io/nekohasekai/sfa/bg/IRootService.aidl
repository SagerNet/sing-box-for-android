package io.nekohasekai.sfa.bg;

import android.os.ParcelFileDescriptor;
import io.nekohasekai.sfa.bg.ParceledListSlice;

interface IRootService {
    void destroy() = 16777114; // Destroy method defined by Shizuku server

    ParceledListSlice getInstalledPackages(int flags, int userId) = 1;

    void installPackage(in ParcelFileDescriptor apk, long size, int userId) = 2;

    String exportDebugInfo(String outputPath) = 3;
}
