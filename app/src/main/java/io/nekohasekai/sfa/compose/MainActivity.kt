package io.nekohasekai.sfa.compose

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.ServiceConnection
import io.nekohasekai.sfa.bg.ServiceNotification
import io.nekohasekai.sfa.compat.WindowSizeClassCompat
import io.nekohasekai.sfa.compat.isWidthAtLeastBreakpointCompat
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.SelectableMessageDialog
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.component.ServiceStatusBar
import io.nekohasekai.sfa.compose.component.UpdateAvailableDialog
import io.nekohasekai.sfa.compose.component.UptimeText
import io.nekohasekai.sfa.compose.model.Connection
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.navigation.ProfileRoutes
import io.nekohasekai.sfa.compose.navigation.SFANavHost
import io.nekohasekai.sfa.compose.navigation.Screen
import io.nekohasekai.sfa.compose.navigation.bottomNavigationScreens
import io.nekohasekai.sfa.compose.screen.connections.ConnectionDetailsScreen
import io.nekohasekai.sfa.compose.screen.connections.ConnectionsPage
import io.nekohasekai.sfa.compose.screen.connections.ConnectionsViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.GroupsCard
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.screen.log.LogViewModel
import io.nekohasekai.sfa.compose.theme.SFATheme
import io.nekohasekai.sfa.compose.topbar.LocalTopBarController
import io.nekohasekai.sfa.compose.topbar.TopBarController
import io.nekohasekai.sfa.compose.topbar.TopBarEntry
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ktx.hasPermission
import io.nekohasekai.sfa.ktx.launchCustomTab
import io.nekohasekai.sfa.update.UpdateState
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity :
    ComponentActivity(),
    ServiceConnection.Callback {
    private val connection = ServiceConnection(this, this)
    private lateinit var dashboardViewModel: DashboardViewModel
    private var currentServiceStatus by mutableStateOf(Status.Stopped)
    private var currentAlert by mutableStateOf<Pair<Alert, String?>?>(null)
    private var showLocationPermissionDialog by mutableStateOf(false)
    private var showBackgroundLocationDialog by mutableStateOf(false)
    private var showImportProfileDialog by mutableStateOf(false)
    private var pendingImportProfile by mutableStateOf<Triple<String, String, String>?>(null)
    private var newProfileArgs by mutableStateOf(NewProfileArgs())

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (Settings.dynamicNotification && !isGranted) {
                onServiceAlert(Alert.RequestNotificationPermission, null)
            } else {
                startService0()
            }
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestBackgroundLocationPermission()
                } else {
                    startService()
                }
            }
        }

    private val backgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                startService()
            }
        }

    private val prepareLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                startService0()
            } else {
                onServiceAlert(Alert.RequestVPNPermission, null)
            }
        }
    private val pendingNavigationRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        connection.reconnect()

        UpdateState.loadFromCache()
        if (Settings.checkUpdateEnabled) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val updateInfo = Vendor.checkUpdateAsync()
                    UpdateState.setUpdate(updateInfo)
                } catch (_: Exception) {
                }
            }
        }

        handleIntent(intent)

        setContent {
            SFATheme {
                SFAApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        if (intent.categories?.contains("de.robv.android.xposed.category.MODULE_SETTINGS") == true) {
            pendingNavigationRoute.value = "settings/privilege"
        }
        val uri = intent.data ?: return
        if (uri.scheme == "sing-box" && uri.host == "import-remote-profile") {
            try {
                val profile = Libbox.parseRemoteProfileImportLink(uri.toString())
                pendingImportProfile = Triple(profile.name, profile.host, profile.url)
                showImportProfileDialog = true
            } catch (e: Exception) {
                lifecycleScope.launch {
                    GlobalEventBus.emit(UiEvent.ErrorMessage(e.message ?: "Failed to parse profile link"))
                }
            }
        }
    }

    @SuppressLint("NewApi")
    fun startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ServiceNotification.checkPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startService0()
    }

    private fun startService0() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (Settings.rebuildServiceMode()) {
                connection.reconnect()
            }
            if (Settings.serviceMode == ServiceMode.VPN) {
                if (prepare()) {
                    return@launch
                }
            }
            val intent = Intent(Application.application, Settings.serviceClass())
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
            Settings.startedByUser = true
        }
    }

    private suspend fun prepare() = withContext(Dispatchers.Main) {
        try {
            val intent = VpnService.prepare(this@MainActivity)
            if (intent != null) {
                prepareLauncher.launch(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            onServiceAlert(Alert.RequestVPNPermission, e.message)
            true
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SFAApp() {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val currentRoute = currentDestination?.route
        val scope = rememberCoroutineScope()

        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val useNavigationRail =
            windowSizeClass.isWidthAtLeastBreakpointCompat(WindowSizeClassCompat.WIDTH_DP_MEDIUM_LOWER_BOUND)

        // Snackbar state
        val snackbarHostState = remember { SnackbarHostState() }

        // Groups Sheet state
        var showGroupsSheet by remember { mutableStateOf(false) }

        // Connections Sheet state
        var showConnectionsSheet by remember { mutableStateOf(false) }

        // Error dialog state for UiEvent.ShowError
        var showErrorDialog by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        val topBarState = remember { mutableStateOf(emptyList<TopBarEntry>()) }
        val topBarController = remember { TopBarController(topBarState) }
        val topBarOverride = topBarState.value.lastOrNull()?.content
        val openNewProfile: (NewProfileArgs) -> Unit = { args ->
            newProfileArgs = args
            navController.navigate(ProfileRoutes.NewProfile) {
                launchSingleTop = true
            }
        }

        // Handle service alerts
        currentAlert?.let { (alertType, message) ->
            ServiceAlertDialog(
                alertType = alertType,
                message = message,
                onDismiss = { currentAlert = null },
            )
        }

        // Handle UiEvent.ShowError dialog
        if (showErrorDialog) {
            SelectableMessageDialog(
                title = stringResource(R.string.error_title),
                message = errorMessage,
                onDismiss = { showErrorDialog = false },
            )
        }

        // Handle location permission dialogs
        if (showLocationPermissionDialog) {
            LocationPermissionDialog(onConfirm = {
                showLocationPermissionDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }, onDismiss = { showLocationPermissionDialog = false })
        }

        if (showBackgroundLocationDialog) {
            BackgroundLocationPermissionDialog(onConfirm = {
                showBackgroundLocationDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }, onDismiss = { showBackgroundLocationDialog = false })
        }

        // Handle import remote profile dialog
        if (showImportProfileDialog && pendingImportProfile != null) {
            val (name, host, url) = pendingImportProfile!!
            AlertDialog(
                onDismissRequest = {
                    showImportProfileDialog = false
                    pendingImportProfile = null
                },
                title = { Text(stringResource(R.string.import_remote_profile)) },
                text = { Text(stringResource(R.string.import_remote_profile_message, name, host)) },
                confirmButton = {
                    TextButton(onClick = {
                        openNewProfile(
                            NewProfileArgs(
                                importName = name,
                                importUrl = url,
                            ),
                        )
                        showImportProfileDialog = false
                        pendingImportProfile = null
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showImportProfileDialog = false
                        pendingImportProfile = null
                    }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        // Handle update check prompt dialog (shown only once on first launch)
        var showUpdateCheckPrompt by remember { mutableStateOf(!Settings.updateCheckPrompted) }
        if (showUpdateCheckPrompt) {
            AlertDialog(
                onDismissRequest = {
                    Settings.updateCheckPrompted = true
                    showUpdateCheckPrompt = false
                },
                title = { Text(stringResource(R.string.check_update)) },
                text = {
                    MarkdownText(
                        markdown = stringResource(
                            if (BuildConfig.FLAVOR == "play") {
                                R.string.check_update_prompt_play
                            } else {
                                R.string.check_update_prompt_github
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        Settings.updateCheckPrompted = true
                        Settings.checkUpdateEnabled = true
                        showUpdateCheckPrompt = false
                        scope.launch(Dispatchers.IO) {
                            try {
                                val result = Vendor.checkUpdateAsync()
                                UpdateState.setUpdate(result)
                            } catch (_: Exception) {
                            }
                        }
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        Settings.updateCheckPrompted = true
                        showUpdateCheckPrompt = false
                    }) {
                        Text(stringResource(R.string.no_thanks))
                    }
                },
            )
        }

        // Handle update available dialog
        val updateInfo by UpdateState.updateInfo
        val shouldShowUpdateDialog = updateInfo != null &&
            updateInfo!!.versionCode > Settings.lastShownUpdateVersion
        var showUpdateDialog by remember { mutableStateOf(true) }

        // Download dialog state
        var showDownloadDialog by remember { mutableStateOf(false) }
        var downloadJob by remember { mutableStateOf<Job?>(null) }
        var downloadError by remember { mutableStateOf<String?>(null) }

        if (showUpdateDialog && shouldShowUpdateDialog) {
            UpdateAvailableDialog(
                updateInfo = updateInfo!!,
                onDismiss = {
                    Settings.lastShownUpdateVersion = updateInfo!!.versionCode
                    showUpdateDialog = false
                },
                onUpdate = {
                    showDownloadDialog = true
                    downloadError = null
                    downloadJob = scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                Vendor.downloadAndInstall(
                                    this@MainActivity,
                                    updateInfo!!.downloadUrl,
                                )
                            }
                            showDownloadDialog = false
                        } catch (e: Exception) {
                            downloadError = e.message
                        }
                    }
                },
            )
        }

        // Download progress dialog
        if (showDownloadDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update)) },
                text = {
                    Column {
                        if (downloadError != null) {
                            Text(
                                downloadError!!,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(stringResource(R.string.downloading))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            downloadJob?.cancel()
                            downloadJob = null
                            showDownloadDialog = false
                            downloadError = null
                        },
                    ) {
                        Text(stringResource(if (downloadError != null) R.string.ok else android.R.string.cancel))
                    }
                },
            )
        }

        // Initialize the dashboard view model and store reference
        val dashboardViewModel: DashboardViewModel = viewModel()
        if (!::dashboardViewModel.isInitialized) {
            this.dashboardViewModel = dashboardViewModel
        }
        val dashboardUiState by dashboardViewModel.uiState.collectAsState()

        val isSettingsSubScreen = currentRoute?.startsWith("settings/") == true
        val isConnectionsDetail = currentRoute?.startsWith("connections/detail") == true
        val isProfileRoute = currentRoute?.startsWith("profile/") == true
        val currentRootRoute =
            when {
                isSettingsSubScreen -> Screen.Settings.route
                currentRoute?.startsWith(Screen.Connections.route) == true -> Screen.Connections.route
                currentRoute?.startsWith(Screen.Log.route) == true -> Screen.Log.route
                isProfileRoute -> Screen.Dashboard.route
                else -> currentRoute
            }
        val isConnectionsRoute = currentRootRoute == Screen.Connections.route
        val isGroupsRoute = currentRootRoute == Screen.Groups.route
        val isLogRoute = currentRootRoute == Screen.Log.route

        val isSubScreen = isSettingsSubScreen || isConnectionsDetail || isProfileRoute
        // Get LogViewModel instance if we're on the Log screen
        val logViewModel: LogViewModel? =
            if (isLogRoute) {
                viewModel()
            } else {
                null
            }

        val groupsViewModel: GroupsViewModel? =
            if (isGroupsRoute) {
                viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return GroupsViewModel(dashboardViewModel.commandClient) as T
                        }
                    },
                )
            } else {
                null
            }

        val connectionsViewModel: ConnectionsViewModel? =
            if (isConnectionsRoute) {
                viewModel()
            } else {
                null
            }

        val showGroupsInNav = dashboardUiState.hasGroups
        val showConnectionsInNav =
            currentServiceStatus == Status.Started || currentServiceStatus == Status.Starting

        val railScreens =
            buildList {
                add(Screen.Dashboard)
                if (showGroupsInNav) {
                    add(Screen.Groups)
                }
                if (showConnectionsInNav) {
                    add(Screen.Connections)
                }
                add(Screen.Log)
                add(Screen.Settings)
            }

        val allowedRoutes =
            buildSet {
                add(Screen.Dashboard.route)
                add(Screen.Log.route)
                add(Screen.Settings.route)
                if (useNavigationRail && showGroupsInNav) {
                    add(Screen.Groups.route)
                }
                if (useNavigationRail && showConnectionsInNav) {
                    add(Screen.Connections.route)
                }
            }

        val pendingRoute = pendingNavigationRoute.value
        LaunchedEffect(pendingRoute) {
            if (pendingRoute != null) {
                navController.navigate(pendingRoute) {
                    launchSingleTop = true
                }
                pendingNavigationRoute.value = null
            }
        }

        LaunchedEffect(allowedRoutes, currentRootRoute, useNavigationRail) {
            if (currentRootRoute != null && !allowedRoutes.contains(currentRootRoute)) {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }

        // Collect all UI events from GlobalEventBus
        LaunchedEffect(Unit) {
            GlobalEventBus.events.collect { event ->
                when (event) {
                    is UiEvent.ErrorMessage -> {
                        errorMessage = event.message
                        showErrorDialog = true
                    }

                    is UiEvent.OpenUrl -> {
                        this@MainActivity.launchCustomTab(event.url)
                    }

                    is UiEvent.RequestStartService -> {
                        startService()
                    }

                    is UiEvent.RequestReconnectService -> {
                        connection.reconnect()
                    }

                    is UiEvent.EditProfile -> {
                        navController.navigate(ProfileRoutes.editProfile(event.profileId)) {
                            launchSingleTop = true
                        }
                    }

                    is UiEvent.RestartToTakeEffect -> {
                        if (currentServiceStatus == Status.Started) {
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result =
                                    snackbarHostState.showSnackbar(
                                        message = "Restart to take effect",
                                        actionLabel = "Restart",
                                        duration = androidx.compose.material3.SnackbarDuration.Short,
                                    )
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    withContext(Dispatchers.IO) {
                                        Libbox.newStandaloneCommandClient().serviceReload()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val topBarContent: @Composable () -> Unit = {
            topBarOverride?.invoke()
        }

        val scaffoldContent: @Composable (PaddingValues) -> Unit = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // Service Status Bar (shown when service is running or stopping)
                val serviceRunning =
                    currentServiceStatus == Status.Started || currentServiceStatus == Status.Starting
                val showStatusBar = serviceRunning || currentServiceStatus == Status.Stopping
                val showStartFab = !serviceRunning && dashboardUiState.selectedProfileId != -1L

                SFANavHost(
                    navController = navController,
                    serviceStatus = currentServiceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    newProfileArgs = newProfileArgs,
                    onClearNewProfileArgs = { newProfileArgs = NewProfileArgs() },
                    onOpenNewProfile = openNewProfile,
                    dashboardViewModel = dashboardViewModel,
                    logViewModel = logViewModel,
                    groupsViewModel = groupsViewModel,
                    connectionsViewModel = connectionsViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!useNavigationRail) {
                    ServiceStatusBar(
                        visible = showStatusBar && !isSubScreen,
                        serviceStatus = currentServiceStatus,
                        startTime = dashboardUiState.serviceStartTime,
                        groupsCount = dashboardUiState.groupsCount,
                        hasGroups = dashboardUiState.hasGroups,
                        onGroupsClick = { showGroupsSheet = true },
                        connectionsCount = dashboardUiState.connectionsCount,
                        onConnectionsClick = { showConnectionsSheet = true },
                        onStopClick = { dashboardViewModel.toggleService() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                val showPadFab = useNavigationRail && !isSubScreen && (showStartFab || showStatusBar)
                if (useNavigationRail) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showPadFab,
                        enter = scaleIn(),
                        exit = scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp),
                    ) {
                        val isRunning =
                            currentServiceStatus == Status.Started || currentServiceStatus == Status.Starting
                        val isStopping = currentServiceStatus == Status.Stopping
                        if (currentServiceStatus == Status.Stopped) {
                            FloatingActionButton(
                                onClick = { startService() },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.action_start),
                                )
                            }
                        } else {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    if (isRunning || isStopping) {
                                        dashboardViewModel.toggleService()
                                    } else {
                                        startService()
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector =
                                        if (isRunning || isStopping) {
                                            Icons.Default.Stop
                                        } else {
                                            Icons.Default.PlayArrow
                                        },
                                        contentDescription =
                                        if (isRunning || isStopping) {
                                            stringResource(R.string.stop)
                                        } else {
                                            stringResource(R.string.action_start)
                                        },
                                    )
                                },
                                text = {
                                    when {
                                        isRunning && dashboardUiState.serviceStartTime != null -> {
                                            UptimeText(startTime = dashboardUiState.serviceStartTime!!)
                                        }
                                        currentServiceStatus == Status.Started -> {
                                            Text(
                                                text = stringResource(R.string.status_started),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                        currentServiceStatus == Status.Starting -> {
                                            Text(
                                                text = stringResource(R.string.status_starting),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                        currentServiceStatus == Status.Stopping -> {
                                            Text(
                                                text = stringResource(R.string.status_stopping),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                        else -> {
                                            Text(
                                                text = stringResource(R.string.action_start),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.height(64.dp),
                            )
                        }
                    }
                } else {
                    // Start FAB (shown when service is stopped and a profile is selected)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = currentServiceStatus == Status.Stopped &&
                            dashboardUiState.selectedProfileId != -1L &&
                            !isSubScreen,
                        enter = scaleIn(),
                        exit = scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    ) {
                        FloatingActionButton(
                            onClick = { startService() },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.action_start),
                            )
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalTopBarController provides topBarController) {
            if (useNavigationRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Surface(tonalElevation = 1.dp) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            val hasUpdate by UpdateState.hasUpdate
                            railScreens.forEach { screen ->
                                val selected = currentRootRoute == screen.route

                                NavigationRailItem(
                                    icon = {
                                        if (screen == Screen.Settings && hasUpdate) {
                                            BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary) }) {
                                                Icon(screen.icon, contentDescription = null)
                                            }
                                        } else {
                                            Icon(screen.icon, contentDescription = null)
                                        }
                                    },
                                    label = { Text(stringResource(screen.titleRes)) },
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        topBar = topBarContent,
                    ) { paddingValues ->
                        scaffoldContent(paddingValues)
                    }
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = topBarContent,
                    bottomBar = {
                        if (!isSubScreen) {
                            val hasUpdate by UpdateState.hasUpdate
                            NavigationBar {
                                bottomNavigationScreens.forEach { screen ->
                                    NavigationBarItem(
                                        icon = {
                                            if (screen == Screen.Settings && hasUpdate) {
                                                BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary) }) {
                                                    Icon(screen.icon, contentDescription = null)
                                                }
                                            } else {
                                                Icon(screen.icon, contentDescription = null)
                                            }
                                        },
                                        label = { Text(stringResource(screen.titleRes)) },
                                        selected =
                                        currentDestination?.hierarchy?.any {
                                            it.route == screen.route
                                        } == true,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                // Pop up to the start destination of the graph to
                                                // avoid building up a large stack of destinations
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                // Avoid multiple copies of the same destination
                                                launchSingleTop = true
                                                // Restore state when reselecting a previously selected item
                                                restoreState = true
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    },
                ) { paddingValues ->
                    scaffoldContent(paddingValues)
                }
            }
        }

        // Groups ModalBottomSheet
        if (showGroupsSheet && !useNavigationRail) {
            val groupsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val groupsViewModel: GroupsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return GroupsViewModel(dashboardViewModel.commandClient) as T
                    }
                },
            )
            val groupsUiState by groupsViewModel.uiState.collectAsState()
            val allCollapsed = groupsUiState.expandedGroups.isEmpty()

            ModalBottomSheet(
                onDismissRequest = { showGroupsSheet = false },
                sheetState = groupsSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f),
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.title_groups),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (groupsUiState.groups.isNotEmpty()) {
                            IconButton(onClick = { groupsViewModel.toggleAllGroups() }) {
                                Icon(
                                    imageVector = if (allCollapsed) {
                                        Icons.Default.UnfoldMore
                                    } else {
                                        Icons.Default.UnfoldLess
                                    },
                                    contentDescription = if (allCollapsed) {
                                        stringResource(R.string.expand_all)
                                    } else {
                                        stringResource(R.string.collapse_all)
                                    },
                                )
                            }
                        }
                    }

                    // Groups content
                    GroupsCard(
                        serviceStatus = currentServiceStatus,
                        commandClient = dashboardViewModel.commandClient,
                        viewModel = groupsViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Connections ModalBottomSheet
        if (showConnectionsSheet && !useNavigationRail) {
            val connectionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val connectionsViewModel: ConnectionsViewModel = viewModel()
            val connectionsUiState by connectionsViewModel.uiState.collectAsState()
            var selectedConnectionId by remember { mutableStateOf<String?>(null) }
            val selectedConnection = connectionsUiState.allConnections.find { it.id == selectedConnectionId }
            var cachedConnection by remember { mutableStateOf<Connection?>(null) }
            if (selectedConnection != null) {
                cachedConnection = selectedConnection
            } else if (selectedConnectionId != null && cachedConnection?.isActive == true) {
                cachedConnection = cachedConnection?.copy(closedAt = System.currentTimeMillis())
            }
            val displayConnection = if (selectedConnectionId != null) cachedConnection else null

            LaunchedEffect(Unit) {
                connectionsViewModel.setVisible(true)
            }

            DisposableEffect(Unit) {
                onDispose {
                    connectionsViewModel.setVisible(false)
                }
            }

            ModalBottomSheet(
                onDismissRequest = {
                    showConnectionsSheet = false
                    selectedConnectionId = null
                },
                sheetState = connectionsSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f),
                ) {
                    if (displayConnection != null) {
                        ConnectionDetailsScreen(
                            connection = displayConnection,
                            onBack = { selectedConnectionId = null },
                            onClose = {
                                selectedConnectionId?.let { connectionsViewModel.closeConnection(it) }
                            },
                        )
                    } else {
                        ConnectionsPage(
                            serviceStatus = currentServiceStatus,
                            viewModel = connectionsViewModel,
                            showTitle = true,
                            onConnectionClick = { selectedConnectionId = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    override fun onServiceStatusChanged(status: Status) {
        currentServiceStatus = status
        // Update service status in ViewModels
        if (::dashboardViewModel.isInitialized) {
            dashboardViewModel.updateServiceStatus(status)
        }
    }

    fun reconnect() {
        connection.reconnect()
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        when (type) {
            Alert.RequestLocationPermission -> {
                return requestLocationPermission()
            }

            else -> {
                currentAlert = Pair(type, message)
            }
        }
    }

    private fun requestLocationPermission() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestFineLocationPermission()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationPermission()
        }
    }

    private fun requestFineLocationPermission() {
        // Show location permission dialog in Compose UI
        showLocationPermissionDialog = true
    }

    private fun requestBackgroundLocationPermission() {
        // Show background location permission dialog in Compose UI
        showBackgroundLocationDialog = true
    }

    override fun onDestroy() {
        connection.disconnect()
        super.onDestroy()
    }

    @Composable
    private fun ServiceAlertDialog(alertType: Alert, message: String?, onDismiss: () -> Unit) {
        val title =
            when (alertType) {
                Alert.RequestNotificationPermission -> stringResource(R.string.notification_permission_title)
                Alert.StartCommandServer -> stringResource(R.string.error_start_command_server)
                Alert.CreateService -> stringResource(R.string.error_create_service)
                Alert.StartService -> stringResource(R.string.error_start_service)
                else -> null
            }

        val dialogMessage =
            when (alertType) {
                Alert.RequestVPNPermission -> stringResource(R.string.error_missing_vpn_permission)
                Alert.RequestNotificationPermission -> stringResource(R.string.notification_permission_required_description)
                Alert.EmptyConfiguration -> stringResource(R.string.error_empty_configuration)
                else -> message
            }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = title?.let { { Text(text = it) } },
            text = dialogMessage?.let { { Text(text = it) } },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    @Composable
    private fun LocationPermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.location_permission_title)) },
            text = { Text(stringResource(R.string.location_permission_description)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.no_thanks))
                }
            },
        )
    }

    @Composable
    private fun BackgroundLocationPermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.location_permission_title)) },
            text = { Text(stringResource(R.string.location_permission_background_description)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.no_thanks))
                }
            },
        )
    }
}
