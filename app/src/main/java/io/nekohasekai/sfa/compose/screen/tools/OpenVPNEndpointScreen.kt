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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.component.qr.QRCodeDialog
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.util.QRCodeGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OpenVPNEndpointScreen(
    navController: NavController,
    viewModel: OpenVPNStatusViewModel,
    endpointTag: String,
) {
    val state by viewModel.uiState.collectAsState()
    val endpoint = state.endpoints.firstOrNull { it.endpointTag == endpointTag }

    OverrideTopBar {
        TopAppBar(
            title = {
                Text(
                    if (state.endpoints.size <= 1) {
                        stringResource(R.string.openvpn)
                    } else {
                        stringResource(R.string.openvpn_with_tag, endpointTag)
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
    val challenge = if (endpoint.state == "auth-pending") endpoint.challenge else null
    val authURL = if (challenge?.kind == "open-url") challenge.url else ""

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
                    if (tunnelInfo.network.isNotEmpty()) {
                        TunnelInfoRow(tunnelInfo.network, stringResource(R.string.endpoint_network))
                    }
                    if (tunnelInfo.cipher.isNotEmpty()) {
                        TunnelInfoRow(tunnelInfo.cipher, stringResource(R.string.endpoint_cipher))
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

        if (challenge != null && (challenge.kind == "open-url" || challenge.kind == "credentials" || challenge.kind == "secret")) {
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
                    if (challenge.kind == "open-url") {
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
                        ChallengeContent(viewModel, endpointTag, challenge)
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
private fun ColumnScope.ChallengeContent(
    viewModel: OpenVPNStatusViewModel,
    endpointTag: String,
    challenge: OpenVPNChallengeData,
) {
    val scope = rememberCoroutineScope()
    var submitting by remember(challenge.id) { mutableStateOf(false) }
    var submitted by remember(challenge.id) { mutableStateOf(false) }
    var username by remember(challenge.id) { mutableStateOf(challenge.username) }
    var password by remember(challenge.id) { mutableStateOf("") }
    var secret by remember(challenge.id) { mutableStateOf("") }

    LaunchedEffect(challenge.id) {
        if (challenge.previousError.isNotEmpty()) {
            viewModel.sendGlobalEvent(UiEvent.ErrorMessage(challenge.previousError))
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val deadlineMillis = challenge.deadline * 1000
    if (deadlineMillis > 0) {
        LaunchedEffect(challenge.id) {
            while (true) {
                delay(1000)
                now = System.currentTimeMillis()
            }
        }
    }
    val expired = deadlineMillis in 1..now
    val editable = !submitting && !expired

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

    if (challenge.message.isNotEmpty()) {
        Text(challenge.message, style = MaterialTheme.typography.bodyMedium)
    }
    if (deadlineMillis > 0) {
        val remainingSeconds = ((deadlineMillis - now) / 1000).coerceAtLeast(0)
        Text(
            String.format(Locale.US, "%d:%02d", remainingSeconds / 60, remainingSeconds % 60),
            style = MaterialTheme.typography.labelLarge,
            color = if (expired) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.tertiary
            },
        )
    }
    if (challenge.kind == "credentials") {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.endpoint_username)) },
            singleLine = true,
            enabled = editable,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.endpoint_password)) },
            singleLine = true,
            enabled = editable,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (challenge.secretMessage.isNotEmpty()) {
            SecretField(challenge, secret, { secret = it }, editable)
        }
    } else {
        SecretField(challenge, secret, { secret = it }, editable)
    }
    Button(
        onClick = {
            scope.launch {
                submitting = true
                val message = viewModel.submitChallengeResponse(
                    endpointTag = endpointTag,
                    challengeID = challenge.id,
                    username = if (challenge.kind == "credentials") username else "",
                    password = if (challenge.kind == "credentials") password else "",
                    secret = secret,
                )
                submitting = false
                if (message != null) {
                    viewModel.sendGlobalEvent(UiEvent.ErrorMessage(message))
                } else {
                    submitted = true
                }
            }
        },
        enabled = editable,
        modifier = Modifier.align(Alignment.End),
    ) {
        Text(stringResource(R.string.endpoint_submit))
    }
}

@Composable
private fun SecretField(
    challenge: OpenVPNChallengeData,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(challenge.secretMessage.ifEmpty { stringResource(R.string.endpoint_response) })
        },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (challenge.echo) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = if (challenge.echo) {
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            )
        } else {
            KeyboardOptions(keyboardType = KeyboardType.Password)
        },
        modifier = Modifier.fillMaxWidth(),
    )
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
