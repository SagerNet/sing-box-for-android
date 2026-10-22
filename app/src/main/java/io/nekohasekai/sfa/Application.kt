package io.nekohasekai.sfa

import android.app.Application
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.sfa.bg.AppChangeReceiver
import io.nekohasekai.sfa.bg.CrashReportManager
import io.nekohasekai.sfa.bg.OOMReportManager
import io.nekohasekai.sfa.bg.PowerReportManager
import io.nekohasekai.sfa.bg.UpdateProfileWork
import io.nekohasekai.sfa.compose.screen.tools.TaildropFiles
import io.nekohasekai.sfa.constant.Bugs
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.HookModuleUpdateNotifier
import io.nekohasekai.sfa.utils.HookStatusClient
import io.nekohasekai.sfa.utils.PrivilegeSettingsClient
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.Locale
import io.nekohasekai.sfa.Application as BoxApplication

class Application : Application() {
    private val setupAccess = Mutex()
    private var workingDir: File? = null
    private var setupCompleted = false

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    override fun onCreate() {
        super.onCreate()
        AppLifecycleObserver.register(this)

//        Seq.setContext(this)
        runCatching {
            Libbox.setLocale(Locale.getDefault().toLanguageTag())
        }.onFailure {
            Log.d("Application", "set locale: ${it.message}")
        }

        filesDir.mkdirs()
        cacheDir.mkdirs()
        installWorkingDir()

        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
            Settings.dataStore.initialize()
            HookStatusClient.register(this@Application)
            PrivilegeSettingsClient.register(this@Application)
            runCatching {
                setupLibbox()
            }.onFailure {
                Log.w("Application", "setup libbox: ${it.message}")
            }
            UpdateProfileWork.reconfigureUpdater()
            HookModuleUpdateNotifier.sync(this@Application)
            TaildropFiles.cleanCache()
        }

        if (Vendor.isPerAppProxyAvailable()) {
            registerReceiver(
                AppChangeReceiver(),
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                },
            )
        }
    }

    private fun installWorkingDir(): File? {
        workingDir?.let { return it }
        val dir = getExternalFilesDir(null) ?: return null
        dir.mkdirs()
        CrashReportManager.install(dir, filesDir)
        OOMReportManager.install(dir)
        PowerReportManager.install(dir)
        workingDir = dir
        return dir
    }

    suspend fun setupLibbox() = setupAccess.withLock {
        if (setupCompleted) return@withLock
        val dir = installWorkingDir() ?: error("android: external storage unavailable")
        Libbox.setup(createSetupOptions(filesDir, dir, cacheDir))
        setupCompleted = true
    }

    fun reloadSetupOptions() {
        val dir = getExternalFilesDir(null) ?: return
        Libbox.reloadSetupOptions(createSetupOptions(filesDir, dir, cacheDir))
    }

    private fun createSetupOptions(baseDir: File, workingDir: File, tempDir: File): SetupOptions = SetupOptions().also {
        it.basePath = baseDir.path
        it.workingPath = workingDir.path
        it.tempPath = tempDir.path
        it.fixAndroidStack = Bugs.fixAndroidStack
        it.logMaxLines = 3000
        it.debug = BuildConfig.DEBUG
        it.crashReportSource = "Application"
        it.appVersion = BuildConfig.VERSION_CODE.toString()
        it.appMarketingVersion = BuildConfig.VERSION_NAME
        it.oomKillerEnabled = Settings.oomKillerEnabled
        it.oomKillerDisabled = Settings.oomKillerDisabled
        it.oomMemoryLimit = Settings.oomMemoryLimitMB.toLong() * 1024L * 1024L
        it.powerReportEnabled = Settings.powerReportEnabled
        it.platformMetadata = JSONObject().apply {
            put("os", "Android " + Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
        }.toString()
    }

    companion object {
        lateinit var application: BoxApplication
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val packageManager by lazy { application.packageManager }
        val powerManager by lazy { application.getSystemService<PowerManager>()!! }
        val notificationManager by lazy { application.getSystemService<NotificationManager>()!! }
        val wifiManager by lazy { application.getSystemService<WifiManager>()!! }
        val clipboard by lazy { application.getSystemService<ClipboardManager>()!! }
    }
}
