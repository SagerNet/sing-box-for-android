package io.nekohasekai.sfa.bg

import android.app.Service
import android.content.Intent
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunInterface
import io.nekohasekai.libbox.TunOptions

class ProxyService : Service(), PlatformInterface {

    private val service = BoxService(this, this)

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int) =
        service.onStartCommand(intent, flags, startId)

    override fun onBind(intent: Intent) = service.onBind(intent)
    override fun onDestroy() = service.onDestroy()

    override fun autoDetectInterfaceControl(fd: Int) {
    }

    override fun openTun(options: TunOptions?): TunInterface {
        error("bad state: create tun in normal service")
    }

    override fun writeLog(message: String?) = service.writeLog(message)

}