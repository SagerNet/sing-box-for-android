package io.nekohasekai.sfa.compose.screen.usbip

import android.content.Context
import android.hardware.usb.UsbManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.usbip.ProvidedDevice
import io.nekohasekai.sfa.usbip.ProvidedDeviceState
import io.nekohasekai.sfa.usbip.USBIPManager
import io.nekohasekai.sfa.usbip.formatVidPid

private enum class Tone { GOOD, MEDIUM, BAD, NEUTRAL }

private data class UsbDeviceRow(
    val key: String,
    val name: String,
    val vidPid: String?,
    val busId: String?,
    val error: String?,
    val backendState: Int?,
    val providedState: ProvidedDeviceState?,
    val deviceData: UsbSharedDeviceData?,
    val providedDeviceId: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun USBIPServerScreen(
    navController: NavController,
    viewModel: USBIPStatusViewModel,
    serverTag: String,
) {
    val state by viewModel.uiState.collectAsState()
    val providerState by USBIPManager.state.collectAsState()
    val server = state.servers.firstOrNull { it.serverTag == serverTag }
    val provided = providerState.devices.filter { it.serverTag == serverTag }
    val endError = providerState.endErrors[serverTag]
    val attach = rememberUsbAttacher(serverTag)

    OverrideTopBar {
        TopAppBar(
            title = {
                Text(
                    if (state.servers.size <= 1) {
                        stringResource(R.string.title_usbip)
                    } else {
                        stringResource(R.string.usbip_with_tag, serverTag)
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                }
            },
            actions = {
                if (server != null) {
                    AddDeviceMenu(provided = provided, onPick = attach)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        if (server == null) {
            Text(
                text = stringResource(R.string.usbip_no_server),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            )
            return@Column
        }

        if (endError != null) {
            Text(
                text = endError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
        }

        val rows = mergeRows(server, provided)
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.usbip_no_devices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column {
                    for (row in rows) {
                        DeviceItem(
                            row = row,
                            onOpen = {
                                if (row.deviceData != null) {
                                    navController.navigate("tools/usbip/${Uri.encode(serverTag)}/device/${Uri.encode(row.key)}")
                                }
                            },
                            onDetach = { id -> USBIPManager.detach(id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDeviceMenu(provided: List<ProvidedDevice>, onPick: (android.hardware.usb.UsbDevice) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.usbip_connect_device))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val devices = usbManager.deviceList.values.toList()
            val attachedNames = provided.mapNotNull { it.usbDeviceName }.toSet()
            if (devices.isEmpty()) {
                DropdownMenuItem(
                    enabled = false,
                    text = { Text(stringResource(R.string.usbip_no_usb_devices)) },
                    onClick = {},
                )
            }
            for (device in devices) {
                val attached = device.deviceName in attachedNames
                DropdownMenuItem(
                    enabled = !attached,
                    leadingIcon = {
                        if (attached) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    },
                    text = {
                        Column {
                            Text(device.productName ?: formatVidPid(device.vendorId, device.productId))
                            Text(
                                formatVidPid(device.vendorId, device.productId),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onPick(device)
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceItem(row: UsbDeviceRow, onOpen: () -> Unit, onDetach: (String) -> Unit) {
    val (label, tone) = stateInfo(row)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(enabled = row.deviceData != null, onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(toneColor(tone)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${row.name}:",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                if (row.error != null) {
                    Text(row.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (row.providedDeviceId != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(52.dp)
                    .clickable { onDetach(row.providedDeviceId) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.UsbOff,
                    contentDescription = stringResource(R.string.usbip_detach),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun stateInfo(row: UsbDeviceRow): Pair<String, Tone> {
    row.backendState?.let { backend ->
        return when (backend) {
            Libbox.USBDeviceStateIdle -> stringResource(R.string.usbip_state_idle) to Tone.GOOD
            Libbox.USBDeviceStateAttached -> stringResource(R.string.usbip_state_attached) to Tone.MEDIUM
            Libbox.USBDeviceStateUnavailable -> stringResource(R.string.usbip_state_unavailable) to Tone.BAD
            else -> "" to Tone.NEUTRAL
        }
    }
    return when (row.providedState) {
        ProvidedDeviceState.ATTACHING -> stringResource(R.string.usbip_state_attaching) to Tone.MEDIUM
        ProvidedDeviceState.READY -> stringResource(R.string.usbip_state_ready) to Tone.GOOD
        ProvidedDeviceState.ERROR -> stringResource(R.string.usbip_state_error) to Tone.BAD
        null -> "" to Tone.NEUTRAL
    }
}

@Composable
private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.GOOD -> Color(0xFF4CAF50)
    Tone.MEDIUM -> MaterialTheme.colorScheme.primary
    Tone.BAD -> MaterialTheme.colorScheme.error
    Tone.NEUTRAL -> Color.Gray
}

private fun mergeRows(server: UsbipServerData, provided: List<ProvidedDevice>): List<UsbDeviceRow> {
    val providedByBusId =
        provided.filter { it.state == ProvidedDeviceState.READY && it.busId != null }.associateBy { it.busId!! }
    val matched = HashSet<String>()
    val rows = mutableListOf<UsbDeviceRow>()

    for (device in server.devices) {
        val providedDevice = providedByBusId[device.busId]
        if (providedDevice != null) matched.add(providedDevice.deviceId)
        rows.add(
            UsbDeviceRow(
                key = device.key,
                name = device.product.ifEmpty { formatVidPid(device.vendorId, device.productId) },
                vidPid = formatVidPid(device.vendorId, device.productId),
                busId = device.busId,
                error = null,
                backendState = device.state,
                providedState = null,
                deviceData = device,
                providedDeviceId = providedDevice?.deviceId,
            ),
        )
    }

    for (device in provided) {
        if (matched.contains(device.deviceId)) continue
        rows.add(
            UsbDeviceRow(
                key = "provided-${device.deviceId}",
                name = device.label,
                vidPid = formatVidPid(device.vendorId, device.productId),
                busId = if (device.state == ProvidedDeviceState.READY) device.busId else null,
                error = if (device.state == ProvidedDeviceState.ERROR) device.error else null,
                backendState = null,
                providedState = device.state,
                deviceData = null,
                providedDeviceId = device.deviceId,
            ),
        )
    }

    return rows
}
