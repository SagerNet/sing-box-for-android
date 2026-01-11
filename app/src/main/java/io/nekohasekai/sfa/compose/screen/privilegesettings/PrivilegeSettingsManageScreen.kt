package io.nekohasekai.sfa.compose.screen.privilegesettings

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ktx.clipboardText
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.shared.AppSelectionCard
import io.nekohasekai.sfa.compose.shared.PackageCache
import io.nekohasekai.sfa.compose.shared.SortMode
import io.nekohasekai.sfa.compose.shared.buildDisplayPackages
import io.nekohasekai.sfa.utils.PrivilegeSettingsClient
import io.nekohasekai.sfa.vendor.PackageQueryManager
import io.nekohasekai.sfa.vendor.PrivilegedAccessRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


private data class LoadResult(
    val packages: List<PackageCache>,
    val selectedUids: Set<Int>,
)

private const val VPN_SERVICE_PERMISSION = "android.permission.BIND_VPN_SERVICE"

private val managementPermissions =
    setOf(
        "android.permission.CONTROL_VPN",
        "android.permission.CONTROL_ALWAYS_ON_VPN",
        "android.permission.MANAGE_VPN",
        "android.permission.NETWORK_SETTINGS",
        "android.permission.NETWORK_STACK",
        "android.permission.MAINLINE_NETWORK_STACK",
        "android.permission.CONNECTIVITY_INTERNAL",
        "android.permission.NETWORK_MANAGEMENT",
        "android.permission.TETHER_PRIVILEGED",
        "android.permission.MANAGE_NETWORK_POLICY",
    )

private enum class RiskCategory {
    NONE,
    VPN_APP,
    MANAGEMENT_APP,
    BOTH,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivilegeSettingsManageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

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
    var riskyWarningMessage by remember { mutableStateOf<String?>(null) }
    var syncErrorMessage by remember { mutableStateOf<String?>(null) }

    fun getRiskCategory(packageCache: PackageCache): RiskCategory {
        val permissions = packageCache.info.requestedPermissions ?: emptyArray()
        val hasManagement = permissions.any { it in managementPermissions }
        val isSelf = packageCache.packageName == context.packageName
        val hasVpnService =
            !isSelf && (
                permissions.any { it == VPN_SERVICE_PERMISSION } ||
                packageCache.info.services?.any { it.permission == VPN_SERVICE_PERMISSION } == true
            )
        return when {
            hasManagement && hasVpnService -> RiskCategory.BOTH
            hasManagement -> RiskCategory.MANAGEMENT_APP
            hasVpnService -> RiskCategory.VPN_APP
            else -> RiskCategory.NONE
        }
    }

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
            val failure =
                withContext(Dispatchers.IO) {
                    Settings.privilegeSettingsList = buildPackageList(newUids)
                    PrivilegeSettingsClient.sync()
                }
            if (failure != null) {
                syncErrorMessage = failure.message ?: failure.toString()
            }
        }
    }

    fun warnIfRiskySelected(newUids: Set<Int>) {
        val addedUids = newUids - selectedUids
        if (addedUids.isEmpty()) return
        val addedApps = packages.filter { it.uid in addedUids }
        val vpnUids =
            addedApps
                .filter { getRiskCategory(it) == RiskCategory.VPN_APP || getRiskCategory(it) == RiskCategory.BOTH }
                .map { it.uid }
                .toSet()
        val managementUids =
            addedApps
                .filter { getRiskCategory(it) == RiskCategory.MANAGEMENT_APP || getRiskCategory(it) == RiskCategory.BOTH }
                .map { it.uid }
                .toSet()
        val vpnApps = packages.filter { it.uid in vpnUids }.distinctBy { it.packageName }
        val managementApps = packages.filter { it.uid in managementUids }.distinctBy { it.packageName }
        if (vpnApps.isEmpty() && managementApps.isEmpty()) return

        val listSeparator = if (Locale.getDefault().language == "zh") "、" else ", "
        val messages = ArrayList<String>(2)
        if (vpnApps.isNotEmpty()) {
            val labelList = vpnApps.map { it.applicationLabel }.distinct().sorted()
            val labels = labelList.joinToString(listSeparator)
            messages +=
                if (labelList.size == 1) {
                    context.getString(
                        R.string.privilege_settings_risky_vpn_message_single,
                        labels,
                    )
                } else {
                    context.getString(
                        R.string.privilege_settings_risky_vpn_message_multi,
                        labels,
                    )
                }
        }
        if (managementApps.isNotEmpty()) {
            val labelList = managementApps.map { it.applicationLabel }.distinct().sorted()
            val labels = labelList.joinToString(listSeparator)
            messages +=
                if (labelList.size == 1) {
                    context.getString(
                        R.string.privilege_settings_risky_management_message_single,
                        labels,
                    )
                } else {
                    context.getString(
                        R.string.privilege_settings_risky_management_message_multi,
                        labels,
                    )
                }
        }
        riskyWarningMessage = messages.joinToString("\n")
    }

    fun postSaveSelectedApplications(newUids: Set<Int>, warnRisky: Boolean = true) {
        if (warnRisky) {
            warnIfRiskySelected(newUids)
        }
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
        postSaveSelectedApplications(newSelected)
    }

    LaunchedEffect(Unit) {
        isLoading = true
        val packageManagerFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_PERMISSIONS or PackageManager.GET_UNINSTALLED_PACKAGES
            }
        val loadResult =
            withContext(Dispatchers.IO) {
                try {
                    val installedPackages = PackageQueryManager.getInstalledPackages(packageManagerFlags, packageManagerFlags)
                    val packageManager = context.packageManager
                    val packageCaches =
                        installedPackages.mapNotNull { packageInfo ->
                            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                            PackageCache(packageInfo, appInfo, packageManager)
                        }
                    val selectedPackageNames = Settings.privilegeSettingsList.toMutableSet()
                    val selectedUidSet =
                        packageCaches.mapNotNull { packageCache ->
                            if (selectedPackageNames.contains(packageCache.packageName)) {
                                packageCache.uid
                            } else {
                                null
                            }
                        }.toSet()
                    LoadResult(packageCaches, selectedUidSet)
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
        packages = loadResult.packages
        selectedUids = loadResult.selectedUids
        applyFilter()
        updateCurrentPackages(searchQuery)
        isLoading = false
    }

    if (riskyWarningMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { riskyWarningMessage = null },
            title = { Text(stringResource(R.string.privilege_settings_risky_app_title)) },
            text = { Text(riskyWarningMessage ?: "") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { riskyWarningMessage = null },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
    if (syncErrorMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { syncErrorMessage = null },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(syncErrorMessage ?: "") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { syncErrorMessage = null },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.privilege_settings_hide_title)) },
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
                PrivilegeSettingsMenus(
                    sortMode = sortMode,
                    sortReverse = sortReverse,
                    hideSystemApps = hideSystemApps,
                    hideOfflineApps = hideOfflineApps,
                    hideDisabledApps = hideDisabledApps,
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
                text = stringResource(R.string.privilege_settings_hide_description),
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivilegeSettingsMenus(
    sortMode: SortMode,
    sortReverse: Boolean,
    hideSystemApps: Boolean,
    hideOfflineApps: Boolean,
    hideDisabledApps: Boolean,
    onSortModeChange: (SortMode) -> Unit,
    onSortReverseToggle: () -> Unit,
    onHideSystemAppsToggle: () -> Unit,
    onHideOfflineAppsToggle: () -> Unit,
    onHideDisabledAppsToggle: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var showMainMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSelectMenu by remember { mutableStateOf(false) }
    var showBackupMenu by remember { mutableStateOf(false) }

    IconButton(onClick = { showMainMenu = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
    }

    DropdownMenu(
        expanded = showMainMenu,
        onDismissRequest = {
            showMainMenu = false
            showSortMenu = false
            showFilterMenu = false
            showSelectMenu = false
            showBackupMenu = false
        },
    ) {
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
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.per_app_proxy_sort_mode_reverse)) },
                onClick = {
                    onSortReverseToggle()
                    showMainMenu = false
                    showSortMenu = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                },
            )
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
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                text = { Text(stringResource(R.string.action_deselect)) },
                onClick = {
                    onDeselectAll()
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        DropdownMenuItem(
            text = { Text(stringResource(R.string.per_app_proxy_backup)) },
            onClick = { showBackupMenu = !showBackupMenu },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                },
            )
        }
    }
}
