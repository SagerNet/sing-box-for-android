package io.nekohasekai.sfa.compose.component.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.screen.qrscan.QRScanResult
import io.nekohasekai.sfa.compose.screen.qrscan.QRScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScanSheet(
    onDismiss: () -> Unit,
    onScanResult: (QRScanResult) -> Unit,
    viewModel: QRScanViewModel = viewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasPermission = true
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetQRSState()
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.result) {
        uiState.result?.let { result ->
            viewModel.clearResult()
            onScanResult(result)
        }
    }

    var showMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.profile_add_scan_qr_code),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                )

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (uiState.useFrontCamera) "✓ " else "   ") +
                                        stringResource(R.string.profile_add_scan_use_front_camera)
                                )
                            },
                            onClick = {
                                viewModel.toggleFrontCamera(lifecycleOwner)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (uiState.torchEnabled) "✓ " else "   ") +
                                        stringResource(R.string.profile_add_scan_enable_torch)
                                )
                            },
                            onClick = {
                                viewModel.toggleTorch()
                                showMenu = false
                            }
                        )
                        if (uiState.vendorAnalyzerAvailable) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (uiState.useVendorAnalyzer) "✓ " else "   ") +
                                            stringResource(R.string.profile_add_scan_use_vendor_analyzer)
                                    )
                                },
                                onClick = {
                                    viewModel.toggleVendorAnalyzer()
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (hasPermission) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel,
                        lifecycleOwner = lifecycleOwner
                    )
                }

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                if (uiState.qrsMode && uiState.qrsProgress != null) {
                    val (decoded, total) = uiState.qrsProgress!!
                    val progress = if (total > 0) decoded.toFloat() / total.toFloat() / 1.2f else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.size(96.dp),
                                color = Color.White,
                                strokeWidth = 8.dp,
                                trackColor = Color.White.copy(alpha = 0.3f),
                            )
                            if (total > 0) {
                                Text(
                                    text = "${minOf(99, (progress * 100).toInt())}%",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "QRS",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                modifier = Modifier.offset(y = (-88).dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text(stringResource(android.R.string.dialog_alert_title)) },
            text = { Text(uiState.errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    viewModel: QRScanViewModel,
    lifecycleOwner: LifecycleOwner,
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(previewView) {
        previewView?.let { view ->
            view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            viewModel.startCamera(lifecycleOwner, view)
        }
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                previewView = this

                previewStreamState.observe(lifecycleOwner) { state ->
                    if (state == PreviewView.StreamState.STREAMING) {
                        viewModel.onPreviewStreamStateChanged(true)
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    }
                }
            }
        }
    )
}
