package io.nekohasekai.sfa.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.util.Base64
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.distribute.Distribute
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
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import io.nekohasekai.sfa.utils.HTTPClient
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Date
import java.util.LinkedList
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec






class MainActivity : AbstractActivity(), ServiceConnection.Callback {

    companion object {
        private const val TAG = "MainActivity"
        private const val BASE_URL = "https://raw.githubusercontent.com/rtlvpn/junk/main/"

    }

    private lateinit var videoView: VideoView

    private lateinit var binding: ActivityMainBinding
    private val connection = ServiceConnection(this, this)

    val logList = LinkedList<String>()
    var logCallback: ((Boolean) -> Unit)? = null
    val serviceStatus = MutableLiveData(Status.Stopped)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCenter.start(application, "d0933572-cdde-4a65-9abe-6ff193cdff64", Analytics::class.java, Distribute::class.java)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.glass);
        window.navigationBarColor = ContextCompat.getColor(this, R.color.glass);
        videoView = findViewById<VideoView>(R.id.videoView)

        // Set the path of the video
        // Set the path of the video
        lifecycleScope.launch(Dispatchers.IO) {
            // Set the path of the video
            val videoPath = "https://raw.githubusercontent.com/rtlvpn/junk/main/cnvs.mp4"
            val uri = Uri.parse(videoPath)
            withContext(Dispatchers.Main) {
                videoView.setVideoURI(uri)
                // Set the OnPreparedListener
                videoView.setOnPreparedListener { mp -> mp.isLooping = true }
                // Start the VideoView
                videoView.start()
            }
        }
        val navController = findNavController(R.id.nav_host_fragment_activity_my)
        navController.navigate(R.id.navigation_dashboard)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_log -> {
                    // Stop the video when navigating to the ShopFragment
                    videoView.pause()
                }
                R.id.navigation_dashboard -> {
                    // Start the video when navigating back to the dashboard
                    videoView.start()
                }
                R.id.navigation_configuration -> {
                    // Start the video when navigating back to the dashboard
                    videoView.start()
                }
                R.id.navigation_settings -> {
                    // Start the video when navigating back to the dashboard
                    videoView.start()
                }
                // Add more cases as needed
                else -> {
                    // Optional: Handle any other cases
                }
            }
        }
        val appBarConfiguration =
            AppBarConfiguration(
                setOf(
                    R.id.navigation_dashboard,
                    R.id.navigation_log,
                    R.id.navigation_configuration,
                    R.id.navigation_settings,
                )
            )
//        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)
        binding.navView.itemIconTintList = null;

        reconnect()
        startIntegration()

        onNewIntent(intent)
    }

    @SuppressLint("StringFormatInvalid")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return
        if (uri.scheme == "rattlevpn") {
            var secretText = uri.path ?: return
            secretText = secretText.substring(1)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_key)
                .setMessage(getString(R.string.import_key_messages, secretText))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val md5Hash = md5(secretText)
                            val fileURL = "$BASE_URL$md5Hash"
                            val url = URL(fileURL)
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "HEAD"

                            val fileExists: Boolean = connection.responseCode == HttpURLConnection.HTTP_OK

                            if (fileExists) {
                                val encryptedContent = HTTPClient().use { it.getString(fileURL) }
                                val keySpec = SecretKeySpec(secretText.toByteArray(), "AES")
                                val json = JSONObject(encryptedContent)
                                val iv = Base64.decode(json.getString("iv"), Base64.DEFAULT)
                                val ct = Base64.decode(json.getString("ciphertext"), Base64.DEFAULT)
                                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                                val ivSpec = IvParameterSpec(iv)
                                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                                val decryptedContent = String(cipher.doFinal(ct))
                                // Extract timestamp from the end of decrypted content
                                val timestampRegex = "(?<=//)\\d{13}(?=$)".toRegex()
                                val timestampStr = timestampRegex.find(decryptedContent)?.value

                                if (timestampStr != null) {
                                    val timestamp = timestampStr.toLong()
                                    if (timestamp < System.currentTimeMillis()) {
                                        // If timestamp is in the past, show error and stop operation
                                        withContext(Dispatchers.Main) {
                                            MaterialAlertDialogBuilder(this@MainActivity)
                                                .setTitle(R.string.key_expired_title)
                                                .setMessage(R.string.key_expired_message)
                                                .setPositiveButton(android.R.string.ok, null)
                                                .show()
                                        }
                                    } else {
                                        // Continue with your existing code...
                                        val typedProfile = TypedProfile()
                                        typedProfile.type = TypedProfile.Type.Remote // Set the type to remote
                                        val profile = Profile(name = secretText, typed = typedProfile)
                                        profile.userOrder = ProfileManager.nextOrder()
                                        val fileID = ProfileManager.nextFileID()
                                        val configDirectory = File(filesDir, "configs").also { it.mkdirs() }
                                        val configFile = File(configDirectory, "$fileID.json")
                                        typedProfile.path = configFile.path
                                        configFile.writeText(decryptedContent)
                                        typedProfile.remoteURL = fileURL
                                        typedProfile.lastUpdated = Date()
                                        typedProfile.autoUpdate = true
                                        typedProfile.autoUpdateInterval = 15
                                        ProfileManager.create(profile)
                                        // spinnerAdapter.reload() // You might need to handle this differently
                                    }
                                } else {
                                    // Handle the case where the timestamp is not found
                                    withContext(Dispatchers.Main) {
                                        MaterialAlertDialogBuilder(this@MainActivity)
                                            .setTitle(R.string.key_damaged_title)
                                            .setMessage(R.string.key_damaged_message)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show()
                                    }
                                }
                            } else {
                                // Handle the case where the file does not exist
                                withContext(Dispatchers.Main) {
                                    MaterialAlertDialogBuilder(this@MainActivity)
                                        .setTitle(R.string.file_not_found_title)
                                        .setMessage(R.string.file_not_found_message)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                errorDialogBuilder(e).show()
                            }
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.fold("", { str, it -> str + "%02x".format(it) })
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
    fun startService(skipRequestFineLocation: Boolean = false) {
        if (!ServiceNotification.checkPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        // MIUI always return false for shouldShowRequestPermissionRationale
        if (!skipRequestFineLocation && ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                fineLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
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

    private val fineLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startService(true)
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
        builder.setPositiveButton(android.R.string.ok, null)
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
        videoView.stopPlayback()

        paused = true
    }

    override fun onResume() {
        super.onResume()
        videoView = findViewById<VideoView>(R.id.videoView)
        lifecycleScope.launch(Dispatchers.IO) {
            // Set the path of the video
            val videoPath = "https://raw.githubusercontent.com/rtlvpn/junk/main/cnvs.mp4"
            val uri = Uri.parse(videoPath)
            withContext(Dispatchers.Main) {
                videoView.setVideoURI(uri)
                // Set the OnPreparedListener
                videoView.setOnPreparedListener { mp -> mp.isLooping = true }
                // Start the VideoView
                videoView.start()
            }
        }

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