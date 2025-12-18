package io.nekohasekai.sfa.vendor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import io.nekohasekai.sfa.database.Settings
import java.io.File
import java.io.FileInputStream

enum class InstallMethod {
    PACKAGE_INSTALLER,
    SHIZUKU,
    ROOT,
}

object ApkInstaller {

    fun getConfiguredMethod(): InstallMethod {
        return if (Settings.silentInstallEnabled) {
            InstallMethod.valueOf(Settings.silentInstallMethod)
        } else {
            InstallMethod.PACKAGE_INSTALLER
        }
    }

    suspend fun install(context: Context, apkFile: File, method: InstallMethod = getConfiguredMethod()): Result<Unit> {
        return when (method) {
            InstallMethod.SHIZUKU -> ShizukuInstaller.install(apkFile)
            InstallMethod.ROOT -> RootInstaller.install(apkFile)
            InstallMethod.PACKAGE_INSTALLER -> installWithPackageInstaller(context, apkFile)
        }
    }

    fun canSystemSilentInstall(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    suspend fun canSilentInstall(): Boolean {
        val method = getConfiguredMethod()
        return when (method) {
            InstallMethod.PACKAGE_INSTALLER -> canSystemSilentInstall()
            InstallMethod.SHIZUKU -> ShizukuInstaller.isAvailable() && ShizukuInstaller.checkPermission()
            InstallMethod.ROOT -> RootInstaller.checkAccess()
        }
    }

    private fun installWithPackageInstaller(context: Context, apkFile: File): Result<Unit> {
        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
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
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                session.commit(pendingIntent.intentSender)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
