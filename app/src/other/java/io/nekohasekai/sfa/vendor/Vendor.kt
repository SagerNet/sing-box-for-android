package io.nekohasekai.sfa.vendor

import android.app.Activity
import androidx.camera.core.ImageAnalysis

object Vendor : VendorInterface {

    override fun checkUpdateAvailable(): Boolean {
        return false
    }

    override fun checkUpdate(activity: Activity, byUser: Boolean) {
    }

    override fun createQRCodeAnalyzer(
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ): ImageAnalysis.Analyzer? {
        return null
    }
}