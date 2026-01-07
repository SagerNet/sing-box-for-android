package io.nekohasekai.sfa.xposed

object HookStatusKeys {
    const val DESCRIPTOR = "android.net.IConnectivityManager"
    const val TRANSACTION_STATUS = 0x5F00
    const val TRANSACTION_UPDATE_PRIVILEGE_SETTINGS = 0x5F01
    const val TRANSACTION_GET_ERRORS = 0x5F02
    const val TRANSACTION_EXPORT_DEBUG_INFO = 0x5F03
}
