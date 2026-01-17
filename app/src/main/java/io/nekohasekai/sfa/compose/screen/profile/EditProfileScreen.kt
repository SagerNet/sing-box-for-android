package io.nekohasekai.sfa.compose.screen.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.base.SelectableMessageDialog
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.compose.util.ProfileIcons
import io.nekohasekai.sfa.compose.util.RelativeTimeFormatter
import io.nekohasekai.sfa.compose.util.icons.MaterialIconsLibrary
import io.nekohasekai.sfa.database.TypedProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    profileId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToIconSelection: (currentIconId: String?) -> Unit = {},
    onNavigateToEditContent: (profileName: String, isReadOnly: Boolean) -> Unit = { _, _ -> },
    viewModel: EditProfileViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Clear success indicator after delay
    LaunchedEffect(uiState.showUpdateSuccess) {
        if (uiState.showUpdateSuccess) {
            kotlinx.coroutines.delay(1500)
            viewModel.clearUpdateSuccess()
        }
    }

    // Dialog states
    var showErrorDialog by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    // Launch icon selection screen when needed
    if (uiState.showIconDialog) {
        LaunchedEffect(Unit) {
            viewModel.hideIconDialog()
            onNavigateToIconSelection(uiState.icon)
        }
    }

    // Show error dialog when there's an error message
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            showErrorDialog = true
        }
    }

    // Error dialog
    if (showErrorDialog) {
        SelectableMessageDialog(
            title = stringResource(R.string.error_title),
            message = uiState.errorMessage ?: "",
            onDismiss = {
                showErrorDialog = false
                viewModel.clearError()
            },
        )
    }

    // Unsaved changes dialog
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onNavigateBack()
                    },
                ) {
                    Text(
                        stringResource(R.string.discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUnsavedChangesDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // Handle back navigation
    val handleBack = {
        if (uiState.hasChanges) {
            showUnsavedChangesDialog = true
        } else {
            onNavigateBack()
        }
    }

    // Intercept system back button
    BackHandler(enabled = uiState.hasChanges) {
        showUnsavedChangesDialog = true
    }

    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.title_edit_profile)) },
            navigationIcon = {
                IconButton(onClick = handleBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
            colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }

    val bottomInset =
        with(LocalDensity.current) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }
    val bottomBarPadding =
        if (uiState.hasChanges) {
            88.dp + bottomInset
        } else {
            0.dp
        }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Progress indicator at top (only for initial loading)
        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!uiState.isLoading) {
            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = bottomBarPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Basic Information Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.basic_information),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = viewModel::updateName,
                            label = { Text(stringResource(R.string.profile_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )

                        // Icon selection with Material You style
                        Text(
                            text = stringResource(R.string.icon),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )

                        Surface(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.showIconDialog() },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Display current icon
                                val currentIcon =
                                    ProfileIcons.getIconById(uiState.icon)
                                        ?: Icons.AutoMirrored.Filled.InsertDriveFile

                                Icon(
                                    imageVector = currentIcon,
                                    contentDescription = stringResource(R.string.profile_icon),
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )

                                Text(
                                    text =
                                    uiState.icon?.let { iconId ->
                                        MaterialIconsLibrary.getAllIcons()
                                            .find { it.id == iconId }?.label
                                    } ?: stringResource(R.string.default_text),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.select_icon),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Remote Profile Options
                if (uiState.profileType == TypedProfile.Type.Remote) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.remote_configuration),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                        uiState.lastUpdated?.let { lastUpdated ->
                                            Text(
                                                text =
                                                stringResource(
                                                    R.string.last_updated_format,
                                                    RelativeTimeFormatter.format(
                                                        context,
                                                        lastUpdated,
                                                    ),
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                // Update button in top-right corner
                                IconButton(
                                    onClick = { viewModel.updateRemoteProfile() },
                                    enabled = !uiState.isUpdating && !uiState.showUpdateSuccess,
                                ) {
                                    when {
                                        uiState.isUpdating -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                        uiState.showUpdateSuccess -> {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = stringResource(R.string.success),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                Icons.Default.Update,
                                                contentDescription = stringResource(R.string.profile_update),
                                                tint = MaterialTheme.colorScheme.tertiary,
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = uiState.remoteUrl,
                                onValueChange = viewModel::updateRemoteUrl,
                                label = { Text(stringResource(R.string.profile_url)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )

                            HorizontalDivider()

                            // Auto Update Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_auto_update),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Switch(
                                    checked = uiState.autoUpdate,
                                    onCheckedChange = viewModel::updateAutoUpdate,
                                )
                            }

                            AnimatedVisibility(visible = uiState.autoUpdate) {
                                OutlinedTextField(
                                    value = uiState.autoUpdateInterval.toString(),
                                    onValueChange = viewModel::updateAutoUpdateInterval,
                                    label = { Text(stringResource(R.string.profile_auto_update_interval)) },
                                    supportingText = {
                                        uiState.autoUpdateIntervalError?.let { error ->
                                            Text(
                                                text = error,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        } ?: Text(stringResource(R.string.profile_auto_update_interval_minimum_hint))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    isError = uiState.autoUpdateIntervalError != null,
                                )
                            }
                        }
                    }
                }

                // Content Card (for both Local and Remote profiles) - placed at the end
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.content),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }

                        // JSON Editor/Viewer option
                        Surface(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onNavigateToEditContent(
                                        uiState.name,
                                        uiState.profileType == TypedProfile.Type.Remote,
                                    )
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text =
                                    if (uiState.profileType == TypedProfile.Type.Remote) {
                                        stringResource(R.string.json_viewer)
                                    } else {
                                        stringResource(R.string.json_editor)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = uiState.hasChanges,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                ) {
                    Button(
                        onClick = { viewModel.saveChanges() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving && uiState.autoUpdateIntervalError == null,
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}
