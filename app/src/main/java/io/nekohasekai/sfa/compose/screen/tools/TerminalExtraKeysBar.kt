package io.nekohasekai.sfa.compose.screen.tools

import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.view.TerminalView
import io.nekohasekai.sfa.terminal.TerminalExtraKeysState
import io.nekohasekai.sfa.terminal.TerminalExtraKeysState.StickyModifierState
import kotlinx.coroutines.delay

@Composable
fun TerminalExtraKeysBar(
    terminalView: TerminalView?,
    extraKeysState: TerminalExtraKeysState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ctrlState by extraKeysState.ctrlState.collectAsState()
    val altState by extraKeysState.altState.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExtraKeyButton("ESC") {
                terminalView?.handleKeyCode(KeyEvent.KEYCODE_ESCAPE, extraKeysState.currentKeyMod())
                extraKeysState.consumeModifiers()
                terminalView?.requestFocus()
            }
            ExtraKeyButton("TAB") {
                terminalView?.handleKeyCode(KeyEvent.KEYCODE_TAB, extraKeysState.currentKeyMod())
                extraKeysState.consumeModifiers()
                terminalView?.requestFocus()
            }

            StickyModifierButton("CTRL", ctrlState) {
                extraKeysState.toggleCtrl(System.currentTimeMillis())
                terminalView?.requestFocus()
            }
            StickyModifierButton("ALT", altState) {
                extraKeysState.toggleAlt(System.currentTimeMillis())
                terminalView?.requestFocus()
            }

            Divider()

            RepeatableKeyButton("◀") {
                terminalView?.handleKeyCode(
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    extraKeysState.currentKeyMod(),
                )
                extraKeysState.consumeModifiers()
                terminalView?.requestFocus()
            }
            RepeatableKeyButton("▲") {
                terminalView?.handleKeyCode(
                    KeyEvent.KEYCODE_DPAD_UP,
                    extraKeysState.currentKeyMod(),
                )
                extraKeysState.consumeModifiers()
                terminalView?.requestFocus()
            }
            RepeatableKeyButton("▼") {
                terminalView?.handleKeyCode(
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    extraKeysState.currentKeyMod(),
                )
                extraKeysState.consumeModifiers()
                terminalView?.requestFocus()
            }
            RepeatableKeyButton("▶") {
                terminalView?.handleKeyCode(
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    extraKeysState.currentKeyMod(),
                )
                extraKeysState.consumeModifiers()
                terminalView?.requestFocus()
            }

            Divider()

            for (ch in symbolKeys) {
                ExtraKeyButton(ch.toString()) {
                    terminalView?.inputCodePoint(
                        TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD,
                        ch.code,
                        false,
                        false,
                    )
                    extraKeysState.consumeModifiers()
                    terminalView?.requestFocus()
                }
            }

            IconKeyButton(Icons.Default.ContentPaste) {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(context).toString()
                    if (text.isNotEmpty()) {
                        terminalView?.mEmulator?.paste(text)
                    }
                }
                extraKeysState.consumeModifiers()
                terminalView?.requestFocus()
            }
        }
    }
}

private val symbolKeys = charArrayOf('|', '/', '~', '-', '_', '`', '\'', '"')

@Composable
private fun ExtraKeyButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 42.dp).height(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = label,
                fontSize = if (label.length > 1) 10.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StickyModifierButton(
    label: String,
    state: StickyModifierState,
    onClick: () -> Unit,
) {
    val backgroundColor = when (state) {
        StickyModifierState.INACTIVE -> MaterialTheme.colorScheme.surfaceContainerHighest
        StickyModifierState.ARMED -> MaterialTheme.colorScheme.primaryContainer
        StickyModifierState.LOCKED -> MaterialTheme.colorScheme.primary
    }
    val textColor = when (state) {
        StickyModifierState.INACTIVE -> MaterialTheme.colorScheme.onSurface
        StickyModifierState.ARMED -> MaterialTheme.colorScheme.onPrimaryContainer
        StickyModifierState.LOCKED -> MaterialTheme.colorScheme.onPrimary
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 42.dp).height(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RepeatableKeyButton(label: String, onAction: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(REPEAT_INITIAL_DELAY_MS)
            while (true) {
                onAction()
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    Surface(
        onClick = onAction,
        interactionSource = interactionSource,
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun IconKeyButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
    )
}

private const val REPEAT_INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 80L
