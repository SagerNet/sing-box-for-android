package io.nekohasekai.sfa.vendor

import android.content.Intent
import android.content.IntentSender
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerSession
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.Os
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.content.IIntentSender
import io.nekohasekai.sfa.BuildConfig
import java.io.IOException

object PrivilegedServiceUtils {

    private fun getPackageManager(): Any {
        val binder = SystemServiceHelperCompat.getSystemService("package") ?: throw IllegalStateException("package service not available")
        val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, binder) ?: throw IllegalStateException("IPackageManager is null")
    }

    fun getInstalledPackages(flags: Int, userId: Int): List<PackageInfo> {
        val iPackageManager = getPackageManager()
        val iPackageManagerClass = Class.forName("android.content.pm.IPackageManager")
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val method = iPackageManagerClass.getMethod(
                "getInstalledPackages",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            method.invoke(iPackageManager, flags.toLong(), userId)
        } else {
            val method = iPackageManagerClass.getMethod(
                "getInstalledPackages",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            method.invoke(iPackageManager, flags, userId)
        }
        return extractPackageList(result)
    }

    fun installPackage(apkFd: ParcelFileDescriptor, size: Long, userId: Int) {
        val iPackageInstaller = getPackageInstaller()
        val isRoot = Os.getuid() == 0
        val installerPackageName = if (isRoot) BuildConfig.APPLICATION_ID else "com.android.shell"
        val targetUserId = if (isRoot) userId else 0

        val packageInstaller = createPackageInstaller(
            iPackageInstaller,
            installerPackageName,
            null,
            targetUserId,
        )

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(BuildConfig.APPLICATION_ID)
        // Set INSTALL_REPLACE_EXISTING flag (value = 2)
        val installFlagsField = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
        installFlagsField.isAccessible = true
        installFlagsField.setInt(params, installFlagsField.getInt(params) or 2)
        val sessionId = packageInstaller.createSession(params)

        val iSession = IPackageInstallerSession.Stub.asInterface(
            iPackageInstaller.openSession(sessionId).asBinder(),
        )
        val session = createSession(iSession)

        try {
            ParcelFileDescriptor.AutoCloseInputStream(apkFd).use { inputStream ->
                session.openWrite("base.apk", 0, size).use { outputStream ->
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

            val intent = resultIntent[0] ?: throw IOException("Installation timed out")

            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            if (status != PackageInstaller.STATUS_SUCCESS) {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                throw IOException("Installation failed ($status): $message")
            }
        } finally {
            session.close()
        }
    }

    private fun getPackageInstaller(): IPackageInstaller {
        val iPackageManager = getPackageManager()
        val iPackageManagerClass = Class.forName("android.content.pm.IPackageManager")
        val method = iPackageManagerClass.getMethod("getPackageInstaller")
        val installer = method.invoke(iPackageManager) as IPackageInstaller
        return IPackageInstaller.Stub.asInterface(installer.asBinder())
    }

    private fun createPackageInstaller(
        installer: IPackageInstaller,
        installerPackageName: String,
        installerAttributionTag: String?,
        userId: Int,
    ): PackageInstaller {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PackageInstaller::class.java.getConstructor(
                    IPackageInstaller::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                ).newInstance(installer, installerPackageName, installerAttributionTag, userId)
        } else {
            PackageInstaller::class.java.getConstructor(
                    IPackageInstaller::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                ).newInstance(installer, installerPackageName, userId)
        }
    }

    private fun createSession(session: IPackageInstallerSession): PackageInstaller.Session {
        return PackageInstaller.Session::class.java.getConstructor(IPackageInstallerSession::class.java).newInstance(session)
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
                options: Bundle?,
            ) {
                onResult(intent)
            }
        }
        return IntentSender::class.java.getConstructor(IIntentSender::class.java).newInstance(sender)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractPackageList(parceledListSlice: Any?): List<PackageInfo> {
        if (parceledListSlice == null) return emptyList()
        val getListMethod = parceledListSlice.javaClass.getMethod("getList")
        val list = getListMethod.invoke(parceledListSlice) as? List<*>
        return list?.filterIsInstance<PackageInfo>() ?: emptyList()
    }
}
