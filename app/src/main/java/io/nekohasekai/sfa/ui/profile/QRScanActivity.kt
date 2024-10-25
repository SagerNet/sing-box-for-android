package io.nekohasekai.sfa.ui.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.databinding.ActivityQrScanBinding
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ui.shared.AbstractActivity
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScanActivity : AbstractActivity<ActivityQrScanBinding>() {

    private lateinit var analysisExecutor: ExecutorService
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.profile_add_scan_qr_code)

        analysisExecutor = Executors.newSingleThreadExecutor()
        binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        binding.previewView.previewStreamState.observe(this) {
            if (it === PreviewView.StreamState.STREAMING) {
                binding.progress.isVisible = false
                binding.previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var imageAnalyzer: ImageAnalysis.Analyzer
    private val onSuccess: (String) -> Unit = { rawValue: String ->
        imageAnalysis.clearAnalyzer()
        if (!onSuccess(rawValue)) {
            imageAnalysis.setAnalyzer(analysisExecutor, imageAnalyzer)
        }
    }
    private val onFailure: (Exception) -> Unit = {
        lifecycleScope.launch {
            resetAnalyzer()
            errorDialogBuilder("MLKit error: ${it.localizedMessage}").show()
        }
    }
    private val vendorAnalyzer = Vendor.createQRCodeAnalyzer(onSuccess, onFailure)
    private var useVendorAnalyzer = vendorAnalyzer != null
    private fun resetAnalyzer() {
        if (useVendorAnalyzer) {
            useVendorAnalyzer = false
            imageAnalysis.clearAnalyzer()
            imageAnalyzer = ZxingQRCodeAnalyzer(onSuccess, onFailure)
            imageAnalysis.setAnalyzer(analysisExecutor, imageAnalyzer)
        }
    }

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraPreview: Preview
    private lateinit var camera: Camera

    private fun startCamera() {
        val cameraProviderFuture = try {
            ProcessCameraProvider.getInstance(this)
        } catch (e: Exception) {
            fatalError(e)
            return
        }
        cameraProviderFuture.addListener({
            cameraProvider = try {
                cameraProviderFuture.get()
            } catch (e: Exception) {
                fatalError(e)
                return@addListener
            }

            cameraPreview = Preview.Builder().build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
            imageAnalysis = ImageAnalysis.Builder().build()
            imageAnalyzer = vendorAnalyzer ?: ZxingQRCodeAnalyzer(onSuccess, onFailure)
            imageAnalysis.setAnalyzer(analysisExecutor, imageAnalyzer)
            cameraProvider.unbindAll()

            try {
                camera = cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, imageAnalysis
                )
            } catch (e: Exception) {
                fatalError(e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun fatalError(e: Exception) {
        lifecycleScope.launch {
            errorDialogBuilder(e).setOnDismissListener {
                setResult(RESULT_CANCELED)
                finish()
            }.show()
        }
    }

    private fun onSuccess(value: String): Boolean {
        try {
            importRemoteProfileFromString(value)
            return true
        } catch (e: Exception) {
            lifecycleScope.launch {
                errorDialogBuilder(e).show()
            }
        }
        return false
    }

    private fun importRemoteProfileFromString(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "sing-box" || uri.host != "import-remote-profile") error("Not a valid sing-box remote profile URI")
        Libbox.parseRemoteProfileImportLink(uri.toString())
        setResult(RESULT_OK, Intent().apply {
            setData(uri)
        })
        finish()
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (!useVendorAnalyzer) {
            menu!!.findItem(R.id.action_use_vendor_analyzer).also {
                it.isEnabled = false
                it.isChecked = false
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.qr_scan_menu, menu)
        if (useVendorAnalyzer) {
            menu.findItem(R.id.action_use_vendor_analyzer).isChecked = true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_use_front_camera -> {
                item.isChecked = !item.isChecked
                cameraProvider.unbindAll()
                try {
                    camera = cameraProvider.bindToLifecycle(
                        this,
                        if (!item.isChecked) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                        cameraPreview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    fatalError(e)
                }
            }

            R.id.action_enable_torch -> {
                item.isChecked = !item.isChecked
                camera.cameraControl.enableTorch(item.isChecked)
            }

            R.id.action_use_vendor_analyzer -> {
                item.isChecked = !item.isChecked
                imageAnalysis.clearAnalyzer()
                imageAnalyzer = if (item.isChecked) {
                    vendorAnalyzer!!
                } else {
                    ZxingQRCodeAnalyzer(onSuccess, onFailure)
                }
                imageAnalysis.setAnalyzer(analysisExecutor, imageAnalyzer)
            }

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }


    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }

    class Contract : ActivityResultContract<Nothing?, Intent?>() {

        override fun createIntent(context: Context, input: Nothing?): Intent =
            Intent(context, QRScanActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?): Intent? {
            return when (resultCode) {
                RESULT_OK -> intent
                else -> null
            }
        }
    }

}