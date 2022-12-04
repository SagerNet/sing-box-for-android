package io.nekohasekai.sfa.bg

import android.app.Service
import android.content.Intent
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunInterface
import io.nekohasekai.libbox.TunOptions

class ProxyService : Service(), PlatformInterface {

    private val commonService = BoxService(this, this)

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