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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.compose.material3.Badge
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.ServiceConnection
import io.nekohasekai.sfa.bg.ServiceNotification
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.component.ServiceStatusBar
import io.nekohasekai.sfa.compose.component.UpdateAvailableDialog
import io.nekohasekai.sfa.compose.navigation.SFANavHost
import io.nekohasekai.sfa.compose.navigation.Screen
import io.nekohasekai.sfa.compose.navigation.bottomNavigationScreens
import io.nekohasekai.sfa.compose.screen.dashboard.CardGroup
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.GroupsCard
import io.nekohasekai.sfa.compose.screen.connections.ConnectionDetailsScreen
import io.nekohasekai.sfa.compose.screen.connections.ConnectionsScreen
import io.nekohasekai.sfa.compose.screen.connections.ConnectionsViewModel
import io.nekohasekai.sfa.compose.model.Connection
import io.nekohasekai.sfa.compose.model.ConnectionSort
import io.nekohasekai.sfa.compose.model.ConnectionStateFilter
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.screen.log.LogViewModel
import io.nekohasekai.sfa.compose.theme.SFATheme
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ktx.hasPermission
import io.nekohasekai.sfa.ktx.launchCustomTab
import io.nekohasekai.sfa.update.UpdateState
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), ServiceConnection.Callback {
    private val connection = ServiceConnection(this, this)
    private lateinit var dashboardViewModel: DashboardViewModel
    private var currentServiceStatus by mutableStateOf(Status.Stopped)
    private var currentAlert by mutableStateOf<Pair<Alert, String?>?>(null)
    private var showLocationPermissionDialog by mutableStateOf(false)
    private var showBackgroundLocationDialog by mutableStateOf(false)
    private var showImportProfileDialog by mutableStateOf(false)
    private var pendingImportProfile by mutableStateOf<Triple<String, String, String>?>(null)

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
        val uri = intent?.data ?: return
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

    private suspend fun prepare() =
        withContext(Dispatchers.Main) {
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
        val scope = rememberCoroutineScope()

        // Snackbar state
        val snackbarHostState = remember { SnackbarHostState() }

        // Groups Sheet state
        var showGroupsSheet by remember { mutableStateOf(false) }

        // Connections Sheet state
        var showConnectionsSheet by remember { mutableStateOf(false) }

        // Error dialog state for UiEvent.ShowError
        var showErrorDialog by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }

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
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = { Text(stringResource(R.string.error_title)) },
                text = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = { showErrorDialog = false }) {
                        Text(stringResource(R.string.ok))
                    }
                },
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
                        startActivity(
                            Intent(this@MainActivity, NewProfileActivity::class.java).apply {
                                putExtra(NewProfileActivity.EXTRA_IMPORT_NAME, name)
                                putExtra(NewProfileActivity.EXTRA_IMPORT_URL, url)
                            },
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
                            if (BuildConfig.FLAVOR == "play") R.string.check_update_prompt_play
                            else R.string.check_update_prompt_github
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
                            val result = withContext(Dispatchers.IO) {
                                Vendor.downloadAndInstall(
                                    this@MainActivity,
                                    updateInfo!!.downloadUrl,
                                )
                            }
                            if (result.isFailure) {
                                downloadError = result.exceptionOrNull()?.message
                            } else {
                                showDownloadDialog = false
                            }
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

        // Determine current screen title
        val currentScreen =
            bottomNavigationScreens.find { screen ->
                currentDestination?.route == screen.route
            } ?: bottomNavigationScreens[0]

        // Check if we're in a settings sub-screen
        val isSettingsSubScreen = currentDestination?.route?.startsWith("settings/") == true
        val settingsScreenTitle =
            when (currentDestination?.route) {
                "settings/app" -> stringResource(R.string.title_app_settings)
                "settings/core" -> stringResource(R.string.core)
                "settings/service" -> stringResource(R.string.service)
                "settings/profile_override" -> stringResource(R.string.profile_override)
                else -> null
            }

        // Get LogViewModel instance if we're on the Log screen
        val logViewModel: LogViewModel? =
            if (currentScreen == Screen.Log) {
                viewModel()
            } else {
                null
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
                        val intent =
                            Intent(this@MainActivity, EditProfileActivity::class.java)
                        intent.putExtra("profile_id", event.profileId)
                        startActivity(intent)
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

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (isSettingsSubScreen && settingsScreenTitle != null) {
                                settingsScreenTitle
                            } else {
                                stringResource(currentScreen.titleRes)
                            },
                        )
                    },
                    navigationIcon = {
                        if (isSettingsSubScreen) {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = stringResource(R.string.content_description_back),
                                )
                            }
                        }
                    },
                    actions = {
                        // Show Others menu for Dashboard screen (but not in settings sub-screens)
                        if (currentScreen == Screen.Dashboard && !isSettingsSubScreen) {
                            // More options button
                            IconButton(onClick = { dashboardViewModel.toggleCardSettingsDialog() }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.title_others),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        if (currentScreen == Screen.Log && logViewModel != null) {
                            val logUiState by logViewModel.uiState.collectAsState()

                            if (!logUiState.isSelectionMode) {
                                IconButton(onClick = { logViewModel.togglePause() }) {
                                    Icon(
                                        imageVector =
                                            if (logUiState.isPaused) {
                                                Icons.Default.PlayArrow
                                            } else {
                                                Icons.Default.Pause
                                            },
                                        contentDescription =
                                            if (logUiState.isPaused) {
                                                stringResource(
                                                    R.string.content_description_resume_logs,
                                                )
                                            } else {
                                                stringResource(R.string.content_description_pause_logs)
                                            },
                                    )
                                }

                                IconButton(onClick = { logViewModel.toggleSearch() }) {
                                    Icon(
                                        imageVector =
                                            if (logUiState.isSearchActive) {
                                                Icons.Default.ExpandLess
                                            } else {
                                                Icons.Default.Search
                                            },
                                        contentDescription =
                                            if (logUiState.isSearchActive) {
                                                stringResource(
                                                    R.string.content_description_collapse_search,
                                                )
                                            } else {
                                                stringResource(R.string.content_description_search_logs)
                                            },
                                        tint =
                                            if (logUiState.isSearchActive) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                    )
                                }

                                IconButton(onClick = { logViewModel.toggleOptionsMenu() }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.more_options),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(),
                )
            },
            bottomBar = {
                // Only show bottom bar when not in settings sub-screens
                if (!isSettingsSubScreen) {
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
                    dashboardViewModel = dashboardViewModel,
                    logViewModel = logViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
                ServiceStatusBar(
                    visible = showStatusBar && !isSettingsSubScreen,
                    serviceStatus = currentServiceStatus,
                    startTime = dashboardUiState.serviceStartTime,
                    groupsCount = dashboardUiState.groupsCount,
                    hasGroups = dashboardUiState.hasGroups,
                    onGroupsClick = { showGroupsSheet = true },
                    connectionsCount = dashboardUiState.connectionsOut.toIntOrNull() ?: 0,
                    onConnectionsClick = { showConnectionsSheet = true },
                    onStopClick = { dashboardViewModel.toggleService() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                // Start FAB (shown when service is stopped and a profile is selected)
                AnimatedVisibility(
                    visible = currentServiceStatus == Status.Stopped && dashboardUiState.selectedProfileId != -1L && !isSettingsSubScreen,
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

        // Groups ModalBottomSheet
        if (showGroupsSheet) {
            val groupsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val groupsViewModel: GroupsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return GroupsViewModel(dashboardViewModel.commandClient) as T
                    }
                }
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
                                    imageVector = if (allCollapsed) Icons.Default.UnfoldMore
                                                  else Icons.Default.UnfoldLess,
                                    contentDescription = if (allCollapsed)
                                        stringResource(R.string.expand_all)
                                    else
                                        stringResource(R.string.collapse_all),
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
        if (showConnectionsSheet) {
            val connectionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val connectionsViewModel: ConnectionsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return ConnectionsViewModel(dashboardViewModel.commandClient) as T
                    }
                }
            )
            val connectionsUiState by connectionsViewModel.uiState.collectAsState()
            var selectedConnectionId by remember { mutableStateOf<String?>(null) }
            val selectedConnection = connectionsUiState.allConnections.find { it.id == selectedConnectionId }
            var showConnectionsMenu by remember { mutableStateOf(false) }

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
                    if (selectedConnection != null) {
                        ConnectionDetailsScreen(
                            connection = selectedConnection,
                            onBack = { selectedConnectionId = null },
                            onClose = {
                                selectedConnectionId?.let { connectionsViewModel.closeConnection(it) }
                                selectedConnectionId = null
                            },
                        )
                    } else {
                        var showStateMenu by remember { mutableStateOf(false) }
                        var showSortMenu by remember { mutableStateOf(false) }

                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.title_connections),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // State Filter
                            Box {
                                FilterChip(
                                    selected = connectionsUiState.stateFilter != ConnectionStateFilter.Active,
                                    onClick = { showStateMenu = true },
                                    label = {
                                        Text(
                                            when (connectionsUiState.stateFilter) {
                                                ConnectionStateFilter.All -> stringResource(R.string.connection_state_all)
                                                ConnectionStateFilter.Active -> stringResource(R.string.connection_state_active)
                                                ConnectionStateFilter.Closed -> stringResource(R.string.connection_state_closed)
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.FilterList, contentDescription = null)
                                    },
                                )

                                DropdownMenu(
                                    expanded = showStateMenu,
                                    onDismissRequest = { showStateMenu = false },
                                ) {
                                    ConnectionStateFilter.entries.forEach { filter ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when (filter) {
                                                        ConnectionStateFilter.All -> stringResource(R.string.connection_state_all)
                                                        ConnectionStateFilter.Active -> stringResource(R.string.connection_state_active)
                                                        ConnectionStateFilter.Closed -> stringResource(R.string.connection_state_closed)
                                                    }
                                                )
                                            },
                                            onClick = {
                                                connectionsViewModel.setStateFilter(filter)
                                                showStateMenu = false
                                            },
                                            leadingIcon = {
                                                if (connectionsUiState.stateFilter == filter) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            // Sort
                            Box {
                                FilterChip(
                                    selected = connectionsUiState.sort != ConnectionSort.ByDate,
                                    onClick = { showSortMenu = true },
                                    label = {
                                        Text(
                                            when (connectionsUiState.sort) {
                                                ConnectionSort.ByDate -> stringResource(R.string.connection_sort_date)
                                                ConnectionSort.ByTraffic -> stringResource(R.string.connection_sort_traffic)
                                                ConnectionSort.ByTrafficTotal -> stringResource(R.string.connection_sort_traffic_total)
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.SwapVert, contentDescription = null)
                                    },
                                )

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                ) {
                                    ConnectionSort.entries.forEach { sort ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when (sort) {
                                                        ConnectionSort.ByDate -> stringResource(R.string.connection_sort_date)
                                                        ConnectionSort.ByTraffic -> stringResource(R.string.connection_sort_traffic)
                                                        ConnectionSort.ByTrafficTotal -> stringResource(R.string.connection_sort_traffic_total)
                                                    }
                                                )
                                            },
                                            onClick = {
                                                connectionsViewModel.setSort(sort)
                                                showSortMenu = false
                                            },
                                            leadingIcon = {
                                                if (connectionsUiState.sort == sort) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            // Menu
                            Box {
                                IconButton(onClick = { showConnectionsMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = null)
                                }

                                DropdownMenu(
                                    expanded = showConnectionsMenu,
                                    onDismissRequest = { showConnectionsMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (connectionsUiState.isSearchActive) {
                                                    stringResource(R.string.close_search)
                                                } else {
                                                    stringResource(R.string.search)
                                                }
                                            )
                                        },
                                        onClick = {
                                            connectionsViewModel.toggleSearch()
                                            showConnectionsMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (connectionsUiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                                contentDescription = null,
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.connection_close_all)) },
                                        onClick = {
                                            connectionsViewModel.closeAllConnections()
                                            showConnectionsMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = null)
                                        },
                                        enabled = connectionsUiState.connections.any { it.isActive },
                                    )
                                }
                            }
                        }

                        // Connections content
                        ConnectionsScreen(
                            serviceStatus = currentServiceStatus,
                            viewModel = connectionsViewModel,
                            onConnectionClick = { selectedConnectionId = it.id },
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

    override fun onServiceAlert(
        type: Alert,
        message: String?,
    ) {
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
    private fun ServiceAlertDialog(
        alertType: Alert,
        message: String?,
        onDismiss: () -> Unit,
    ) {
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
    private fun LocationPermissionDialog(
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
    ) {
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
    private fun BackgroundLocationPermissionDialog(
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
    ) {
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
