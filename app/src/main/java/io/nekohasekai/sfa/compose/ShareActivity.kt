package io.nekohasekai.sfa.compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.ServiceConnection
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.SelectableMessageDialog
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.screen.tools.TaildropSendFile
import io.nekohasekai.sfa.compose.screen.tools.TaildropSendManager
import io.nekohasekai.sfa.compose.screen.tools.TaildropSendProgress
import io.nekohasekai.sfa.compose.screen.tools.TailscaleEndpointData
import io.nekohasekai.sfa.compose.screen.tools.TailscalePeerData
import io.nekohasekai.sfa.compose.screen.tools.TailscaleStatusViewModel
import io.nekohasekai.sfa.compose.theme.Theme
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.RemoteControlManager
import kotlinx.coroutines.launch

class ShareActivity :
    AppCompatActivity(),
    ServiceConnection.Callback {
    private val connection = ServiceConnection(this, this)
    private var serviceStatus by mutableStateOf(Status.Stopped)
    private var errorMessage by mutableStateOf<String?>(null)
    private var sendID by mutableStateOf(-1L)
    private var sharedFiles: List<TaildropSendFile> = emptyList()
    private val statusViewModel: TailscaleStatusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sendID = savedInstanceState?.getLong(STATE_SEND_ID) ?: -1L
        if (sendID == -1L) {
            try {
                sharedFiles = openSharedContent(intent)
            } catch (e: Exception) {
                errorMessage = e.message ?: e.toString()
            }
        }
        connection.reconnect()
        RemoteControlManager.restore()
        lifecycleScope.launch {
            GlobalEventBus.events.collect { event ->
                if (event is UiEvent.ErrorMessage) {
                    errorMessage = event.message
                }
            }
        }
        setContent {
            Theme {
                ShareSheet()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_SEND_ID, sendID)
    }

    override fun onDestroy() {
        connection.disconnect()
        val files = sharedFiles
        sharedFiles = emptyList()
        for (file in files) {
            file.close()
        }
        super.onDestroy()
    }

    override fun onServiceStatusChanged(status: Status) {
        serviceStatus = status
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        errorMessage = message ?: type.name
    }

    private fun openSharedContent(intent: Intent): List<TaildropSendFile> {
        val uris = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))

            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()

            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            return TaildropSendManager.openURIs(uris)
        }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return listOf(TaildropSendManager.createTextFile(textFileName(intent, text), text))
    }

    private fun textFileName(intent: Intent, text: String): String {
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
        val source = subject ?: text.trim().lineSequence().first()
        val name = source.take(48).replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_', '.')
        return if (name.isEmpty()) "shared.txt" else "$name.txt"
    }

    private fun startSend(endpointTag: String, peer: TailscalePeerData) {
        val files = sharedFiles
        if (files.isEmpty()) return
        sharedFiles = emptyList()
        sendID = TaildropSendManager.send(endpointTag, peer.stableID, peer.displayName, files)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ShareSheet() {
        val state by statusViewModel.uiState.collectAsState()
        val sendSessions by TaildropSendManager.sessions.collectAsState()
        val remoteServer by RemoteControlManager.remoteServer.collectAsState()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedEndpointTag by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(remoteServer?.id, serviceStatus) {
            if (remoteServer != null || serviceStatus == Status.Started) {
                statusViewModel.subscribe()
            } else {
                statusViewModel.cancel()
            }
        }

        val endpoints = state.endpoints.filter { it.backendState == "Running" && it.canShareFiles }
        val endpoint = endpoints.firstOrNull { it.endpointTag == selectedEndpointTag } ?: endpoints.firstOrNull()

        val currentSend = sendSessions.firstOrNull { it.id == sendID }
        val dialogErrorMessage = errorMessage ?: currentSend?.errorMessage
        if (dialogErrorMessage != null) {
            SelectableMessageDialog(
                title = stringResource(R.string.error_title),
                message = dialogErrorMessage,
                onDismiss = {
                    currentSend?.takeIf { it.errorMessage != null }?.let { TaildropSendManager.dismiss(it.id) }
                    finish()
                },
            )
        }

        ModalBottomSheet(
            onDismissRequest = { finish() },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    stringResource(R.string.taildrop),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                when {
                    currentSend != null -> {
                        TaildropSendProgress(currentSend)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            if (currentSend.finished) {
                                if (currentSend.errorMessage == null) {
                                    TextButton(onClick = {
                                        TaildropSendManager.dismiss(currentSend.id)
                                        finish()
                                    }) {
                                        Text(stringResource(R.string.taildrop_done))
                                    }
                                }
                            } else {
                                TextButton(onClick = { TaildropSendManager.cancel(currentSend.id) }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            }
                        }
                    }

                    sharedFiles.isEmpty() -> {
                        Text(
                            stringResource(R.string.taildrop_no_files_selected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    endpoint == null -> {
                        when {
                            remoteServer == null && serviceStatus != Status.Started && serviceStatus != Status.Starting ->
                                ServiceNotStarted()

                            !state.hasUpdate -> Connecting()

                            else -> Text(
                                stringResource(R.string.taildrop_no_targets),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> {
                        if (endpoints.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for (candidate in endpoints) {
                                    FilterChip(
                                        selected = candidate.endpointTag == endpoint.endpointTag,
                                        onClick = { selectedEndpointTag = candidate.endpointTag },
                                        label = { Text(candidate.endpointTag) },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        TargetList(endpoint) { peer ->
                            startSend(endpoint.endpointTag, peer)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ServiceNotStarted() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.service_not_started),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    startActivity(
                        Intent(this@ShareActivity, MainActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    finish()
                },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(stringResource(R.string.taildrop_open_app))
            }
        }
    }

    @Composable
    private fun Connecting() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                stringResource(R.string.taildrop_connecting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun TargetList(endpoint: TailscaleEndpointData, onSelect: (TailscalePeerData) -> Unit) {
        val selfID = endpoint.selfPeer?.stableID
        val peers = endpoint.userGroups
            .flatMap { it.peers }
            .filter { it.canReceiveFiles && it.stableID != selfID }
            .sortedWith(compareByDescending<TailscalePeerData> { it.online }.thenBy { it.displayName })
        if (peers.isEmpty()) {
            Text(
                stringResource(R.string.taildrop_no_targets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        Column(
            modifier = Modifier
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            for (peer in peers) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = peer.online) { onSelect(peer) }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (peer.online) Color(0xFF4CAF50) else Color.Gray),
                    )
                    Text(
                        peer.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (peer.online) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (peer.os.isNotEmpty()) {
                        Text(
                            peer.os,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val STATE_SEND_ID = "send_id"
    }
}
