package io.nekohasekai.sfa

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import go.Seq
import io.nekohasekai.sfa.Application as BoxApplication

class Application : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        application = this
    }

    override fun onCreate() {
        super.onCreate()

        Seq.setContext(this)
    }


    companion object {
        lateinit var application: BoxApplication
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val packageManager by lazy { application.packageManager }
    }

}