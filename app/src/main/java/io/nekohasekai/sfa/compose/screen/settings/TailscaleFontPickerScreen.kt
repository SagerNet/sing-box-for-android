package io.nekohasekai.sfa.compose.screen.settings

import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.terminal.ImportedFont
import io.nekohasekai.sfa.terminal.ImportedFontStore

private val knownMonospaceFamilies = listOf(
    "monospace",
    "Droid Sans Mono",
    "Courier New",
    "Courier",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscaleFontPickerScreen(navController: NavController) {
    val context = LocalContext.current
    var selectedFamily by remember { mutableStateOf(Settings.tailscaleSSHFontFamily) }
    var selectedCustomPath by remember { mutableStateOf(Settings.tailscaleSSHCustomFontPath) }
    var importedFonts by remember { mutableStateOf(ImportedFontStore.listImportedFonts(context)) }

    val systemFonts = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            enumerateSystemMonospaceFonts()
        } else {
            knownMonospaceFamilies
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val imported = ImportedFontStore.importFont(context, uri)
            if (imported != null) {
                importedFonts = ImportedFontStore.listImportedFonts(context)
                selectedFamily = ""
                selectedCustomPath = imported.path
                Settings.tailscaleSSHFontFamily = ""
                Settings.tailscaleSSHCustomFontPath = imported.path
            }
        }
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.tailscale_terminal_font)) },
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
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    text = stringResource(R.string.tailscale_terminal_font_system),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
            item {
                val isDefault = selectedFamily.isBlank() && selectedCustomPath.isBlank()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.tailscale_terminal_font_default)) },
                    leadingContent = {
                        RadioButton(selected = isDefault, onClick = null)
                    },
                    modifier = Modifier.clickable {
                        selectedFamily = ""
                        selectedCustomPath = ""
                        Settings.tailscaleSSHFontFamily = ""
                        Settings.tailscaleSSHCustomFontPath = ""
                    },
                )
            }
            items(systemFonts) { family ->
                val isSelected = selectedFamily == family && selectedCustomPath.isBlank()
                ListItem(
                    headlineContent = { Text(family) },
                    leadingContent = {
                        RadioButton(selected = isSelected, onClick = null)
                    },
                    modifier = Modifier.clickable {
                        selectedFamily = family
                        selectedCustomPath = ""
                        Settings.tailscaleSSHFontFamily = family
                        Settings.tailscaleSSHCustomFontPath = ""
                    },
                )
            }

            if (importedFonts.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tailscale_terminal_font_imported),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    )
                }
                items(importedFonts) { font ->
                    val isSelected = selectedCustomPath == font.path
                    ListItem(
                        headlineContent = { Text(font.name) },
                        leadingContent = {
                            RadioButton(selected = isSelected, onClick = null)
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                ImportedFontStore.deleteFont(context, font.name)
                                importedFonts = ImportedFontStore.listImportedFonts(context)
                                if (isSelected) {
                                    selectedFamily = ""
                                    selectedCustomPath = ""
                                    Settings.tailscaleSSHFontFamily = ""
                                    Settings.tailscaleSSHCustomFontPath = ""
                                }
                            }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            selectedFamily = ""
                            selectedCustomPath = font.path
                            Settings.tailscaleSSHFontFamily = ""
                            Settings.tailscaleSSHCustomFontPath = font.path
                        },
                    )
                }
            }
        }

        Button(
            onClick = { importLauncher.launch("font/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.tailscale_terminal_import_font))
        }
    }
}

private fun enumerateSystemMonospaceFonts(): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return knownMonospaceFamilies

    val families = mutableSetOf<String>()
    try {
        val monoReference = Typeface.MONOSPACE
        for (family in knownMonospaceFamilies) {
            val typeface = Typeface.create(family, Typeface.NORMAL)
            if (typeface != Typeface.DEFAULT) {
                families.add(family)
            }
        }
    } catch (_: Exception) {
    }
    return families.sorted()
}
