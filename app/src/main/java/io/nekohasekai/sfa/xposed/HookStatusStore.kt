package io.nekohasekai.sfa.xposed

import android.os.Process

object HookStatusStore {
    @Volatile
    private var active = false
    @Volatile
    private var lastPatchedAt = 0L

    fun markHookActive() {
        active = true
    }

    fun markPatched() {
        lastPatchedAt = System.currentTimeMillis()
    }

    fun snapshot(): Status {
        return Status(active, lastPatchedAt, HookModuleVersion.CURRENT, Process.myPid())
    }

    data class Status(
        val active: Boolean,
        val lastPatchedAt: Long,
        val version: Int,
        val systemPid: Int,
    )
}
