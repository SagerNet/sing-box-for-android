package io.nekohasekai.sfa.utils

import android.content.Context
import android.net.ConnectivityManager
import android.os.IBinder
import android.os.Parcel
import android.util.Log

object ConnectivityBinderUtils {
    private const val TAG = "ConnectivityBinderUtils"

    fun getBinder(context: Context): IBinder? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        try {
            val field = cm.javaClass.getDeclaredField("mService")
            field.isAccessible = true
            val service = field.get(cm) as? android.os.IInterface
            if (service != null) {
                return service.asBinder()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to get ConnectivityManager service binder", e)
        }
        return try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            getService.invoke(null, Context.CONNECTIVITY_SERVICE) as? IBinder
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to get binder from ServiceManager", e)
            null
        }
    }

    inline fun <T> withParcel(block: (data: Parcel, reply: Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            block(data, reply)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
