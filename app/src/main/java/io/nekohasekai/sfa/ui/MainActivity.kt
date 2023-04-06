package io.nekohasekai.sfa.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes
import com.microsoft.appcenter.distribute.Distribute
import com.microsoft.appcenter.distribute.DistributeListener
import com.microsoft.appcenter.distribute.ReleaseDetails
import com.microsoft.appcenter.distribute.UpdateAction
import com.microsoft.appcenter.utils.AppNameHelper
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.ServiceConnection
import io.nekohasekai.sfa.bg.ServiceNotification
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.ActivityMainBinding
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList

class MainActivity : AbstractActivity(), ServiceConnection.Callback, DistributeListener {

    companion object {
        private const val TAG = "MyActivity"
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
        startAnalysis()
    }

    fun reconnect() {
        connection.reconnect()
    }

    private fun startAnalysis() {
        lifecycleScope.launch(Dispatchers.IO) {
            when (Settings.analyticsAllowed) {
                Settings.ANALYSIS_UNKNOWN -> {
                    withContext(Dispatchers.Main) {
                        showAnalysisDialog()
                    }
                }

                Settings.ANALYSIS_ALLOWED -> {
                    startAnalysisInternal()
                }
            }
        }
    }

    private fun showAnalysisDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.analytics_title))
            .setMessage(getString(R.string.analytics_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    Settings.analyticsAllowed = Settings.ANALYSIS_ALLOWED
                    startAnalysisInternal()
                }
            }
            .setNegativeButton(getString(R.string.no_thanks)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    Settings.analyticsAllowed = Settings.ANALYSIS_DISALLOWED
                }
            }
            .show()
    }

    suspend fun startAnalysisInternal() {
        if (BuildConfig.APPCENTER_SECRET.isBlank()) {
            return
        }
        Distribute.setListener(this)
        runCatching {
            AppCenter.start(
                application,
                BuildConfig.APPCENTER_SECRET,
                Analytics::class.java,
                Crashes::class.java,
                Distribute::class.java,
            )
            if (!Settings.checkUpdateEnabled) {
                Distribute.disableAutomaticCheckForUpdate()
            }
        }.onFailure {
            withContext(Dispatchers.Main) {
                errorDialogBuilder(it).show()
            }
        }
    }

    override fun onReleaseAvailable(activity: Activity, releaseDetails: ReleaseDetails): Boolean {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(getString(com.microsoft.appcenter.distribute.R.string.appcenter_distribute_update_dialog_title))
        var message = if (releaseDetails.isMandatoryUpdate) {
            getString(com.microsoft.appcenter.distribute.R.string.appcenter_distribute_update_dialog_message_mandatory)
        } else {
            getString(com.microsoft.appcenter.distribute.R.string.appcenter_distribute_update_dialog_message_optional)
        }
        message = String.format(
            message,
            AppNameHelper.getAppName(this),
            releaseDetails.shortVersion,
            releaseDetails.version
        )
        builder.setMessage(message)
        builder.setPositiveButton(com.microsoft.appcenter.distribute.R.string.appcenter_distribute_update_dialog_download) { _, _ ->
            Distribute.notifyUpdateAction(UpdateAction.UPDATE)
            startActivity(Intent(Intent.ACTION_VIEW, releaseDetails.downloadUrl))
        }
        builder.setCancelable(false)
        if (!releaseDetails.isMandatoryUpdate) {
            builder.setNegativeButton(com.microsoft.appcenter.distribute.R.string.appcenter_distribute_update_dialog_postpone) { _, _ ->
                Distribute.notifyUpdateAction(UpdateAction.POSTPONE)
            }
        }
        if (!TextUtils.isEmpty(releaseDetails.releaseNotes) && releaseDetails.releaseNotesUrl != null) {
            builder.setNeutralButton(com.microsoft.appcenter.distribute.R.string.appcenter_distribute_update_dialog_view_release_notes) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, releaseDetails.releaseNotesUrl))
            }
        }
        builder.show()
        return true
    }

    override fun onNoReleaseAvailable(activity: Activity) {
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
        val builder = MaterialAlertDialogBuilder(this)
        builder.setPositiveButton(resources.getString(android.R.string.ok), null)
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
        }
        builder.show()
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