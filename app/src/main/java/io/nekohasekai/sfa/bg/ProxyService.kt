package io.nekohasekai.sfa.bg

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.lifecycle.MutableLiveData
import go.Seq
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunInterface
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.sfa.constant.Action
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.db.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProxyService : Service(), PlatformInterface {

    private val commonService = CommonService(this, this)

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int) =
        commonService.onStartCommand(intent, flags, startId)
    override fun onBind(intent: Intent) = commonService.onBind(intent)
    override fun onDestroy() = commonService.onDestroy()

    override fun autoDetectInterfaceControl(fd: Int) {
    }

    override fun openTun(options: TunOptions?): TunInterface {
        error("bad state: create tun in normal service")
    }

    override fun writeLog(message: String?) = commonService.writeLog(message)

}