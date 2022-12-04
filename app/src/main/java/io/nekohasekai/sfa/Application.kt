package io.nekohasekai.sfa

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import go.Seq

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
        lateinit var application: Application
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
    }

}