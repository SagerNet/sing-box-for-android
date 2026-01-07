package io.nekohasekai.sfa.utils

import android.content.Context
import io.nekohasekai.sfa.xposed.HookStatusKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object HookStatusClient {
    data class Status(
        val active: Boolean,
        val lastPatchedAt: Long,
        val version: Int,
        val systemPid: Int,
    )

    private val statusFlow = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = statusFlow

    @Volatile
    private var appContext: Context? = null

    fun register(context: Context) {
        appContext = context.applicationContext
        refresh()
    }

    fun refresh() {
        val context = appContext ?: return
        val binder = ConnectivityBinderUtils.getBinder(context) ?: run {
            statusFlow.value = null
            return
        }
        ConnectivityBinderUtils.withParcel { data, reply ->
            data.writeInterfaceToken(HookStatusKeys.DESCRIPTOR)
            val ok = binder.transact(HookStatusKeys.TRANSACTION_STATUS, data, reply, 0)
            if (!ok) {
                statusFlow.value = null
                return
            }
            reply.readException()
            statusFlow.value = Status(
                active = reply.readInt() != 0,
                lastPatchedAt = reply.readLong(),
                version = reply.readInt(),
                systemPid = reply.readInt(),
            )
        }
    }
}
