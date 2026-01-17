package io.nekohasekai.sfa.vendor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.FileInputStream
import android.content.pm.PackageInstaller as AndroidPackageInstaller

object SystemPackageInstaller {

    fun canSystemSilentInstall(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun install(context: Context, apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = AndroidPackageInstaller.SessionParams(AndroidPackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(AndroidPackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = packageInstaller.createSession(params)
        packageInstaller.openSession(sessionId).use { session ->
            session.openWrite("update.apk", 0, apkFile.length()).use { outputStream ->
                FileInputStream(apkFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                session.fsync(outputStream)
            }

            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = InstallResultReceiver.ACTION_INSTALL_COMPLETE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

            session.commit(pendingIntent.intentSender)
        }
    }
}
