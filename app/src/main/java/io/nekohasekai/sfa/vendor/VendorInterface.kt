package io.nekohasekai.sfa.vendor

import android.app.Activity

interface VendorInterface {
    fun checkUpdateAvailable(): Boolean
    fun checkUpdate(activity: Activity, byUser: Boolean)
    fun initializeBillingClient(activity: Activity)
    fun startSponsor(activity: Activity, fallback: () -> Unit)
}