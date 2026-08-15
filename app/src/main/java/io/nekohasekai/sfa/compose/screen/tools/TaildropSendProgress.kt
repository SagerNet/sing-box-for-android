package io.nekohasekai.sfa.compose.screen.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R

@Composable
fun TaildropSendProgress(state: TaildropSendState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (file in state.files) {
            TaildropTransferItem(
                name = file.name,
                peerLabel = stringResource(R.string.taildrop_to, state.peerName),
                transferredBytes = file.sentBytes,
                totalBytes = file.size,
                onCancel = null,
                finished = state.finished,
            )
        }
    }
}
