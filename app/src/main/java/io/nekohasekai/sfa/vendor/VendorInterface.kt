package io.nekohasekai.sfa.vendor

import android.app.Activity
import androidx.camera.core.ImageAnalysis
import io.nekohasekai.sfa.update.UpdateInfo

interface VendorInterface {
    fun checkUpdate(
        activity: Activity,
        byUser: Boolean,
    )

    fun createQRCodeAnalyzer(
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
    ): ImageAnalysis.Analyzer?

    /**
     * Check if Per-app Proxy feature is available
     * @return true if available, false if disabled (e.g., for Play Store builds)
     */
    fun isPerAppProxyAvailable(): Boolean = true

    /**
     * Check if track selection is available (e.g., stable/beta)
     * @return true if track selection is supported
     */
    fun supportsTrackSelection(): Boolean = false

    /**
     * Check for updates asynchronously
     * @return UpdateInfo if update is available, null otherwise
     */
    fun checkUpdateAsync(): UpdateInfo? = null
}
