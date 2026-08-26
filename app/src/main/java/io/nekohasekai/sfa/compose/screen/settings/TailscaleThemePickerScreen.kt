package io.nekohasekai.sfa.compose.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import io.github.sagernet.libghostty.extras.GhosttyThemeStore
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var themes by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        themes = withContext(Dispatchers.IO) {
            GhosttyThemeStore.listThemes(context, isDark)
        }
        if (selectedTheme !in themes) {
            selectedTheme = GhosttyThemeStore.defaultTheme(isDark)
            if (isDark) {
                Settings.tailscaleSSHDarkTheme = selectedTheme
            } else {
                Settings.tailscaleSSHLightTheme = selectedTheme
            }
        }
    }

    val filteredThemes = if (searchQuery.isBlank()) {
        themes
    } else {
        themes.filter { it.contains(searchQuery, ignoreCase = true) }
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

    val scaffoldPadding = LocalScaffoldPadding.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = scaffoldPadding.calculateTopPadding()),
    ) {
        SearchBarDefaults.InputField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.tailscale_terminal_search_themes)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            items(filteredThemes) { theme ->
                ListItem(
                    headlineContent = { Text(theme) },
                    leadingContent = {
                        RadioButton(
                            selected = theme == selectedTheme,
                            onClick = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        selectedTheme = theme
                        if (isDark) {
                            Settings.tailscaleSSHDarkTheme = theme
                        } else {
                            Settings.tailscaleSSHLightTheme = theme
                        }
                    },
                )
            }
        }
    }
}
