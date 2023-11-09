package io.nekohasekai.sfa.vendor

import android.app.Activity

object Vendor : VendorInterface {

    override fun checkUpdateAvailable(): Boolean {
        return false
    }

    override fun checkUpdate(activity: Activity, byUser: Boolean) {
    }


}