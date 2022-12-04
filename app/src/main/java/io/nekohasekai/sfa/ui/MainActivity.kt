package io.nekohasekai.sfa.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.aidl.IService
import io.nekohasekai.sfa.aidl.IServiceCallback
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.LinkedList

class MainActivity : AppCompatActivity(), ServiceConnection {

    companion object {
        const val TAG = "MyActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val callback = ServiceCallback()

    val logList = LinkedList<String>()
    var logCallback: ((Boolean) -> Unit)? = null
    val serviceStatus = MutableLiveData(Status.Stopped)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val color = SurfaceColors.SURFACE_2.getColor(this)
        window.statusBarColor = color
        window.navigationBarColor = color


        val navController = findNavController(R.id.nav_host_fragment_activity_my)
        val appBarConfiguration =
            AppBarConfiguration(setOf(R.id.navigation_dashboard, R.id.navigation_configuration))
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        connect()
    }

    private val prepareIntent = registerForActivityResult(PrepareService()) {
        if (it) {
            startService()
        } else {
            serviceAlert(Alert.CreateService)
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

    fun startService() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (Settings.rebuildServiceMode()) {
                disconnect()
                connect()
            }
            if (Settings.serviceMode == ServiceMode.VPN) {
                if (withContext(Dispatchers.Main) {
                        try {
                            val intent = VpnService.prepare(this@MainActivity)
                            if (intent != null) {
                                prepareIntent.launch(intent)
                                true
                            } else {
                                false
                            }
                        } catch (e: Exception) {
                            serviceAlert(Alert.RequestVPNPermission, e.message)
                            false
                        }
                    }) {
                    return@launch
                }
            }
            val intent = Intent(Application.application, Settings.serviceClass())
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(Application.application, intent)
            }
        }

    }

    fun stopService() {
        application.sendBroadcast(Intent(Action.SERVICE_CLOSE).setPackage(Application.application.packageName))
    }


    private var service: IService? = null
    private var callbackRegistered = false

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        val service = IService.Stub.asInterface(binder)
        this.service = service
        try {
            service.registerCallback(callback)
            callbackRegistered = true
        } catch (e: RemoteException) {
            Log.e(TAG, "initialize service connection", e)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        try {
            service?.unregisterCallback(callback)
        } catch (e: RemoteException) {
            Log.e(TAG, "cleanup service connection", e)
        }
    }

    override fun onBindingDied(name: ComponentName) {
        disconnect()
        connect()
    }

    private fun connect() {
        val intent = runBlocking {
            withContext(Dispatchers.IO) {
                Intent(this@MainActivity, Settings.serviceClass()).setAction(Action.SERVICE)
            }
        }
        bindService(intent, this, BIND_AUTO_CREATE)
    }

    private fun disconnect() {
        sendBroadcast(
            Intent(Action.SERVICE_CLOSE).setPackage(
                Application.application.packageName
            )
        )
        try {
            unbindService(this@MainActivity)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun serviceOnStatusChanged(status: Int) {
        serviceStatus.postValue(Status.values()[status])
    }

    fun serviceAlert(type: Alert, message: String? = null) {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setPositiveButton(resources.getString(android.R.string.ok), null)
        when (type) {
            Alert.RequestVPNPermission -> {
                builder.setMessage("Failed to request VPN permission")
            }

            Alert.EmptyConfiguration -> {
                builder.setMessage("Empty configuration")
            }

            Alert.CreateService -> {
                if (message.isNullOrBlank()) {
                    builder.setMessage("create service")
                } else {
                    builder.setTitle("Create service")
                    builder.setMessage(message)
                }
            }

            Alert.StartService -> {
                if (message.isNullOrBlank()) {
                    builder.setMessage("start service")
                } else {
                    builder.setTitle("Start service")
                    builder.setMessage(message)
                }
            }
        }
        builder.show()
    }

    private fun serviceWriteLog(message: String?) {
        logList.addLast(message)
        logCallback?.invoke(false)
    }

    private fun serviceResetLog(messages: MutableList<String>) {
        logList.clear()
        logList.addAll(messages)
        logCallback?.invoke(true)
    }

    inner class ServiceCallback : IServiceCallback.Stub() {
        override fun onStatusChanged(status: Int) = serviceOnStatusChanged(status)

        override fun alert(type: Int, message: String?) {
            serviceAlert(Alert.values()[type], message)
        }

        override fun writeLog(message: String) = serviceWriteLog(message)

        override fun resetLogs(messages: MutableList<String>) = serviceResetLog(messages)
    }


}