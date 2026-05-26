package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.nekohasekai.sfa.terminal.TerminalColorSchemeLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscaleThemePickerScreen(
    navController: NavController,
    isDark: Boolean,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTheme by remember {
        mutableStateOf(if (isDark) Settings.tailscaleSSHDarkTheme else Settings.tailscaleSSHLightTheme)
    }
    var schemes by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        schemes = TerminalColorSchemeLoader.listSchemes(context, isDark)
    }

    val filteredSchemes = if (searchQuery.isBlank()) {
        schemes
    } else {
        schemes.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    OverrideTopBar {
        TopAppBar(
            title = {
                Text(
                    if (isDark) {
                        stringResource(R.string.tailscale_terminal_dark_theme)
                    } else {
                        stringResource(R.string.tailscale_terminal_light_theme)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.tailscale_terminal_search_themes)) },
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {}

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredSchemes) { scheme ->
                ListItem(
                    headlineContent = { Text(scheme) },
                    leadingContent = {
                        RadioButton(
                            selected = scheme == selectedTheme,
                            onClick = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        selectedTheme = scheme
                        if (isDark) {
                            Settings.tailscaleSSHDarkTheme = scheme
                        } else {
                            Settings.tailscaleSSHLightTheme = scheme
                        }
                    },
                )
            }
        }
    }
}
