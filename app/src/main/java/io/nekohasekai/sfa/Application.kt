package io.nekohasekai.sfa

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import go.Seq
import io.nekohasekai.libbox.Libbox

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
    }

}