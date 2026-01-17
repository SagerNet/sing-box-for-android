package io.nekohasekai.sfa.compose.component.qr

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.qrs.QRSConstants
import io.nekohasekai.sfa.qrs.QRSEncoder
import kotlinx.coroutines.delay

@Composable
fun QRSDialog(profileData: ByteArray, profileName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE)
    val coroutineScope = rememberCoroutineScope()
    var fps by remember { mutableIntStateOf(QRSConstants.DEFAULT_FPS) }
    var sliceSize by remember { mutableIntStateOf(QRSConstants.DEFAULT_SLICE_SIZE) }

    val encoder = remember(sliceSize) { QRSEncoder(sliceSize) }
    val dataWithMeta = remember(profileData, profileName) {
        QRSEncoder.appendFileHeaderMeta(
            data = profileData,
            filename = "$profileName.bpf",
            contentType = "application/octet-stream",
        )
    }
    val requiredFrames = remember(dataWithMeta, sliceSize) {
        QRSConstants.calculateRequiredFrames(dataWithMeta.size, sliceSize)
    }
    val frames = remember(dataWithMeta, sliceSize, requiredFrames) {
        encoder.encode(dataWithMeta, QRSConstants.OFFICIAL_URL_PREFIX)
            .take(requiredFrames)
            .toList()
    }

    val frameInterval = remember(fps) { 1000L / fps }

    val generator = remember(frames) {
        QRSBitmapGenerator(
            scope = coroutineScope,
            frames = frames,
            foregroundColor = Color.BLACK,
            backgroundColor = Color.WHITE,
            bufferSize = QRSConstants.BITMAP_BUFFER_SIZE,
        )
    }

    val generationState by generator.state.collectAsState()

    LaunchedEffect(generator) {
        generator.start()
    }

    DisposableEffect(generator) {
        onDispose {
            generator.cancel()
        }
    }

    LaunchedEffect(frameInterval, generationState.generatedCount) {
        if (generationState.generatedCount > 0) {
            while (true) {
                delay(frameInterval)
                generator.advanceFrame()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
            if (isTablet) {
                Modifier
                    .fillMaxWidth(0.85f)
                    .sizeIn(maxWidth = 960.dp)
                    .wrapContentHeight()
            } else {
                Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
            },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            val qrSurface: @Composable () -> Unit = {
                Surface(
                    modifier = Modifier
                        .sizeIn(maxWidth = if (isTablet) 420.dp else 360.dp, maxHeight = if (isTablet) 420.dp else 360.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(0.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        generationState.currentBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.content_description_qr_code),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }

            val controlsContent: @Composable () -> Unit = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.qrs_fps),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "$fps Hz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Slider(
                        value = fps.toFloat(),
                        onValueChange = { fps = it.toInt() },
                        valueRange = QRSConstants.MIN_FPS.toFloat()..QRSConstants.MAX_FPS.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.qrs_slice_size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "$sliceSize",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Slider(
                        value = sliceSize.toFloat(),
                        onValueChange = { sliceSize = it.toInt() },
                        valueRange = QRSConstants.MIN_SLICE_SIZE.toFloat()..QRSConstants.MAX_SLICE_SIZE.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qifi-dev/qrs"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.qrs_what_is_qrs))
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.close))
                    }
                }
            }

            if (isTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        qrSurface()
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        controlsContent()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    qrSurface()

                    Spacer(modifier = Modifier.height(16.dp))

                    controlsContent()
                }
            }
        }
    }
}
