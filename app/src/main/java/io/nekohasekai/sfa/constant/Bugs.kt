package io.nekohasekai.sfa.constant

import android.os.Build
import io.nekohasekai.sfa.BuildConfig

object Bugs {

    // TODO: remove launch after fixed
    // https://github.com/golang/go/issues/68760
    val fixAndroidStack = BuildConfig.DEBUG ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1 ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

}