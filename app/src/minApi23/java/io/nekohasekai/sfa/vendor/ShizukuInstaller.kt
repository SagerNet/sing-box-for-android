package io.nekohasekai.sfa.vendor

import android.content.Intent
import android.content.IntentSender
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerSession
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import io.nekohasekai.sfa.vendor.hidden.IPackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.content.IIntentSender

object ShizukuInstaller {

    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun checkPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        if (!Shizuku.isPreV11()) {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    private fun isRunningAsRoot(): Boolean {
        return try {
            Shizuku.getUid() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun getPackageInstaller(): IPackageInstaller {
        val packageManagerBinder = SystemServiceHelper.getSystemService("package")
        val packageManager = IPackageManager.Stub.asInterface(ShizukuBinderWrapper(packageManagerBinder))
        val installerBinder = packageManager.packageInstaller.asBinder()
        return IPackageInstaller.Stub.asInterface(ShizukuBinderWrapper(installerBinder))
    }

    private fun createPackageInstaller(
        installer: IPackageInstaller,
        installerPackageName: String,
        installerAttributionTag: String?,
        userId: Int
    ): PackageInstaller {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return PackageInstaller::class.java
                .getConstructor(
                    IPackageInstaller::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                .newInstance(installer, installerPackageName, installerAttributionTag, userId)
        } else {
            return PackageInstaller::class.java
                .getConstructor(
                    IPackageInstaller::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                .newInstance(installer, installerPackageName, userId)
        }
    }

    private fun createSession(session: IPackageInstallerSession): PackageInstaller.Session {
        return PackageInstaller.Session::class.java
            .getConstructor(IPackageInstallerSession::class.java)
            .newInstance(session)
    }

    private fun createIntentSender(onResult: (Intent) -> Unit): IntentSender {
        val sender = object : IIntentSender.Stub() {
            override fun send(
                code: Int,
                intent: Intent,
                resolvedType: String?,
                whitelistToken: android.os.IBinder?,
                finishedReceiver: android.content.IIntentReceiver?,
                requiredPermission: String?,
                options: android.os.Bundle?
            ) {
                onResult(intent)
            }
        }
        return IntentSender::class.java
            .getConstructor(IIntentSender::class.java)
            .newInstance(sender)
    }

    suspend fun install(apkFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("")
            }

            val iPackageInstaller = getPackageInstaller()
            val isRoot = isRunningAsRoot()

            val installerPackageName = if (isRoot) "io.nekohasekai.sfa" else "com.android.shell"
            val installerAttributionTag: String? = null
            val userId = if (isRoot) Process.myUserHandle().hashCode() else 0

            val packageInstaller = createPackageInstaller(
                iPackageInstaller,
                installerPackageName,
                installerAttributionTag,
                userId
            )

            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)

            val iSession = IPackageInstallerSession.Stub.asInterface(
                ShizukuBinderWrapper(iPackageInstaller.openSession(sessionId).asBinder())
            )
            val session = createSession(iSession)

            try {
                FileInputStream(apkFile).use { inputStream ->
                    session.openWrite("base.apk", 0, apkFile.length()).use { outputStream ->
                        inputStream.copyTo(outputStream)
                        session.fsync(outputStream)
                    }
                }

                val resultIntent = arrayOfNulls<Intent>(1)
                val latch = CountDownLatch(1)

                val intentSender = createIntentSender { intent ->
                    resultIntent[0] = intent
                    latch.countDown()
                }

                session.commit(intentSender)
                latch.await(60, TimeUnit.SECONDS)

                val intent = resultIntent[0]
                    ?: return@withContext Result.failure(Exception("Installation timed out"))

                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

                if (status == PackageInstaller.STATUS_SUCCESS) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Installation failed: $status - $message"))
                }
            } finally {
                session.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
