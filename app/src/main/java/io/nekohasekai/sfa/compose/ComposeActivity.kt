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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.ServiceConnection
import io.nekohasekai.sfa.bg.ServiceNotification
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.navigation.SFANavHost
import io.nekohasekai.sfa.compose.navigation.Screen
import io.nekohasekai.sfa.compose.navigation.bottomNavigationScreens
import io.nekohasekai.sfa.compose.screen.dashboard.CardGroup
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.screen.log.LogViewModel
import io.nekohasekai.sfa.compose.theme.SFATheme
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.ktx.hasPermission
import io.nekohasekai.sfa.ktx.launchCustomTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeActivity : ComponentActivity(), ServiceConnection.Callback {
    private val connection = ServiceConnection(this, this)
    private lateinit var dashboardViewModel: DashboardViewModel
    private var currentServiceStatus by mutableStateOf(Status.Stopped)
    private var currentAlert by mutableStateOf<Pair<Alert, String?>?>(null)
    private var showLocationPermissionDialog by mutableStateOf(false)
    private var showBackgroundLocationDialog by mutableStateOf(false)

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

        setContent {
            SFATheme {
                SFAApp()
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
                ContextCompat.startForegroundService(this@ComposeActivity, intent)
            }
            Settings.startedByUser = true
        }
    }

    private suspend fun prepare() =
        withContext(Dispatchers.Main) {
            try {
                val intent = VpnService.prepare(this@ComposeActivity)
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
                        this@ComposeActivity.launchCustomTab(event.url)
                    }

                    is UiEvent.RequestStartService -> {
                        startService()
                    }

                    is UiEvent.RequestReconnectService -> {
                        connection.reconnect()
                    }

                    is UiEvent.EditProfile -> {
                        val intent =
                            Intent(this@ComposeActivity, EditProfileComposeActivity::class.java)
                        intent.putExtra("profile_id", event.profileId)
                        startActivity(intent)
                    }

                    is UiEvent.RestartToTakeEffect -> {
                        if (currentServiceStatus == Status.Started) {
                            scope.launch {
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
                        // Show Groups and Others menu for Dashboard screen (but not in settings sub-screens)
                        if (currentScreen == Screen.Dashboard && !isSettingsSubScreen) {
                            // Groups button - only show when service is running, groups exist, and Groups card is disabled
                            if ((currentServiceStatus == Status.Started || currentServiceStatus == Status.Starting) &&
                                dashboardUiState.hasGroups &&
                                !dashboardUiState.visibleCards.contains(CardGroup.Groups)
                            ) {
                                IconButton(onClick = {
                                    val intent =
                                        Intent(
                                            this@ComposeActivity,
                                            GroupsComposeActivity::class.java,
                                        )
                                    startActivity(intent)
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = stringResource(R.string.title_groups),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            // More options button
                            IconButton(onClick = { dashboardViewModel.toggleCardSettingsDialog() }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.title_others),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        // Show actions only for Log screen and when logs are not empty
                        if (currentScreen == Screen.Log && logViewModel != null) {
                            val logUiState by logViewModel.uiState.collectAsState()

                            // Only show toolbar actions if logs are not empty and not in selection mode
                            if (logUiState.logs.isNotEmpty() && !logUiState.isSelectionMode) {
                                // Pause/Resume button
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

                                // Search button
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

                                // Options menu button
                                IconButton(onClick = { logViewModel.toggleOptionsMenu() }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.more_options),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            } // End of logs.isNotEmpty() check
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(),
                )
            },
            bottomBar = {
                // Only show bottom bar when not in settings sub-screens
                if (!isSettingsSubScreen) {
                    NavigationBar {
                        bottomNavigationScreens.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
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
            SFANavHost(
                navController = navController,
                serviceStatus = currentServiceStatus,
                dashboardViewModel = dashboardViewModel,
                logViewModel = logViewModel,
                modifier = Modifier.padding(paddingValues),
            )
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
                Alert.StartCommandServer -> stringResource(R.string.service_error_title_start_command_server)
                Alert.CreateService -> stringResource(R.string.service_error_title_create_service)
                Alert.StartService -> stringResource(R.string.service_error_title_start_service)
                else -> null
            }

        val dialogMessage =
            when (alertType) {
                Alert.RequestVPNPermission -> stringResource(R.string.service_error_missing_permission)
                Alert.RequestNotificationPermission -> stringResource(R.string.notification_permission_required_description)
                Alert.EmptyConfiguration -> stringResource(R.string.service_error_empty_configuration)
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
