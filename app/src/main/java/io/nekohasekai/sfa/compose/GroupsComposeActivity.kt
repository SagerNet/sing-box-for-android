package io.nekohasekai.sfa.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.ServiceConnection
import io.nekohasekai.sfa.compose.screen.dashboard.GroupsCard
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.theme.SFATheme
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.Status

class GroupsComposeActivity : ComponentActivity(), ServiceConnection.Callback {
    private val connection = ServiceConnection(this, this)
    private var currentServiceStatus by mutableStateOf(Status.Stopped)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        connection.reconnect()

        setContent {
            SFATheme {
                GroupsApp()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GroupsApp() {
        val viewModel: GroupsViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsState()
        val allCollapsed = uiState.expandedGroups.isEmpty()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.title_groups)) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.content_description_back),
                            )
                        }
                    },
                    actions = {
                        if (uiState.groups.isNotEmpty()) {
                            IconButton(onClick = { viewModel.toggleAllGroups() }) {
                                Icon(
                                    imageVector =
                                        if (allCollapsed) {
                                            Icons.Default.UnfoldMore
                                        } else {
                                            Icons.Default.UnfoldLess
                                        },
                                    contentDescription =
                                        if (allCollapsed) {
                                            stringResource(R.string.expand_all)
                                        } else {
                                            stringResource(R.string.collapse_all)
                                        },
                                )
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                )
            },
        ) { paddingValues ->
            GroupsCard(
                serviceStatus = currentServiceStatus,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }

    override fun onServiceStatusChanged(status: Status) {
        currentServiceStatus = status
    }

    override fun onServiceAlert(
        type: Alert,
        message: String?,
    ) {
        // Handle alerts if needed
    }

    override fun onDestroy() {
        connection.disconnect()
        super.onDestroy()
    }
}
