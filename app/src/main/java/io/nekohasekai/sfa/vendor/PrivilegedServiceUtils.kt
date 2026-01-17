package io.nekohasekai.sfa.vendor

import android.content.IIntentSender
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
import io.nekohasekai.sfa.BuildConfig
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object PrivilegedServiceUtils {

    private val iPackageManagerStubClass by lazy { Class.forName("android.content.pm.IPackageManager\$Stub") }
    private val asInterfaceMethod by lazy { iPackageManagerStubClass.getMethod("asInterface", IBinder::class.java) }
    private val iPackageManagerClass by lazy { Class.forName("android.content.pm.IPackageManager") }

    private val getInstalledPackagesMethodLong by lazy {
        iPackageManagerClass.getMethod(
            "getInstalledPackages",
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
    }
    private val getInstalledPackagesMethodInt by lazy {
        iPackageManagerClass.getMethod(
            "getInstalledPackages",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
    }
    private val getPackageInstallerMethod by lazy { iPackageManagerClass.getMethod("getPackageInstaller") }

    private val packageInstallerCtorS by lazy {
        PackageInstaller::class.java.getConstructor(
            IPackageInstaller::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
        )
    }
    private val packageInstallerCtorPre by lazy {
        PackageInstaller::class.java.getConstructor(
            IPackageInstaller::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
        )
    }
    private val sessionCtor by lazy {
        PackageInstaller.Session::class.java.getConstructor(IPackageInstallerSession::class.java)
    }
    private val intentSenderCtor by lazy {
        IntentSender::class.java.getConstructor(IIntentSender::class.java)
    }
    private val installFlagsField by lazy {
        PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags").apply { isAccessible = true }
    }
    private val getListMethod by lazy {
        Class.forName("android.content.pm.ParceledListSlice").getMethod("getList")
    }

    private fun getPackageManager(): Any {
        val binder = SystemServiceHelperCompat.getSystemService("package")
            ?: throw IllegalStateException("package service not available")
        return asInterfaceMethod.invoke(null, binder)
            ?: throw IllegalStateException("IPackageManager is null")
    }

    fun getInstalledPackages(flags: Int, userId: Int): List<PackageInfo> {
        val iPackageManager = getPackageManager()
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getInstalledPackagesMethodLong.invoke(iPackageManager, flags.toLong(), userId)
        } else {
            getInstalledPackagesMethodInt.invoke(iPackageManager, flags, userId)
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
        val installer = getPackageInstallerMethod.invoke(iPackageManager) as IPackageInstaller
        return IPackageInstaller.Stub.asInterface(installer.asBinder())
    }

    private fun createPackageInstaller(
        installer: IPackageInstaller,
        installerPackageName: String,
        installerAttributionTag: String?,
        userId: Int,
    ): PackageInstaller = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        packageInstallerCtorS.newInstance(installer, installerPackageName, installerAttributionTag, userId)
    } else {
        packageInstallerCtorPre.newInstance(installer, installerPackageName, userId)
    }

    private fun createSession(session: IPackageInstallerSession): PackageInstaller.Session = sessionCtor.newInstance(session)

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
        return intentSenderCtor.newInstance(sender)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractPackageList(parceledListSlice: Any?): List<PackageInfo> {
        if (parceledListSlice == null) return emptyList()
        val list = getListMethod.invoke(parceledListSlice) as? List<*>
        return list?.filterIsInstance<PackageInfo>() ?: emptyList()
    }
}
