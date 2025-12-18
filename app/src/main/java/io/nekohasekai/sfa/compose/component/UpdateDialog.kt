package io.nekohasekai.sfa.compose.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.update.UpdateInfo
import org.kodein.emoji.Emoji
import org.kodein.emoji.EmojiTemplateCatalog
import org.kodein.emoji.all

@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val emojiCatalog = remember { EmojiTemplateCatalog(Emoji.all()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.check_update)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.new_version_available, updateInfo.versionName),
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (!updateInfo.releaseNotes.isNullOrBlank()) {
                    val processedNotes = remember(updateInfo.releaseNotes) {
                        emojiCatalog.replaceShortcodes(updateInfo.releaseNotes)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    MarkdownText(
                        markdown = processedNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onUpdate()
                },
            ) {
                Text(stringResource(R.string.update))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl))
                    context.startActivity(intent)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.view_release))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}
