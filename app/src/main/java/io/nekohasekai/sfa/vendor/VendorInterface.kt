package io.nekohasekai.sfa.vendor

import android.app.Activity
import androidx.camera.core.ImageAnalysis

interface VendorInterface {
    fun checkUpdateAvailable(): Boolean
    fun checkUpdate(activity: Activity, byUser: Boolean)
    fun createQRCodeAnalyzer(
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ): ImageAnalysis.Analyzer?
}