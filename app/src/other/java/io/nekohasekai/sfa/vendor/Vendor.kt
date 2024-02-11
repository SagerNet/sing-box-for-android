package io.nekohasekai.sfa.vendor

import android.app.Activity

object Vendor : VendorInterface {

    override fun checkUpdateAvailable(): Boolean {
        return false
    }

    override fun checkUpdate(activity: Activity, byUser: Boolean) {
    }

    override fun initializeBillingClient(activity: Activity) {
    }

    override fun startSponsor(activity: Activity, fallback: () -> Unit) {
        fallback()
    }

}