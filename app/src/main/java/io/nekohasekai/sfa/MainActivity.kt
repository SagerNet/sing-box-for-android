package io.nekohasekai.sfa

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blacksquircle.ui.language.json.JsonLanguage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sfa.aidl.IVPNService
import io.nekohasekai.sfa.aidl.IVPNServiceCallback
import io.nekohasekai.sfa.bg.VPNService
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.databinding.ActivityMainBinding
import io.nekohasekai.sfa.databinding.ViewLogTextItemBinding
import io.nekohasekai.sfa.db.Settings
import io.nekohasekai.sfa.utils.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.LinkedList

class MainActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var binding: ActivityMainBinding
    private var boxStatus = MutableLiveData(Status.Stopped)
    private val logManager = LogManager()
    private val callback = VPNCallback()

    private fun showText(content: String) {
        runOnUiThread {
            MaterialAlertDialogBuilder(this).setTitle("Error").setMessage(content)
                .setPositiveButton(resources.getString(android.R.string.ok)) { _, _ ->
                }.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val editor = binding.editor
        editor.language = JsonLanguage()

        lifecycleScope.launch(Dispatchers.IO) {
            val configurationContent = Settings.configurationContent
            withContext(Dispatchers.Main) {
                editor.setTextContent(configurationContent)
                editor.addTextChangedListener {
                    val newContent = it.toString()
                    lifecycleScope.launch(Dispatchers.IO) {
                        Settings.configurationContent = newContent
                    }
                }
            }
        }

        val button = binding.button
        val logView = binding.logView
        logView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        logView.adapter = logManager

        boxStatus.observe(this) {
            it!!

            button.isClickable = it == Status.Started || it == Status.Stopped
            editor.isVisible = it == Status.Stopped
            logView.isVisible = it != Status.Stopped

            when (it) {
                Status.Stopped -> {
                    button.text = "Start"
                    logManager.clear()
                }

                Status.Starting -> {
                    button.text = "Starting..."
                }

                Status.Started -> {
                    button.text = "Stop"
                }

                Status.Stopping -> {
                    button.text = "Stopping..."
                }
            }
        }

        button.setOnClickListener {
            when (boxStatus.value) {
                Status.Stopped -> {
                    startService()
                }

                Status.Started -> {
                    stopService()
                }

                else -> {}
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            connect()
        }
    }

    private fun startService() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (Settings.rebuildServiceMode()) {
                disconnect()
                connect()
            }
            if (Settings.serviceMode == ServiceMode.VPN) {
                try {
                    val intent = VpnService.prepare(this@MainActivity)
                    if (intent != null) {
                        prepare.launch(intent)
                        return@launch
                    }
                } catch (e: Exception) {
                    showText("Failed to request VPN permission: ${e.message}")
                }
            }
            val intent = Intent(Application.application, Settings.serviceClass())
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(Application.application, intent)
            }
        }

    }

    private suspend fun connect() {
        val intent = withContext(Dispatchers.IO) {
            Intent(this@MainActivity, Settings.serviceClass()).setAction(Action.SERVICE)
        }
        withContext(Dispatchers.Main) {
            bindService(intent, this@MainActivity, Context.BIND_AUTO_CREATE)
        }
    }

    private suspend fun disconnect() {
        withContext(Dispatchers.Main) {
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
    }

    override fun onBindingDied(name: ComponentName?) {
        runBlocking {
            disconnect()
            connect()
        }
    }

    private fun stopService() {
        application.sendBroadcast(Intent(Action.SERVICE_CLOSE).setPackage(Application.application.packageName))
    }

    private val prepare = registerForActivityResult(PrepareService()) {
        if (it) {
            startService()
        } else {
            showText("failed to request VPN permission")
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

    private var service: IVPNService? = null
    private var callbackRegistered = false

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        val service = IVPNService.Stub.asInterface(binder)
        this.service = service
        try {
            service.registerCallback(callback)
            callbackRegistered = true
        } catch (e: RemoteException) {
            Log.e("sing-box", "initialize service connection", e)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        try {
            service?.unregisterCallback(callback)
        } catch (e: RemoteException) {
            Log.e("sing-box", "cleanup service connection", e)
        }
    }

    inner class VPNCallback : IVPNServiceCallback.Stub() {
        override fun onStatusChanged(status: Int) {
            this@MainActivity.boxStatus.postValue(Status.values()[status])
        }

        override fun alert(type: Int, message: String?) {
            alertDialog(Alert.values()[type], message ?: "")
        }

        override fun writeLog(message: String) {
            binding.logView.scrollToPosition(logManager.insert(message))
        }
    }

    fun alertDialog(type: Alert, content: String) {
        val builder =
            MaterialAlertDialogBuilder(this).setPositiveButton(resources.getString(android.R.string.ok)) { _, _ ->
            }

        when (type) {
            Alert.EmptyConfiguration -> {
                builder.setMessage("Empty configuration")
            }

            Alert.CreateService -> {
                if (content.isNotBlank()) {
                    builder.setTitle("Create service")
                    builder.setMessage(content)
                } else {
                    builder.setMessage("create service")
                }
            }

            Alert.StartService -> {
                if (content.isNotBlank()) {
                    builder.setTitle("Start service")
                    builder.setMessage(content)
                } else {
                    builder.setMessage("start service")
                }
            }
        }

        builder.show()
    }

    class LogManager : RecyclerView.Adapter<LogViewHolder>() {

        private val logList = LinkedList<String>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            return LogViewHolder(
                ViewLogTextItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(logList.getOrElse(position) { "" })
        }

        override fun getItemCount(): Int {
            return logList.size
        }

        @SuppressLint("NotifyDataSetChanged")
        fun clear() {
            logList.clear()
            notifyDataSetChanged()
        }

        fun insert(message: String): Int {
            if (logList.size > 30) {
                logList.removeFirst()
                notifyItemRemoved(0)
            }

            logList.addLast(message)
            val position = logList.size - 1
            notifyItemInserted(position)
            return position
        }

    }

    class LogViewHolder(private val binding: ViewLogTextItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: String) {
            binding.text.text = ColorUtils.ansiEscapeToSpannable(binding.root.context, message)
        }
    }

}