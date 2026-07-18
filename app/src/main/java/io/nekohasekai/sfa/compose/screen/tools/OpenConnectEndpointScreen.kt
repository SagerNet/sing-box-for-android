package io.nekohasekai.sfa.compose.screen.tools

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.component.qr.QRCodeDialog
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.util.QRCodeGenerator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OpenConnectEndpointScreen(
    navController: NavController,
    viewModel: OpenConnectStatusViewModel,
    endpointTag: String,
) {
    val state by viewModel.uiState.collectAsState()
    val endpoint = state.endpoints.firstOrNull { it.endpointTag == endpointTag }

    OverrideTopBar {
        TopAppBar(
            title = {
                Text(
                    if (state.endpoints.size <= 1) {
                        stringResource(R.string.openconnect)
                    } else {
                        stringResource(R.string.openconnect_with_tag, endpointTag)
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                }
            },
        )
    }

    if (endpoint == null) {
        LaunchedEffect(Unit) {
            navController.navigateUp()
        }
        return
    }

    LaunchedEffect(endpoint.error) {
        if (endpoint.error.isNotEmpty()) {
            viewModel.sendGlobalEvent(UiEvent.ErrorMessage(endpoint.error))
        }
    }

    val context = LocalContext.current
    var showAuthQRCode by remember { mutableStateOf(false) }
    val authForm = if (endpoint.state == "auth-pending") endpoint.authForm else null
    val authURL = authForm?.url.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        SectionHeader(stringResource(R.string.endpoint_status))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StateRow(endpoint.state, endpoint.stateText)
                val tunnelInfo = endpoint.tunnelInfo
                if (endpoint.state == "connected" && tunnelInfo != null) {
                    if (tunnelInfo.server.isNotEmpty()) {
                        TunnelInfoRow(tunnelInfo.server, stringResource(R.string.endpoint_server))
                    }
                    if (tunnelInfo.flavor.isNotEmpty()) {
                        TunnelInfoRow(tunnelInfo.flavor, stringResource(R.string.endpoint_flavor))
                    }
                    if (tunnelInfo.transport.isNotEmpty()) {
                        TunnelInfoRow(tunnelInfo.transport, stringResource(R.string.endpoint_transport))
                    }
                    if (tunnelInfo.ipv4.isNotEmpty()) {
                        TunnelInfoRow(tunnelInfo.ipv4.joinToString(", "), stringResource(R.string.endpoint_ipv4))
                    }
                    if (tunnelInfo.ipv6.isNotEmpty()) {
                        TunnelInfoRow(tunnelInfo.ipv6.joinToString(", "), stringResource(R.string.endpoint_ipv6))
                    }
                    if (tunnelInfo.dns.isNotEmpty()) {
                        TunnelInfoRow(tunnelInfo.dns.joinToString(", "), stringResource(R.string.endpoint_dns))
                    }
                    if (tunnelInfo.mtu > 0) {
                        TunnelInfoRow(tunnelInfo.mtu.toString(), stringResource(R.string.endpoint_mtu))
                    }
                    if (tunnelInfo.connectedSince > 0) {
                        val connectedText = DateUtils.getRelativeTimeSpanString(
                            tunnelInfo.connectedSince * 1000,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            0,
                        ).toString()
                        TunnelInfoRow(connectedText, stringResource(R.string.endpoint_connected))
                    }
                }
            }
        }

        if (authForm != null) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.endpoint_authentication))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (authURL.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authURL)))
                            }) {
                                Text(stringResource(R.string.endpoint_open_auth_url))
                            }
                            OutlinedButton(onClick = { showAuthQRCode = true }) {
                                Text(stringResource(R.string.endpoint_open_auth_url_qr_code))
                            }
                        }
                    } else {
                        AuthFormContent(viewModel, endpointTag, authForm)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAuthQRCode && authURL.isNotEmpty()) {
        val qrBitmap = QRCodeGenerator.rememberBitmap(authURL)
        QRCodeDialog(
            bitmap = qrBitmap,
            onDismiss = { showAuthQRCode = false },
        )
    }
}

@Composable
private fun ColumnScope.AuthFormContent(
    viewModel: OpenConnectStatusViewModel,
    endpointTag: String,
    form: OpenConnectAuthFormData,
) {
    val scope = rememberCoroutineScope()
    var submitting by remember(form.id) { mutableStateOf(false) }
    var submitted by remember(form.id) { mutableStateOf(false) }
    val values = remember(form.id) {
        mutableStateMapOf<String, String>().apply {
            for (field in form.fields) {
                put(field.submissionKey, initialFieldValue(field))
            }
        }
    }

    LaunchedEffect(form.id) {
        if (form.error.isNotEmpty()) {
            viewModel.sendGlobalEvent(UiEvent.ErrorMessage(form.error))
        }
    }

    if (submitted) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Text(stringResource(R.string.endpoint_verifying))
        }
        return
    }

    if (form.banner.isNotEmpty()) {
        Text(
            form.banner,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (form.message.isNotEmpty()) {
        Text(form.message, style = MaterialTheme.typography.bodyMedium)
    }
    for (field in form.fields) {
        AuthFormFieldInput(
            field = field,
            value = values[field.submissionKey].orEmpty(),
            onValueChange = { values[field.submissionKey] = it },
            enabled = !submitting,
        )
    }
    Button(
        onClick = {
            scope.launch {
                submitting = true
                val message = viewModel.submitAuthForm(endpointTag, form.id, values.toMap())
                submitting = false
                if (message != null) {
                    viewModel.sendGlobalEvent(UiEvent.ErrorMessage(message))
                } else {
                    submitted = true
                }
            }
        },
        enabled = !submitting,
        modifier = Modifier.align(Alignment.End),
    ) {
        Text(
            stringResource(
                if (form.fields.isEmpty()) R.string.endpoint_continue else R.string.endpoint_submit,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthFormFieldInput(
    field: OpenConnectAuthFormFieldData,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    val label = field.label.ifEmpty { field.name }
    when (field.kind) {
        "select" -> {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = field.options.firstOrNull { it.value == value }?.label ?: value,
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    for (option in field.options) {
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onValueChange(option.value)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        "password" -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
                enabled = enabled,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun initialFieldValue(field: OpenConnectAuthFormFieldData): String {
    if (field.kind != "select") {
        return field.value
    }
    return if (field.options.any { it.value == field.value }) {
        field.value
    } else {
        field.options.firstOrNull()?.value.orEmpty()
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

@Composable
private fun StateRow(state: String, stateText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.endpoint_state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(stateColor(state)),
            )
            Text(
                stateText,
                style = MaterialTheme.typography.bodyMedium,
                color = stateColor(state),
            )
        }
    }
}

@Composable
private fun TunnelInfoRow(value: String, label: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun stateColor(state: String): Color = when (state) {
    "connected" -> Color(0xFF4CAF50)
    "auth-pending" -> Color(0xFFFF9800)
    "connecting" -> Color(0xFFFFEB3B)
    "error" -> Color(0xFFF44336)
    else -> Color.Gray
}
