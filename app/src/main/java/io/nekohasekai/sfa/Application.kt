package io.nekohasekai.sfa

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import go.Seq
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.constant.Action

class Application : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        application = this
    }

    override fun onCreate() {
        super.onCreate()

        Seq.setContext(this)

        val baseDir = getExternalFilesDir(null) ?: filesDir
        baseDir.mkdirs()
        Libbox.setBasePath(baseDir.path)
    }


    companion object {
        lateinit var application: Application
        val notification by lazy { application.getSystemService<NotificationManager>()!! }

        fun startService() {
            ContextCompat.startForegroundService(
                application,
                Intent(application, VPNService::class.java)
            )
        }

        fun stopService() {
            application.sendBroadcast(Intent(Action.SERVICE_CLOSE).setPackage(application.packageName))
        }
    }

}