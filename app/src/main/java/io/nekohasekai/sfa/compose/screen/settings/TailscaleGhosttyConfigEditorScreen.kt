package io.nekohasekai.sfa.compose.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compat.CodeEditorSyntax
import io.nekohasekai.sfa.compat.ProfileCodeEditor
import io.nekohasekai.sfa.compat.ProfileEditorColors
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscaleGhosttyConfigEditorScreen(navController: NavController, isDark: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var content by remember {
        mutableStateOf(if (isDark) Settings.tailscaleSSHDarkConfig else Settings.tailscaleSSHLightConfig)
    }

    val editor = remember {
        ProfileCodeEditor(context, CodeEditorSyntax.GHOSTTY_CONFIG).apply {
            setText(content)
        }
    }
    editor.onTextChanged = { content = editor.getText() }

    LaunchedEffect(content) {
        delay(300)
        if (isDark) {
            Settings.tailscaleSSHDarkConfig = content
        } else {
            Settings.tailscaleSSHLightConfig = content
        }
    }

    DisposableEffect(Unit) {
        onDispose { editor.release() }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }
                if (imported != null) {
                    editor.setText(imported)
                }
            }
        }
    }

    OverrideTopBar {
        TopAppBar(
            title = {
                Text(
                    if (isDark) {
                        stringResource(R.string.tailscale_terminal_dark_custom_config)
                    } else {
                        stringResource(R.string.tailscale_terminal_light_custom_config)
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                }
            },
            actions = {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.content_description_more_options),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tailscale_terminal_import_config)) },
                        leadingIcon = {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            importLauncher.launch("*/*")
                        },
                    )
                }
            },
        )
    }

    val colorScheme = MaterialTheme.colorScheme
    val editorColors = remember(colorScheme) {
        ProfileEditorColors(
            background = colorScheme.background.toArgb(),
            foreground = colorScheme.onSurface.toArgb(),
            lineNumber = colorScheme.onSurfaceVariant.toArgb(),
            lineNumberBackground = colorScheme.background.toArgb(),
            selectionBackground = colorScheme.primary.copy(alpha = 0.35f).toArgb(),
            currentLineBackground = colorScheme.surfaceContainer.toArgb(),
            cursor = colorScheme.primary.toArgb(),
            matchedTextBackground = colorScheme.tertiary.copy(alpha = 0.35f).toArgb(),
            comment = colorScheme.onSurfaceVariant.toArgb(),
            key = colorScheme.primary.toArgb(),
            string = colorScheme.tertiary.toArgb(),
            number = colorScheme.secondary.toArgb(),
            literal = colorScheme.primary.toArgb(),
        )
    }

    val scaffoldPadding = LocalScaffoldPadding.current

    AndroidView(
        factory = { editor.view },
        update = { editor.applyColors(editorColors) },
        modifier = Modifier
            .fillMaxSize()
            .padding(top = scaffoldPadding.calculateTopPadding())
            .imePadding(),
    )
}
