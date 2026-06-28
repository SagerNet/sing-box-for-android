package io.nekohasekai.sfa.compose.screen.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.nekohasekai.sfa.R

@Composable
fun ReportShareDialog(
    hasConfig: Boolean,
    hasLog: Boolean,
    onSave: (includeConfig: Boolean, includeLog: Boolean, useAgeEncryption: Boolean) -> Unit,
    onShare: (includeConfig: Boolean, includeLog: Boolean, useAgeEncryption: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeConfig by remember { mutableStateOf(false) }
    var includeLog by remember { mutableStateOf(true) }
    var useAgeEncryption by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_share)) },
        text = {
            Column {
                if (hasLog) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.report_with_log),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = includeLog, onCheckedChange = { includeLog = it })
                    }
                }
                if (hasConfig) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.report_with_config),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = includeConfig, onCheckedChange = { includeConfig = it })
                    }
                }
                if (hasConfig || hasLog) {
                    Text(
                        text = if (hasLog) {
                            stringResource(R.string.report_share_privacy_warning)
                        } else {
                            stringResource(R.string.report_share_privacy_warning_config)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.report_with_age_encryption),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = useAgeEncryption, onCheckedChange = { useAgeEncryption = it })
                }
                MarkdownText(
                    markdown = stringResource(R.string.report_age_encryption_description),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onSave(includeConfig, includeLog, useAgeEncryption) }) {
                    Text(stringResource(R.string.save))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = { onShare(includeConfig, includeLog, useAgeEncryption) }) {
                    Text(stringResource(R.string.report_share))
                }
            }
        },
    )
}
