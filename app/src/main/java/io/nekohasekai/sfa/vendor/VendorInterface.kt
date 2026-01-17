package io.nekohasekai.sfa.vendor

import android.app.Activity
import androidx.camera.core.ImageAnalysis
import io.nekohasekai.sfa.compose.screen.qrscan.QRCodeCropArea
import io.nekohasekai.sfa.update.UpdateInfo

interface VendorInterface {
    fun checkUpdate(activity: Activity, byUser: Boolean)

    fun createQRCodeAnalyzer(
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        onCropArea: ((QRCodeCropArea?) -> Unit)? = null,
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

    /**
     * Check if silent install feature is available
     * @return true if silent install is supported (Other flavor only)
     */
    fun supportsSilentInstall(): Boolean = false

    /**
     * Check if auto update feature is available
     * @return true if auto update is supported (Other flavor only)
     */
    fun supportsAutoUpdate(): Boolean = false

    /**
     * Schedule auto update worker
     */
    fun scheduleAutoUpdate() {}

    /**
     * Verify if the specified silent install method is available
     * @param method The install method (SHIZUKU or ROOT)
     * @return true if the method is available and working
     */
    suspend fun verifySilentInstallMethod(method: String): Boolean = false

    /**
     * Download and install an APK update
     * @param context The context
     * @param downloadUrl The URL to download the APK from
     * @throws Exception if download or install fails
     */
    suspend fun downloadAndInstall(context: android.content.Context, downloadUrl: String): Unit = throw UnsupportedOperationException("Not supported in this flavor")
}
