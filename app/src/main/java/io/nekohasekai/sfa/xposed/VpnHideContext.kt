package io.nekohasekai.sfa.xposed

object VpnHideContext {
    private val targetUid = ThreadLocal<Int?>()

    fun setTargetUid(uid: Int) {
        targetUid.set(uid)
    }

    fun consumeTargetUid(): Int? {
        val value = targetUid.get()
        targetUid.remove()
        return value
    }

    fun clear() {
        targetUid.remove()
    }
}
