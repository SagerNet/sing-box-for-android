package io.nekohasekai.sfa.compose.screen.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AppShortcut
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.RootClient
import io.nekohasekai.sfa.compose.screen.profileoverride.PerAppProxyScanner
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.vendor.PackageQueryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileOverrideScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.profile_override)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
        )
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var autoRedirect by remember { mutableStateOf(Settings.autoRedirect) }
    var perAppProxyEnabled by remember { mutableStateOf(Settings.perAppProxyEnabled) }
    var managedModeEnabled by remember { mutableStateOf(Settings.perAppProxyManagedMode) }
    var isScanning by remember { mutableStateOf(false) }

    fun scanAndSaveManagedList() {
        isScanning = true
        scope.launch {
            val chinaApps = PerAppProxyScanner.scanAllChinaApps()
            withContext(Dispatchers.IO) {
                Settings.perAppProxyManagedList = chinaApps
            }
            isScanning = false
        }
    }

    var showShizukuDialog by remember { mutableStateOf(false) }
    var showRootDialog by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }

    val showModeSelector = PackageQueryManager.showModeSelector
    var packageQueryMode by remember { mutableStateOf(Settings.perAppProxyPackageQueryMode) }
    val useRootMode = packageQueryMode == Settings.PACKAGE_QUERY_MODE_ROOT

    val isShizukuInstalled by PackageQueryManager.shizukuInstalled.collectAsState()
    val isShizukuBinderReady by PackageQueryManager.shizukuBinderReady.collectAsState()
    val isShizukuPermissionGranted by PackageQueryManager.shizukuPermissionGranted.collectAsState()
    val isShizukuAvailable = isShizukuBinderReady && isShizukuPermissionGranted
    var isShizukuStateInitialized by remember(showModeSelector) { mutableStateOf(!showModeSelector) }

    DisposableEffect(showModeSelector) {
        if (showModeSelector) {
            isShizukuStateInitialized = false
            PackageQueryManager.registerListeners()
            PackageQueryManager.refreshShizukuState()
            isShizukuStateInitialized = true
        }
        onDispose {
            if (showModeSelector) {
                PackageQueryManager.unregisterListeners()
                isShizukuStateInitialized = false
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, showModeSelector) {
        if (!showModeSelector) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PackageQueryManager.refreshShizukuState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-disable per-app proxy if Shizuku authorization is revoked (only when using Shizuku mode)
    LaunchedEffect(isShizukuAvailable, useRootMode, isShizukuStateInitialized, perAppProxyEnabled, showModeSelector) {
        if (
            showModeSelector &&
            !useRootMode &&
            isShizukuStateInitialized &&
            perAppProxyEnabled &&
            !PackageQueryManager.isShizukuAvailable()
        ) {
            perAppProxyEnabled = false
            withContext(Dispatchers.IO) {
                Settings.perAppProxyEnabled = false
            }
        }
    }

    // Auto-close dialog and enable feature when Shizuku becomes available
    LaunchedEffect(isShizukuAvailable) {
        if (showModeSelector && isShizukuAvailable && showShizukuDialog) {
            showShizukuDialog = false
            perAppProxyEnabled = true
            withContext(Dispatchers.IO) {
                Settings.perAppProxyEnabled = true
            }
            if (managedModeEnabled) {
                scanAndSaveManagedList()
            }
        }
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        // Card 1: Auto Redirect
        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.auto_redirect),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.auto_redirect_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = autoRedirect,
                        onCheckedChange = { checked ->
                            if (checked && !autoRedirect) {
                                scope.launch {
                                    val hasRoot = RootClient.checkRootAvailable()
                                    if (hasRoot) {
                                        autoRedirect = true
                                        withContext(Dispatchers.IO) {
                                            Settings.autoRedirect = true
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.root_access_required),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            } else if (!checked) {
                                autoRedirect = false
                                scope.launch(Dispatchers.IO) {
                                    Settings.autoRedirect = false
                                }
                            }
                        },
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                colors =
                ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
            )
        }

        // Section: Per-App Proxy
        val canUsePerAppProxy = if (showModeSelector) {
            if (useRootMode) true else isShizukuAvailable
        } else {
            true
        }

        Text(
            text = stringResource(R.string.per_app_proxy),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
        )

        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column {
                // Mode selector (only when privileged query is needed)
                if (showModeSelector) {
                    val modeEnabled = !perAppProxyEnabled
                    val disabledAlpha = 0.38f
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.per_app_proxy_package_query_mode),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (modeEnabled) {
                                    Color.Unspecified
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                                },
                            )
                        },
                        supportingContent = {
                            Text(
                                if (useRootMode) "ROOT" else "Shizuku",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (modeEnabled) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
                                },
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = if (modeEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                                },
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = if (modeEnabled) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                                },
                            )
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .clickable(enabled = modeEnabled) { showModeDialog = true },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }

                // Enabled toggle
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.enabled),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = perAppProxyEnabled,
                            onCheckedChange = { checked ->
                                if (checked && showModeSelector) {
                                    if (useRootMode) {
                                        showRootDialog = true
                                    } else {
                                        showShizukuDialog = true
                                    }
                                } else {
                                    perAppProxyEnabled = checked
                                    scope.launch(Dispatchers.IO) {
                                        Settings.perAppProxyEnabled = checked
                                    }
                                    if (checked && managedModeEnabled) {
                                        scanAndSaveManagedList()
                                    }
                                }
                            },
                            enabled = !isScanning,
                        )
                    },
                    modifier =
                    Modifier.clip(
                        if (showModeSelector) {
                            RoundedCornerShape(0.dp)
                        } else if (perAppProxyEnabled && canUsePerAppProxy) {
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        } else {
                            RoundedCornerShape(12.dp)
                        },
                    ),
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )

                if (perAppProxyEnabled && canUsePerAppProxy) {
                    // Manage entry
                    val manageEnabled = !managedModeEnabled
                    val disabledAlpha = 0.38f
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.per_app_proxy_manage),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (manageEnabled) {
                                    Color.Unspecified
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                                },
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.AppShortcut,
                                contentDescription = null,
                                tint = if (manageEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                                },
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = if (manageEnabled) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                                },
                            )
                        },
                        modifier =
                        Modifier.clickable(enabled = manageEnabled) {
                            navController.navigate("settings/profile_override/manage")
                        },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )

                    // Managed Mode toggle
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.per_app_proxy_managed_mode),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.per_app_proxy_managed_mode_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Switch(
                                    checked = managedModeEnabled,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            managedModeEnabled = true
                                            scope.launch(Dispatchers.IO) {
                                                Settings.perAppProxyManagedMode = true
                                            }
                                            scanAndSaveManagedList()
                                        } else {
                                            managedModeEnabled = false
                                            scope.launch(Dispatchers.IO) {
                                                Settings.perAppProxyManagedMode = false
                                            }
                                        }
                                    },
                                )
                            }
                        },
                        modifier = Modifier.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }

        // Shizuku dialog
        if (showShizukuDialog) {
            AlertDialog(
                onDismissRequest = { showShizukuDialog = false },
                title = {
                    Text(stringResource(R.string.per_app_proxy))
                },
                text = {
                    Text(stringResource(R.string.per_app_proxy_shizuku_required))
                },
                confirmButton = {
                    when {
                        isShizukuAvailable -> {
                            TextButton(
                                onClick = {
                                    showShizukuDialog = false
                                    perAppProxyEnabled = true
                                    scope.launch(Dispatchers.IO) {
                                        Settings.perAppProxyEnabled = true
                                    }
                                    if (managedModeEnabled) {
                                        scanAndSaveManagedList()
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.ok))
                            }
                        }
                        isShizukuBinderReady -> {
                            TextButton(
                                onClick = {
                                    PackageQueryManager.requestShizukuPermission()
                                },
                            ) {
                                Text(stringResource(R.string.request_shizuku))
                            }
                        }
                        isShizukuInstalled -> {
                            TextButton(
                                onClick = {
                                    showShizukuDialog = false
                                    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.start_shizuku))
                            }
                        }
                        else -> {
                            TextButton(
                                onClick = {
                                    showShizukuDialog = false
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                                    context.startActivity(intent)
                                },
                            ) {
                                Text(stringResource(R.string.get_shizuku))
                            }
                        }
                    }
                },
                dismissButton = {
                    if (!isShizukuAvailable) {
                        TextButton(
                            onClick = { showShizukuDialog = false },
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                },
            )
        }

        // ROOT dialog
        if (showRootDialog) {
            AlertDialog(
                onDismissRequest = { showRootDialog = false },
                title = {
                    Text(stringResource(R.string.per_app_proxy))
                },
                text = {
                    Text(stringResource(R.string.per_app_proxy_root_required))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val hasRoot = PackageQueryManager.checkRootAvailable()
                                if (hasRoot) {
                                    showRootDialog = false
                                    perAppProxyEnabled = true
                                    withContext(Dispatchers.IO) {
                                        Settings.perAppProxyEnabled = true
                                    }
                                    if (managedModeEnabled) {
                                        scanAndSaveManagedList()
                                    }
                                } else {
                                    showRootDialog = false
                                    Toast.makeText(
                                        context,
                                        R.string.root_access_denied,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showRootDialog = false },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        // Mode selection dialog
        if (showModeDialog) {
            AlertDialog(
                onDismissRequest = { showModeDialog = false },
                title = {
                    Text(stringResource(R.string.per_app_proxy_package_query_mode))
                },
                text = {
                    Column {
                        ListItem(
                            headlineContent = { Text("Shizuku") },
                            leadingContent = {
                                RadioButton(
                                    selected = packageQueryMode == Settings.PACKAGE_QUERY_MODE_SHIZUKU,
                                    onClick = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                packageQueryMode = Settings.PACKAGE_QUERY_MODE_SHIZUKU
                                PackageQueryManager.setQueryMode(Settings.PACKAGE_QUERY_MODE_SHIZUKU)
                                scope.launch(Dispatchers.IO) {
                                    Settings.perAppProxyPackageQueryMode = Settings.PACKAGE_QUERY_MODE_SHIZUKU
                                }
                                if (
                                    perAppProxyEnabled &&
                                    isShizukuStateInitialized &&
                                    !PackageQueryManager.isShizukuAvailable()
                                ) {
                                    perAppProxyEnabled = false
                                    scope.launch(Dispatchers.IO) {
                                        Settings.perAppProxyEnabled = false
                                    }
                                }
                                showModeDialog = false
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                        ListItem(
                            headlineContent = { Text("ROOT") },
                            leadingContent = {
                                RadioButton(
                                    selected = packageQueryMode == Settings.PACKAGE_QUERY_MODE_ROOT,
                                    onClick = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                packageQueryMode = Settings.PACKAGE_QUERY_MODE_ROOT
                                PackageQueryManager.setQueryMode(Settings.PACKAGE_QUERY_MODE_ROOT)
                                scope.launch(Dispatchers.IO) {
                                    Settings.perAppProxyPackageQueryMode = Settings.PACKAGE_QUERY_MODE_ROOT
                                }
                                showModeDialog = false
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                },
                confirmButton = {},
            )
        }
    }
}
