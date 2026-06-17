package io.nekohasekai.sfa.compose.screen.usbip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.usbip.bcdToVersion
import io.nekohasekai.sfa.usbip.formatVidPid
import io.nekohasekai.sfa.usbip.usbBackendLabel
import io.nekohasekai.sfa.usbip.usbClassTriplet
import io.nekohasekai.sfa.usbip.usbSpeedLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun USBIPDeviceDetailScreen(
    navController: NavController,
    viewModel: USBIPStatusViewModel,
    serverTag: String,
    deviceKey: String,
) {
    val state by viewModel.uiState.collectAsState()
    val device = state.servers.firstOrNull { it.serverTag == serverTag }?.devices?.firstOrNull { it.key == deviceKey }

    OverrideTopBar {
        TopAppBar(
            title = {
                Text(device?.product?.ifEmpty { device.busId } ?: stringResource(R.string.title_usbip))
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                }
            },
        )
    }

    if (device == null) {
        LaunchedEffect(Unit) { navController.navigateUp() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        DetailSection(stringResource(R.string.usbip_identity)) {
            if (device.product.isNotEmpty()) DataLine(stringResource(R.string.usbip_product), device.product)
            DataLine("VID:PID", formatVidPid(device.vendorId, device.productId), mono = true)
            if (device.serial.isNotEmpty()) DataLine(stringResource(R.string.usbip_serial), device.serial, mono = true)
            if (device.bcdDevice > 0) DataLine(stringResource(R.string.usbip_version), bcdToVersion(device.bcdDevice), mono = true)
        }

        DetailSection(stringResource(R.string.usbip_connection)) {
            if (device.busId.isNotEmpty()) DataLine(stringResource(R.string.usbip_bus_id), device.busId, mono = true)
            usbBackendLabel(device.backend)?.let { DataLine(stringResource(R.string.usbip_backend), it, mono = true) }
            usbSpeedLabel(device.speed)?.let { DataLine(stringResource(R.string.usbip_speed), it) }
            if (device.busNum > 0 || device.devNum > 0) {
                DataLine(stringResource(R.string.usbip_bus_device), "${device.busNum} · ${device.devNum}", mono = true)
            }
        }

        DetailSection(stringResource(R.string.usbip_class_interfaces)) {
            DataLine(
                stringResource(R.string.usbip_device_class),
                usbClassTriplet(device.deviceClass, device.deviceSubClass, device.deviceProtocol),
            )
            DataLine(
                stringResource(R.string.usbip_configuration),
                "${device.configurationValue} / ${device.numConfigurations}",
            )
            device.interfaces.forEachIndexed { index, iface ->
                DataLine(
                    stringResource(R.string.usbip_interface, index),
                    usbClassTriplet(iface.interfaceClass, iface.interfaceSubClass, iface.interfaceProtocol),
                )
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DataLine(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}
