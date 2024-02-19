package io.nekohasekai.sfa.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.Html
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.ProfileContent
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.ServiceConnection
import io.nekohasekai.sfa.bg.ServiceNotification
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.databinding.ActivityMainBinding
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.hasPermission
import io.nekohasekai.sfa.ui.profile.NewProfileActivity
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.LinkedList

class MainActivity : AbstractActivity(), ServiceConnection.Callback {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val connection = ServiceConnection(this, this)

    val logList = LinkedList<String>()
    var logCallback: ((Boolean) -> Unit)? = null
    val serviceStatus = MutableLiveData(Status.Stopped)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_activity_my)
        navController.navigate(R.id.navigation_dashboard)
        val appBarConfiguration =
            AppBarConfiguration(
                setOf(
                    R.id.navigation_dashboard,
                    R.id.navigation_log,
                    R.id.navigation_configuration,
                    R.id.navigation_settings,
                )
            )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        reconnect()
        startIntegration()

        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return
        if (uri.scheme == "sing-box" && uri.host == "import-remote-profile") {
            val profile = try {
                Libbox.parseRemoteProfileImportLink(uri.toString())
            } catch (e: Exception) {
                errorDialogBuilder(e).show()
                return
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_remote_profile)
                .setMessage(
                    getString(
                        R.string.import_remote_profile_message,
                        profile.name,
                        profile.host
                    )
                )
                .setPositiveButton(R.string.ok) { _, _ ->
                    startActivity(Intent(this, NewProfileActivity::class.java).apply {
                        putExtra("importName", profile.name)
                        putExtra("importURL", profile.url)
                    })
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else if (intent.action == Intent.ACTION_VIEW) {
            try {
                val data = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
                val content = Libbox.decodeProfileContent(data)
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.import_profile)
                    .setMessage(
                        getString(
                            R.string.import_profile_message,
                            content.name
                        )
                    )
                    .setPositiveButton(R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    importProfile(content)
                                }.onFailure {
                                    withContext(Dispatchers.Main) {
                                        errorDialogBuilder(it).show()
                                    }
                                }
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } catch (e: Exception) {
                errorDialogBuilder(e).show()
            }
        }
    }

    private suspend fun importProfile(content: ProfileContent) {
        val typedProfile = TypedProfile()
        val profile = Profile(name = content.name, typed = typedProfile)
        profile.userOrder = ProfileManager.nextOrder()
        when (content.type) {
            Libbox.ProfileTypeLocal -> {
                typedProfile.type = TypedProfile.Type.Local
            }

            Libbox.ProfileTypeiCloud -> {
                errorDialogBuilder(R.string.icloud_profile_unsupported).show()
                return
            }

            Libbox.ProfileTypeRemote -> {
                typedProfile.type = TypedProfile.Type.Remote
                typedProfile.remoteURL = content.remotePath
                typedProfile.autoUpdate = content.autoUpdate
                typedProfile.autoUpdateInterval = content.autoUpdateInterval
                typedProfile.lastUpdated = Date(content.lastUpdated)
            }
        }
        val configDirectory = File(filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "${profile.userOrder}.json")
        configFile.writeText(content.config)
        typedProfile.path = configFile.path
        ProfileManager.create(profile)
    }

    fun reconnect() {
        connection.reconnect()
    }

    private fun startIntegration() {
        if (Vendor.checkUpdateAvailable()) {
            lifecycleScope.launch(Dispatchers.IO) {
                if (Settings.checkUpdateEnabled) {
                    Vendor.checkUpdate(this@MainActivity, false)
                }
            }
        }
    }

    @SuppressLint("NewApi")
    fun startService() {
        if (!ServiceNotification.checkPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            if (Settings.rebuildServiceMode()) {
                reconnect()
            }
            if (Settings.serviceMode == ServiceMode.VPN) {
                if (prepare()) {
                    return@launch
                }
            }
            val intent = Intent(Application.application, Settings.serviceClass())
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(Application.application, intent)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (it) {
            startService()
        } else {
            onServiceAlert(Alert.RequestNotificationPermission, null)
        }
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestBackgroundLocationPermission()
                } else {
                    startService()
                }
            } else {
                showPermissionDeniedDescription()
            }
        }

    private val backgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                startService()
            } else {
                showPermissionDeniedDescription()
            }
        }

    private val prepareLauncher = registerForActivityResult(PrepareService()) {
        if (it) {
            startService()
        } else {
            onServiceAlert(Alert.RequestVPNPermission, null)
        }
    }

    private class PrepareService : ActivityResultContract<Intent, Boolean>() {
        override fun createIntent(context: Context, input: Intent): Intent {
            return input
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
            return resultCode == RESULT_OK
        }
    }

    private suspend fun prepare() = withContext(Dispatchers.Main) {
        try {
            val intent = VpnService.prepare(this@MainActivity)
            if (intent != null) {
                prepareLauncher.launch(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            onServiceAlert(Alert.RequestVPNPermission, e.message)
            false
        }
    }

    override fun onServiceStatusChanged(status: Status) {
        serviceStatus.postValue(status)
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        when (type) {
            Alert.RequestLocationPermission -> {
                return requestLocationPermission()
            }

            else -> {}
        }

        val builder = MaterialAlertDialogBuilder(this)
        builder.setPositiveButton(R.string.ok, null)
        when (type) {
            Alert.RequestVPNPermission -> {
                builder.setMessage(getString(R.string.service_error_missing_permission))
            }

            Alert.RequestNotificationPermission -> {
                builder.setMessage(getString(R.string.service_error_missing_notification_permission))
            }

            Alert.EmptyConfiguration -> {
                builder.setMessage(getString(R.string.service_error_empty_configuration))
            }

            Alert.StartCommandServer -> {
                builder.setTitle(getString(R.string.service_error_title_start_command_server))
                builder.setMessage(message)
            }

            Alert.CreateService -> {
                builder.setTitle(getString(R.string.service_error_title_create_service))
                builder.setMessage(message)
            }

            Alert.StartService -> {
                builder.setTitle(getString(R.string.service_error_title_start_service))
                builder.setMessage(message)

            }

            else -> {}
        }
        builder.show()
    }

    private fun requestLocationPermission() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestFineLocationPermission()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationPermission()
        }
    }

    private fun requestFineLocationPermission() {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(
                getString(R.string.location_permission_description),
                Html.FROM_HTML_MODE_LEGACY
            )
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(getString(R.string.location_permission_description))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_permission_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ ->
                requestFineLocationPermission0()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestFineLocationPermission0() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            openPermissionSettings()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestBackgroundLocationPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_permission_title)
            .setMessage(
                Html.fromHtml(
                    getString(R.string.location_permission_background_description),
                    Html.FROM_HTML_MODE_LEGACY
                )
            )
            .setPositiveButton(R.string.ok) { _, _ ->
                backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setCancelable(false)
            .show()
    }

    private fun openPermissionSettings() {
        if (!getSystemProperty("ro.miui.ui.version.name").isNullOrBlank()) {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            intent.putExtra("extra_package_uid", Process.myUid())
            intent.putExtra("extra_pkgname", packageName)
            try {
                startActivity(intent)
                return
            } catch (ignored: Exception) {
            }
        }

        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            errorDialogBuilder(e).show()
        }
    }

    private fun showPermissionDeniedDescription() {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(
                getString(R.string.location_permission_denied_description),
                Html.FROM_HTML_MODE_LEGACY
            )
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(getString(R.string.location_permission_denied_description))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_permission_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.open_settings) { _, _ ->
                openPermissionSettings()
            }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("PrivateApi")
    fun getSystemProperty(key: String?): String? {
        try {
            return Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (ignored: Exception) {
        }
        return null
    }

    private var paused = false
    override fun onPause() {
        super.onPause()

        paused = true
    }

    override fun onResume() {
        super.onResume()

        paused = false
        logCallback?.invoke(true)
    }

    override fun onServiceWriteLog(message: String?) {
        if (paused) {
            if (logList.size > 300) {
                logList.removeFirst()
            }
        }
        logList.addLast(message)
        if (!paused) {
            logCallback?.invoke(false)
        }
    }

    override fun onServiceResetLogs(messages: MutableList<String>) {
        logList.clear()
        logList.addAll(messages)
        if (!paused) logCallback?.invoke(true)
    }

    override fun onDestroy() {
        connection.disconnect()
        super.onDestroy()
    }

}