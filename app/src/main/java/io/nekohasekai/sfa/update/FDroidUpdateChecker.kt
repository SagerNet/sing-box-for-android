package io.nekohasekai.sfa.update

import android.content.Context
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.Settings

fun checkFDroidUpdate(context: Context): UpdateInfo? {
    val packageName = context.packageName

    @Suppress("DEPRECATION")
    val versionCode = context.packageManager.getPackageInfo(packageName, 0).versionCode
    val result = Libbox.checkFDroidUpdate(
        Settings.fdroidMirrorUrl,
        packageName,
        versionCode,
        context.cacheDir.absolutePath,
    ) ?: return null
    return UpdateInfo(
        versionCode = result.versionCode,
        versionName = result.versionName,
        downloadUrl = result.downloadURL,
        releaseUrl = "https://f-droid.org/packages/$packageName/",
        releaseNotes = null,
        isPrerelease = false,
        fileSize = result.fileSize,
    )
}
