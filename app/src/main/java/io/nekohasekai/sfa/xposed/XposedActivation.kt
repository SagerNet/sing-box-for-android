package io.nekohasekai.sfa.xposed

import android.content.Context
import android.os.Process

object XposedActivation {
    private const val PREFS_NAME = "xposed_activation"
    private const val KEY_ACTIVATED_PID = "activated_pid"
    private const val KEY_ACTIVATED_AT = "activated_at"
    private const val KEY_SYSTEM_IN_SCOPE = "system_in_scope"

    fun markActivated(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACTIVATED_PID, Process.myPid())
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun updateScope(context: Context, scope: Collection<String>) {
        val hasSystemScope = scope.any { it == "system" || it == "android" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SYSTEM_IN_SCOPE, hasSystemScope)
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun isActivated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_SYSTEM_IN_SCOPE)) {
            return prefs.getBoolean(KEY_SYSTEM_IN_SCOPE, false)
        }
        return prefs.getInt(KEY_ACTIVATED_PID, -1) == Process.myPid()
    }
}
