package io.nekohasekai.sfa.vendor

import android.annotation.SuppressLint
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
object SystemServiceHelperCompat {

    private val serviceCache = HashMap<String, IBinder?>()
    private val getService: Method? = try {
        val cls = Class.forName("android.os.ServiceManager")
        cls.getMethod("getService", String::class.java)
    } catch (e: Exception) {
        Log.w("SystemServiceHelper", Log.getStackTraceString(e))
        null
    }

    fun getSystemService(name: String): IBinder? {
        if (serviceCache.containsKey(name)) {
            return serviceCache[name]
        }
        val binder = try {
            getService?.invoke(null, name) as? IBinder
        } catch (e: Exception) {
            Log.w("SystemServiceHelper", Log.getStackTraceString(e))
            null
        }
        serviceCache[name] = binder
        return binder
    }
}
