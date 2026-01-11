package io.nekohasekai.sfa.compose.screen.profileoverride

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ktx.clipboardText
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.shared.AppSelectionCard
import io.nekohasekai.sfa.compose.shared.PackageCache
import io.nekohasekai.sfa.compose.shared.SortMode
import io.nekohasekai.sfa.compose.shared.buildDisplayPackages
import io.nekohasekai.sfa.vendor.PackageQueryManager
import io.nekohasekai.sfa.vendor.PrivilegedAccessRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

private data class LoadResult(
    val proxyMode: Int,
    val packages: List<PackageCache>,
    val selectedUids: Set<Int>,
)

private data class ScanProgress(
    val current: Int,
    val max: Int,
)

private sealed class ScanResult {
    data object Empty : ScanResult()
    data class Found(val apps: Map<String, PackageCache>) : ScanResult()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppProxyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var proxyMode by remember { mutableStateOf(Settings.perAppProxyMode) }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var sortReverse by remember { mutableStateOf(false) }
    var hideSystemApps by remember { mutableStateOf(false) }
    var hideOfflineApps by remember { mutableStateOf(true) }
    var hideDisabledApps by remember { mutableStateOf(true) }

    var packages by remember { mutableStateOf<List<PackageCache>>(emptyList()) }
    var displayPackages by remember { mutableStateOf<List<PackageCache>>(emptyList()) }
    var currentPackages by remember { mutableStateOf<List<PackageCache>>(emptyList()) }
    var selectedUids by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var scanProgress by remember { mutableStateOf<ScanProgress?>(null) }
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }

    fun buildPackageList(newUids: Set<Int>): Set<String> {
        return newUids.mapNotNull { uid ->
            packages.find { it.uid == uid }?.packageName
        }.toSet()
    }

    fun updateCurrentPackages(filterQuery: String) {
        currentPackages =
            if (filterQuery.isEmpty()) {
                displayPackages
            } else {
                displayPackages.filter {
                    it.applicationLabel.contains(filterQuery, ignoreCase = true) ||
                        it.packageName.contains(filterQuery, ignoreCase = true) ||
                        it.uid.toString().contains(filterQuery)
                }
            }
    }

    fun applyFilter() {
        displayPackages =
            buildDisplayPackages(
                packages = packages,
                selectedUids = selectedUids,
                selectedFirst = true,
                hideSystemApps = hideSystemApps,
                hideOfflineApps = hideOfflineApps,
                hideDisabledApps = hideDisabledApps,
                sortMode = sortMode,
                sortReverse = sortReverse,
            )
        currentPackages = displayPackages
    }

    fun saveSelectedApplications(newUids: Set<Int>) {
        coroutineScope.launch {
            Settings.perAppProxyList = buildPackageList(newUids)
        }
    }

    fun postSaveSelectedApplications(newUids: Set<Int>) {
        selectedUids = newUids
        saveSelectedApplications(newUids)
    }

    fun toggleSelection(packageCache: PackageCache, selected: Boolean) {
        val newSelected =
            if (selected) {
                selectedUids + packageCache.uid
            } else {
                selectedUids - packageCache.uid
            }
        if (newSelected == selectedUids) return
        selectedUids = newSelected
        saveSelectedApplications(newSelected)
    }

    fun startScan() {
        if (scanProgress != null) return
        val scanPackages = currentPackages.toList()
        if (scanPackages.isEmpty()) return
        scanProgress = ScanProgress(0, scanPackages.size)
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            val foundApps =
                withContext(Dispatchers.Default) {
                    mutableMapOf<String, PackageCache>().also { found ->
                        val progressInt = AtomicInteger()
                        scanPackages.map { packageCache ->
                            async {
                                if (PerAppProxyScanner.scanChinaPackage(packageCache.info)) {
                                    found[packageCache.packageName] = packageCache
                                }
                                val nextValue = progressInt.incrementAndGet()
                                withContext(Dispatchers.Main) {
                                    scanProgress = ScanProgress(nextValue, scanPackages.size)
                                }
                            }
                        }.awaitAll()
                    }
                }
            Log.d(
                "PerAppProxyScanner",
                "Scan China apps took ${(System.currentTimeMillis() - startTime).toDouble() / 1000}s",
            )
            scanProgress = null
            scanResult = if (foundApps.isEmpty()) ScanResult.Empty else ScanResult.Found(foundApps)
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        val packageManagerFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES or
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_PERMISSIONS or PackageManager.GET_UNINSTALLED_PACKAGES or
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            }
        val retryFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_PERMISSIONS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_UNINSTALLED_PACKAGES or PackageManager.GET_PERMISSIONS
            }
        val loadResult =
            withContext(Dispatchers.IO) {
                try {
                    val mode =
                        if (Settings.perAppProxyMode == Settings.PER_APP_PROXY_INCLUDE) {
                            Settings.PER_APP_PROXY_INCLUDE
                        } else {
                            Settings.PER_APP_PROXY_EXCLUDE
                        }
                    val installedPackages = PackageQueryManager.getInstalledPackages(packageManagerFlags, retryFlags)
                    val packageManager = context.packageManager
                    val packageCaches =
                        installedPackages.mapNotNull { packageInfo ->
                            if (packageInfo.packageName == context.packageName) return@mapNotNull null
                            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                            PackageCache(packageInfo, appInfo, packageManager)
                        }
                    val selectedPackageNames = Settings.perAppProxyList.toMutableSet()
                    val selectedUidSet =
                        packageCaches.mapNotNull { packageCache ->
                            if (selectedPackageNames.contains(packageCache.packageName)) {
                                packageCache.uid
                            } else {
                                null
                            }
                        }.toSet()
                    LoadResult(mode, packageCaches, selectedUidSet)
                } catch (_: PrivilegedAccessRequiredException) {
                    null
                }
            }
        if (loadResult == null) {
            Toast.makeText(
                context,
                R.string.privileged_access_required,
                Toast.LENGTH_LONG,
            ).show()
            onBack()
            return@LaunchedEffect
        }
        proxyMode = loadResult.proxyMode
        packages = loadResult.packages
        selectedUids = loadResult.selectedUids
        applyFilter()
        updateCurrentPackages(searchQuery)
        isLoading = false
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.per_app_proxy)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                            updateCurrentPackages("")
                            focusManager.clearFocus()
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                    )
                }
                PerAppProxyMenus(
                    proxyMode = proxyMode,
                    sortMode = sortMode,
                    sortReverse = sortReverse,
                    hideSystemApps = hideSystemApps,
                    hideOfflineApps = hideOfflineApps,
                    hideDisabledApps = hideDisabledApps,
                    onModeChange = { mode ->
                        proxyMode = mode
                        coroutineScope.launch {
                            Settings.perAppProxyMode = mode
                        }
                    },
                    onSortModeChange = { mode ->
                        sortMode = mode
                        applyFilter()
                    },
                    onSortReverseToggle = {
                        sortReverse = !sortReverse
                        applyFilter()
                    },
                    onHideSystemAppsToggle = {
                        hideSystemApps = !hideSystemApps
                        applyFilter()
                    },
                    onHideOfflineAppsToggle = {
                        hideOfflineApps = !hideOfflineApps
                        applyFilter()
                    },
                    onHideDisabledAppsToggle = {
                        hideDisabledApps = !hideDisabledApps
                        applyFilter()
                    },
                    onSelectAll = {
                        val newSelected = currentPackages.map { it.uid }.toSet()
                        postSaveSelectedApplications(newSelected)
                    },
                    onDeselectAll = {
                        postSaveSelectedApplications(emptySet())
                    },
                    onImport = {
                        val packageNames =
                            clipboardText?.split("\n")?.distinct()
                                ?.takeIf { it.isNotEmpty() && it[0].isNotEmpty() }
                        if (packageNames.isNullOrEmpty()) {
                            Toast.makeText(
                                context,
                                R.string.toast_clipboard_empty,
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            val newSelected =
                                packages.mapNotNull { packageCache ->
                                    if (packageNames.contains(packageCache.packageName)) {
                                        packageCache.uid
                                    } else {
                                        null
                                    }
                                }.toSet()
                            postSaveSelectedApplications(newSelected)
                            Toast.makeText(
                                context,
                                R.string.toast_imported_from_clipboard,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onExport = {
                        val packageList =
                            packages.mapNotNull { packageCache ->
                                if (selectedUids.contains(packageCache.uid)) {
                                    packageCache.packageName
                                } else {
                                    null
                                }
                            }
                        clipboardText = packageList.joinToString("\n")
                        Toast.makeText(
                            context,
                            R.string.toast_copied_to_clipboard,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onScanChinaApps = { startScan() },
                )
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text =
                    if (proxyMode == Settings.PER_APP_PROXY_INCLUDE) {
                        stringResource(R.string.per_app_proxy_mode_include_description)
                    } else {
                        stringResource(R.string.per_app_proxy_mode_exclude_description)
                    },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        AnimatedVisibility(
            visible = isSearchActive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(isSearchActive) {
                if (isSearchActive) {
                    focusRequester.requestFocus()
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    updateCurrentPackages(it)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            updateCurrentPackages("")
                            focusManager.clearFocus()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.content_description_clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(currentPackages, key = { it.packageName }) { packageCache ->
                AppSelectionCard(
                    packageCache = packageCache,
                    selected = selectedUids.contains(packageCache.uid),
                    onToggle = { selected -> toggleSelection(packageCache, selected) },
                    onCopyLabel = { clipboardText = packageCache.applicationLabel },
                    onCopyPackage = { clipboardText = packageCache.packageName },
                    onCopyUid = { clipboardText = packageCache.uid.toString() },
                )
            }
        }
    }

    if (scanProgress != null) {
        val progress = scanProgress
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.message_scanning),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (progress == null || progress.max == 0) {
                                0f
                            } else {
                                progress.current.toFloat() / progress.max.toFloat()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (progress != null) {
                        Text(
                            text = "${progress.current}/${progress.max}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    when (val result = scanResult) {
        ScanResult.Empty -> {
            Dialog(
                onDismissRequest = { scanResult = null },
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.title_scan_result),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.message_scan_app_no_apps_found),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { scanResult = null }) {
                                Text(stringResource(R.string.ok))
                            }
                        }
                    }
                }
            }
        }

        is ScanResult.Found -> {
            val dialogContent =
                stringResource(R.string.message_scan_app_found) + "\n\n" +
                    result.apps.entries.joinToString("\n") {
                        "${it.value.applicationLabel} (${it.key})"
                    }
            Dialog(
                onDismissRequest = { scanResult = null },
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.title_scan_result),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = dialogContent,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { scanResult = null }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            TextButton(
                                onClick = {
                                    val newSelected = selectedUids.toMutableSet()
                                    result.apps.values.forEach {
                                        newSelected.remove(it.uid)
                                    }
                                    postSaveSelectedApplications(newSelected)
                                    scanResult = null
                                },
                            ) {
                                Text(stringResource(R.string.action_deselect))
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            TextButton(
                                onClick = {
                                    val newSelected = selectedUids.toMutableSet()
                                    result.apps.values.forEach {
                                        newSelected.add(it.uid)
                                    }
                                    postSaveSelectedApplications(newSelected)
                                    scanResult = null
                                },
                            ) {
                                Text(stringResource(R.string.per_app_proxy_select))
                            }
                        }
                    }
                }
            }
        }

        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerAppProxyMenus(
    proxyMode: Int,
    sortMode: SortMode,
    sortReverse: Boolean,
    hideSystemApps: Boolean,
    hideOfflineApps: Boolean,
    hideDisabledApps: Boolean,
    onModeChange: (Int) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onSortReverseToggle: () -> Unit,
    onHideSystemAppsToggle: () -> Unit,
    onHideOfflineAppsToggle: () -> Unit,
    onHideDisabledAppsToggle: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onScanChinaApps: () -> Unit,
) {
    var showMainMenu by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSelectMenu by remember { mutableStateOf(false) }
    var showBackupMenu by remember { mutableStateOf(false) }
    var showScanMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMainMenu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }

        DropdownMenu(
            expanded = showMainMenu,
            onDismissRequest = {
                showMainMenu = false
                showModeMenu = false
                showSortMenu = false
                showFilterMenu = false
                showSelectMenu = false
                showBackupMenu = false
                showScanMenu = false
            },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.per_app_proxy_mode)) },
                onClick = { showModeMenu = !showModeMenu },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector =
                            if (showModeMenu) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                        contentDescription = null,
                    )
                },
            )
            if (showModeMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_mode_include)) },
                    onClick = {
                        onModeChange(Settings.PER_APP_PROXY_INCLUDE)
                        showMainMenu = false
                        showModeMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (proxyMode == Settings.PER_APP_PROXY_INCLUDE) {
                                    Icons.Default.RadioButtonChecked
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (proxyMode == Settings.PER_APP_PROXY_INCLUDE) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_mode_exclude)) },
                    onClick = {
                        onModeChange(Settings.PER_APP_PROXY_EXCLUDE)
                        showMainMenu = false
                        showModeMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (proxyMode == Settings.PER_APP_PROXY_EXCLUDE) {
                                    Icons.Default.RadioButtonChecked
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (proxyMode == Settings.PER_APP_PROXY_EXCLUDE) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.per_app_proxy_sort_mode)) },
                onClick = { showSortMenu = !showSortMenu },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector =
                            if (showSortMenu) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                        contentDescription = null,
                    )
                },
            )
            if (showSortMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_sort_mode_name)) },
                    onClick = {
                        onSortModeChange(SortMode.NAME)
                        showMainMenu = false
                        showSortMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (sortMode == SortMode.NAME) {
                                    Icons.Default.RadioButtonChecked
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (sortMode == SortMode.NAME) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_sort_mode_package_name)) },
                    onClick = {
                        onSortModeChange(SortMode.PACKAGE_NAME)
                        showMainMenu = false
                        showSortMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (sortMode == SortMode.PACKAGE_NAME) {
                                    Icons.Default.RadioButtonChecked
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (sortMode == SortMode.PACKAGE_NAME) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_sort_mode_uid)) },
                    onClick = {
                        onSortModeChange(SortMode.UID)
                        showMainMenu = false
                        showSortMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (sortMode == SortMode.UID) {
                                    Icons.Default.RadioButtonChecked
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (sortMode == SortMode.UID) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_sort_mode_install_time)) },
                    onClick = {
                        onSortModeChange(SortMode.INSTALL_TIME)
                        showMainMenu = false
                        showSortMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (sortMode == SortMode.INSTALL_TIME) {
                                    Icons.Default.RadioButtonChecked
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (sortMode == SortMode.INSTALL_TIME) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_sort_mode_update_time)) },
                    onClick = {
                        onSortModeChange(SortMode.UPDATE_TIME)
                        showMainMenu = false
                        showSortMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (sortMode == SortMode.UPDATE_TIME) {
                                    Icons.Default.RadioButtonChecked
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (sortMode == SortMode.UPDATE_TIME) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_sort_mode_reverse)) },
                    onClick = {
                        onSortReverseToggle()
                        showMainMenu = false
                        showSortMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (sortReverse) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (sortReverse) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.per_app_proxy_filter)) },
                onClick = { showFilterMenu = !showFilterMenu },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector =
                            if (showFilterMenu) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                        contentDescription = null,
                    )
                },
            )
            if (showFilterMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_hide_system_apps)) },
                    onClick = {
                        onHideSystemAppsToggle()
                        showMainMenu = false
                        showFilterMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (hideSystemApps) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (hideSystemApps) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_hide_offline_apps)) },
                    onClick = {
                        onHideOfflineAppsToggle()
                        showMainMenu = false
                        showFilterMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (hideOfflineApps) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (hideOfflineApps) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_hide_disabled_apps)) },
                    onClick = {
                        onHideDisabledAppsToggle()
                        showMainMenu = false
                        showFilterMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (hideDisabledApps) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.RadioButtonUnchecked
                                },
                            contentDescription = null,
                            tint =
                                if (hideDisabledApps) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.per_app_proxy_select)) },
                onClick = { showSelectMenu = !showSelectMenu },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector =
                            if (showSelectMenu) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                        contentDescription = null,
                    )
                },
            )
            if (showSelectMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_select_all)) },
                    onClick = {
                        onSelectAll()
                        showMainMenu = false
                        showSelectMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_select_none)) },
                    onClick = {
                        onDeselectAll()
                        showMainMenu = false
                        showSelectMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.per_app_proxy_backup)) },
                onClick = { showBackupMenu = !showBackupMenu },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector =
                            if (showBackupMenu) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                        contentDescription = null,
                    )
                },
            )
            if (showBackupMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_import)) },
                    onClick = {
                        onImport()
                        showMainMenu = false
                        showBackupMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_export)) },
                    onClick = {
                        onExport()
                        showMainMenu = false
                        showBackupMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.per_app_proxy_scan)) },
                onClick = { showScanMenu = !showScanMenu },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ManageSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector =
                            if (showScanMenu) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                        contentDescription = null,
                    )
                },
            )
            if (showScanMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_scan_china_apps)) },
                    onClick = {
                        onScanChinaApps()
                        showMainMenu = false
                        showScanMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ManageSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    },
                )
            }
        }
    }
}

object PerAppProxyScanner {
    private val skipPrefixList =
        listOf(
            "com.google",
            "com.android.chrome",
            "com.android.vending",
            "com.microsoft",
            "com.apple",
            "com.zhiliaoapp.musically", // Banned by China
            "com.android.providers.downloads",
        )

    private val chinaAppPrefixList =
        listOf(
            "com.tencent",
            "com.alibaba",
            "com.umeng",
            "com.qihoo",
            "com.ali",
            "com.alipay",
            "com.amap",
            "com.sina",
            "com.weibo",
            "com.vivo",
            "com.xiaomi",
            "com.huawei",
            "com.taobao",
            "com.secneo",
            "s.h.e.l.l",
            "com.stub",
            "com.kiwisec",
            "com.secshell",
            "com.wrapper",
            "cn.securitystack",
            "com.mogosec",
            "com.secoen",
            "com.netease",
            "com.mx",
            "com.qq.e",
            "com.baidu",
            "com.bytedance",
            "com.bugly",
            "com.miui",
            "com.oppo",
            "com.coloros",
            "com.iqoo",
            "com.meizu",
            "com.gionee",
            "cn.nubia",
            "com.oplus",
            "andes.oplus",
            "com.unionpay",
            "cn.wps",
        )

    private val chinaAppRegex by lazy {
        ("(" + chinaAppPrefixList.joinToString("|").replace(".", "\\.") + ").*").toRegex()
    }

    fun scanChinaPackage(packageInfo: PackageInfo): Boolean {
        val packageName = packageInfo.packageName
        skipPrefixList.forEach {
            if (packageName == it || packageName.startsWith("$it.")) return false
        }

        if (packageName.matches(chinaAppRegex)) {
            Log.d("PerAppProxyScanner", "Match package name: $packageName")
            return true
        }
        try {
            val appInfo = packageInfo.applicationInfo ?: return false
            packageInfo.services?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Log.d("PerAppProxyScanner", "Match service ${it.name} in $packageName")
                    return true
                }
            }
            packageInfo.activities?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Log.d("PerAppProxyScanner", "Match activity ${it.name} in $packageName")
                    return true
                }
            }
            packageInfo.receivers?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Log.d("PerAppProxyScanner", "Match receiver ${it.name} in $packageName")
                    return true
                }
            }
            packageInfo.providers?.forEach {
                if (it.name.matches(chinaAppRegex)) {
                    Log.d("PerAppProxyScanner", "Match provider ${it.name} in $packageName")
                    return true
                }
            }
            ZipFile(File(appInfo.publicSourceDir)).use {
                for (packageEntry in it.entries()) {
                    if (packageEntry.name.startsWith("firebase-")) return false
                }
                for (packageEntry in it.entries()) {
                    if (!(
                            packageEntry.name.startsWith("classes") &&
                                packageEntry.name.endsWith(".dex")
                        )
                    ) {
                        continue
                    }
                    if (packageEntry.size > 15000000) {
                        Log.d(
                            "PerAppProxyScanner",
                            "Confirm $packageName due to large dex file",
                        )
                        return true
                    }
                    val input = it.getInputStream(packageEntry).buffered()
                    val dexFile =
                        try {
                            DexBackedDexFile.fromInputStream(null, input)
                        } catch (e: Exception) {
                            Log.e("PerAppProxyScanner", "Error reading dex file", e)
                            return false
                        }
                    for (clazz in dexFile.classes) {
                        val clazzName =
                            clazz.type.substring(1, clazz.type.length - 1).replace("/", ".")
                                .replace("$", ".")
                        if (clazzName.matches(chinaAppRegex)) {
                            Log.d("PerAppProxyScanner", "Match $clazzName in $packageName")
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PerAppProxyScanner", "Error scanning package $packageName", e)
        }
        return false
    }
}
