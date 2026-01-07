package io.nekohasekai.sfa.xposed

import android.util.Log
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.LogEntry
import java.util.ArrayDeque

object HookErrorStore {
    private const val MAX_ENTRIES = 100

    private val lock = Any()
    private val entries = ArrayDeque<LogEntry>()

    fun i(source: String, message: String, throwable: Throwable? = null) {
        log(LogEntry.LEVEL_INFO, source, message, throwable, store = true)
    }

    fun w(source: String, message: String, throwable: Throwable? = null) {
        log(LogEntry.LEVEL_WARN, source, message, throwable, store = true)
    }

    fun e(source: String, message: String, throwable: Throwable? = null) {
        log(LogEntry.LEVEL_ERROR, source, message, throwable, store = true)
    }

    fun d(source: String, message: String, throwable: Throwable? = null) {
        log(LogEntry.LEVEL_DEBUG, source, message, throwable, store = false)
    }

    private fun log(
        level: Int,
        source: String,
        message: String,
        throwable: Throwable?,
        store: Boolean,
    ) {
        if (BuildConfig.DEBUG) {
            when (level) {
                LogEntry.LEVEL_DEBUG -> {
                    if (throwable != null) {
                        Log.d(XposedInit.TAG, "[$source] $message", throwable)
                    } else {
                        Log.d(XposedInit.TAG, "[$source] $message")
                    }
                }
                LogEntry.LEVEL_INFO -> {
                    if (throwable != null) {
                        Log.i(XposedInit.TAG, "[$source] $message", throwable)
                    } else {
                        Log.i(XposedInit.TAG, "[$source] $message")
                    }
                }
                LogEntry.LEVEL_WARN -> {
                    if (throwable != null) {
                        Log.w(XposedInit.TAG, "[$source] $message", throwable)
                    } else {
                        Log.w(XposedInit.TAG, "[$source] $message")
                    }
                }
                LogEntry.LEVEL_ERROR -> {
                    if (throwable != null) {
                        Log.e(XposedInit.TAG, "[$source] $message", throwable)
                    } else {
                        Log.e(XposedInit.TAG, "[$source] $message")
                    }
                }
            }
        }
        if (!store || level == LogEntry.LEVEL_DEBUG) return
        val stackTrace = throwable?.let { Log.getStackTraceString(it) }
        val entry = LogEntry(level, System.currentTimeMillis(), source, message, stackTrace)
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
    }

    fun snapshot(): List<LogEntry> {
        synchronized(lock) {
            return entries.toList()
        }
    }

    fun hasWarnings(): Boolean {
        synchronized(lock) {
            return entries.any { it.level >= LogEntry.LEVEL_WARN }
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }
}
