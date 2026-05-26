package io.nekohasekai.sfa.compose.screen.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.terminal.TailscaleSSHPresentedSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscaleSSHPromptScreen(
    navController: NavController,
    sharedViewModel: TailscaleSSHSharedViewModel,
    viewModel: TailscaleStatusViewModel,
    endpointTag: String,
    peerId: String,
) {
    val peer = viewModel.peer(endpointTag, peerId)

    if (peer == null) {
        LaunchedEffect(Unit) { navController.navigateUp() }
        return
    }

    val rememberedUsernames = Settings.tailscaleSSHRememberedUsernames
    val quickConnectPeers = Settings.tailscaleSSHQuickConnectPeers

    var username by remember {
        mutableStateOf(rememberedUsernames[peer.stableID]?.takeIf { it.isNotBlank() } ?: "root")
    }
    var rememberOptions by remember {
        mutableStateOf(quickConnectPeers.contains(peer.stableID))
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(peer.hostName) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
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
        Text(
            text = stringResource(R.string.tailscale_ssh_options),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.tailscale_ssh_username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tailscale_ssh_terminal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.tailscale_ssh_quick_connect),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.tailscale_ssh_remember_options)) },
                trailingContent = {
                    Switch(
                        checked = rememberOptions,
                        onCheckedChange = { checked ->
                            rememberOptions = checked
                            val peers = Settings.tailscaleSSHQuickConnectPeers.toMutableSet()
                            if (checked) {
                                peers.add(peer.stableID)
                            } else {
                                peers.remove(peer.stableID)
                            }
                            Settings.tailscaleSSHQuickConnectPeers = peers
                        },
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tailscale_ssh_quick_connect_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val trimmedUsername = username.trim()
                val usernames = Settings.tailscaleSSHRememberedUsernames.toMutableMap()
                if (trimmedUsername == "root") {
                    usernames.remove(peer.stableID)
                } else {
                    usernames[peer.stableID] = trimmedUsername
                }
                Settings.tailscaleSSHRememberedUsernames = usernames

                sharedViewModel.setPendingSession(
                    TailscaleSSHPresentedSession(
                        endpointTag = endpointTag,
                        peerHostName = peer.hostName,
                        peerAddress = peer.tailscaleIPs.first(),
                        username = trimmedUsername,
                        hostKeys = peer.sshHostKeys,
                    ),
                )
                navController.navigate(
                    "tools/tailscale/${android.net.Uri.encode(endpointTag)}/peer/${android.net.Uri.encode(peerId)}/terminal",
                ) {
                    popUpTo(
                        "tools/tailscale/${android.net.Uri.encode(endpointTag)}/peer/${android.net.Uri.encode(peerId)}/ssh",
                    ) {
                        inclusive = true
                    }
                }
            },
            enabled = username.trim().isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.tailscale_ssh_connect_button))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
