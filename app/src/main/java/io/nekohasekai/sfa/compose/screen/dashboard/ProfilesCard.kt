package io.nekohasekai.sfa.compose.screen.dashboard

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.ProfileContent
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.NewProfileComposeActivity
import io.nekohasekai.sfa.compose.screen.configuration.ProfileImportHandler
import io.nekohasekai.sfa.compose.screen.configuration.QRCodeDialog
import io.nekohasekai.sfa.compose.util.ProfileIcons
import io.nekohasekai.sfa.compose.util.QRCodeGenerator
import io.nekohasekai.sfa.compose.util.RelativeTimeFormatter
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.shareProfile
import io.nekohasekai.sfa.ui.profile.QRScanActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfilesCard(
    profiles: List<Profile>,
    selectedProfileId: Long,
    isLoading: Boolean,
    showAddProfileSheet: Boolean,
    updatingProfileId: Long? = null,
    updatedProfileId: Long? = null,
    onProfileSelected: (Long) -> Unit,
    onProfileEdit: (Profile) -> Unit,
    onProfileDelete: (Profile) -> Unit,
    onProfileShare: (Profile) -> Unit,
    onProfileShareURL: (Profile) -> Unit,
    onProfileUpdate: (Profile) -> Unit,
    onProfileMove: (Int, Int) -> Unit,
    onShowAddProfileSheet: () -> Unit,
    onHideAddProfileSheet: () -> Unit,
    onImportFromFile: () -> Unit,
    onScanQrCode: () -> Unit,
    onCreateManually: () -> Unit,
    shareQRCodeImage: suspend (Bitmap, String) -> Unit,
    saveQRCodeToGallery: suspend (Bitmap, String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Import handler
    val importHandler = remember { ProfileImportHandler(context) }

    // QR code dialog state
    var showQRCodeDialog by remember { mutableStateOf(false) }
    var qrCodeProfile by remember { mutableStateOf<Profile?>(null) }

    // Activity result launchers
    val newProfileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val profileId = result.data?.getLongExtra(NewProfileComposeActivity.EXTRA_PROFILE_ID, -1L)
                if (profileId != null && profileId != -1L) {
                    // Find the profile and open edit screen
                    coroutineScope.launch {
                        val profile =
                            withContext(Dispatchers.IO) {
                                ProfileManager.get(profileId)
                            }
                        profile?.let {
                            withContext(Dispatchers.Main) {
                                onProfileEdit(it)
                            }
                        }
                    }
                }
            }
        }

    val importFromFileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            uri?.let {
                coroutineScope.launch {
                    when (val result = importHandler.importFromUri(uri)) {
                        is ProfileImportHandler.ImportResult.Success -> {
                            // Profile imported successfully, open edit screen
                            withContext(Dispatchers.Main) {
                                onProfileEdit(result.profile)
                            }
                        }
                        is ProfileImportHandler.ImportResult.Error -> {
                            withContext(Dispatchers.Main) {
                                context.errorDialogBuilder(Exception(result.message)).show()
                            }
                        }
                    }
                }
            }
        }

    val scanQrCodeLauncher =
        rememberLauncherForActivityResult(
            QRScanActivity.Contract(),
        ) { result ->
            result?.let { intent ->
                val data = intent.dataString
                if (data != null) {
                    coroutineScope.launch {
                        when (val parseResult = importHandler.parseQRCode(data)) {
                            is ProfileImportHandler.QRCodeParseResult.RemoteProfile -> {
                                withContext(Dispatchers.Main) {
                                    val newProfileIntent =
                                        Intent(context, NewProfileComposeActivity::class.java).apply {
                                            putExtra(NewProfileComposeActivity.EXTRA_IMPORT_NAME, parseResult.name)
                                            putExtra(NewProfileComposeActivity.EXTRA_IMPORT_URL, parseResult.url)
                                        }
                                    newProfileLauncher.launch(newProfileIntent)
                                }
                            }

                            is ProfileImportHandler.QRCodeParseResult.LocalProfile -> {
                                when (val importResult = importHandler.importFromQRCode(data)) {
                                    is ProfileImportHandler.ImportResult.Success -> {
                                        withContext(Dispatchers.Main) {
                                            onProfileEdit(importResult.profile)
                                        }
                                    }

                                    is ProfileImportHandler.ImportResult.Error -> {
                                        withContext(Dispatchers.Main) {
                                            context.errorDialogBuilder(Exception(importResult.message)).show()
                                        }
                                    }
                                }
                            }

                            is ProfileImportHandler.QRCodeParseResult.Error -> {
                                withContext(Dispatchers.Main) {
                                    context.errorDialogBuilder(Exception(parseResult.message)).show()
                                }
                            }
                        }
                    }
                }
            }
        }

    // Handle import events
    LaunchedEffect(onImportFromFile, onScanQrCode) {
        // These are just to trigger the launchers
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Header with title and add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.title_configuration),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                IconButton(
                    onClick = onShowAddProfileSheet,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_profile),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_profiles),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ProfileList(
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    isLoading = isLoading,
                    updatingProfileId = updatingProfileId,
                    updatedProfileId = updatedProfileId,
                    onProfileClick = { profile ->
                        if (profile.id != selectedProfileId) {
                            onProfileSelected(profile.id)
                        }
                    },
                    onEditProfile = onProfileEdit,
                    onDeleteProfile = onProfileDelete,
                    onShareProfile = { profile ->
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                context.shareProfile(profile)
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    context.errorDialogBuilder(e).show()
                                }
                            }
                        }
                    },
                    onShareProfileURL = { profile ->
                        qrCodeProfile = profile
                        showQRCodeDialog = true
                    },
                    onUpdateProfile = onProfileUpdate,
                    onMove = onProfileMove,
                )
            }
        }
    }

    // Add profile bottom sheet
    if (showAddProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = onHideAddProfileSheet,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
            ) {
                Text(
                    text = stringResource(R.string.add_profile),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )

                ListItem(
                    modifier =
                        Modifier.clickable {
                            onHideAddProfileSheet()
                            // Accept any file type to support both JSON and encoded profile files
                            importFromFileLauncher.launch("*/*")
                        },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text(stringResource(R.string.profile_add_import_file))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.import_from_file_description))
                    },
                )

                ListItem(
                    modifier =
                        Modifier.clickable {
                            onHideAddProfileSheet()
                            scanQrCodeLauncher.launch(null)
                        },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text(stringResource(R.string.profile_add_scan_qr_code))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.scan_qr_code_description))
                    },
                )

                ListItem(
                    modifier =
                        Modifier.clickable {
                            onHideAddProfileSheet()
                            val intent = Intent(context, NewProfileComposeActivity::class.java)
                            newProfileLauncher.launch(intent)
                        },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text(stringResource(R.string.profile_add_create_manually))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.create_new_profile_description))
                    },
                )
            }
        }
    }

    // QR Code dialog
    if (showQRCodeDialog && qrCodeProfile != null) {
        val profile = qrCodeProfile!!
        val link =
            remember(profile) {
                Libbox.generateRemoteProfileImportLink(
                    profile.name,
                    profile.typed.remoteURL,
                )
            }
        val qrBitmap =
            remember(link) {
                QRCodeGenerator.generate(link)
            }

        QRCodeDialog(
            bitmap = qrBitmap,
            onDismiss = {
                showQRCodeDialog = false
                qrCodeProfile = null
            },
            onShare = {
                coroutineScope.launch {
                    shareQRCodeImage(qrBitmap, profile.name)
                }
                showQRCodeDialog = false
                qrCodeProfile = null
            },
            onSave = {
                coroutineScope.launch {
                    saveQRCodeToGallery(qrBitmap, profile.name)
                    showQRCodeDialog = false
                    qrCodeProfile = null
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileList(
    profiles: List<Profile>,
    selectedProfileId: Long,
    isLoading: Boolean,
    updatingProfileId: Long? = null,
    updatedProfileId: Long? = null,
    onProfileClick: (Profile) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onShareProfile: (Profile) -> Unit,
    onShareProfileURL: (Profile) -> Unit,
    onUpdateProfile: (Profile) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            onMove(from.index, to.index)
        }

    LazyColumn(
        state = lazyListState,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp, max = 400.dp),
        // Flexible height with min/max constraints
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = profiles.size > 6, // Only enable scroll if more than 6 profiles
    ) {
        itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
            ReorderableItem(
                reorderableLazyListState,
                key = profile.id,
            ) { isDragging ->
                ProfileItem(
                    profile = profile,
                    isSelected = profile.id == selectedProfileId,
                    isDragging = isDragging,
                    isLoading = isLoading,
                    isUpdating = profile.id == updatingProfileId,
                    showUpdateSuccess = profile.id == updatedProfileId,
                    onProfileClick = onProfileClick,
                    onEditProfile = onEditProfile,
                    onDeleteProfile = onDeleteProfile,
                    onShareProfile = onShareProfile,
                    onShareProfileURL = onShareProfileURL,
                    onUpdateProfile = onUpdateProfile,
                    modifier = Modifier.longPressDraggableHandle(),
                )
            }
        }
    }
}

private suspend fun createProfileContent(profile: Profile): ByteArray {
    val content = ProfileContent()
    content.name = profile.name
    when (profile.typed.type) {
        TypedProfile.Type.Local -> {
            content.type = Libbox.ProfileTypeLocal
        }
        TypedProfile.Type.Remote -> {
            content.type = Libbox.ProfileTypeRemote
        }
    }
    content.config = java.io.File(profile.typed.path).readText()
    content.remotePath = profile.typed.remoteURL
    content.autoUpdate = profile.typed.autoUpdate
    content.autoUpdateInterval = profile.typed.autoUpdateInterval
    content.lastUpdated = profile.typed.lastUpdated.time
    return content.encode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileItem(
    profile: Profile,
    isSelected: Boolean,
    isDragging: Boolean,
    isLoading: Boolean,
    isUpdating: Boolean = false,
    showUpdateSuccess: Boolean = false,
    onProfileClick: (Profile) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onShareProfile: (Profile) -> Unit,
    onShareProfileURL: (Profile) -> Unit,
    onUpdateProfile: (Profile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var expandedShareSubmenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Animated values for visual feedback
    val animatedElevation by animateFloatAsState(
        targetValue =
            when {
                isDragging -> 8.dp.value
                isSelected -> 3.dp.value
                else -> 1.dp.value
            },
        animationSpec = tween(300),
        label = "Elevation",
    )

    val animatedBorderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.8f else 0.3f,
        animationSpec = tween(300),
        label = "BorderAlpha",
    )

    // File save launcher
    val saveFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val profileData = createProfileContent(profile)
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(profileData)
                        }
                        withContext(Dispatchers.Main) {
                            val successMessage = context.getString(R.string.profile_saved_successfully)
                            Toast.makeText(
                                context,
                                successMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            val failedMessage = context.getString(R.string.profile_save_failed)
                            Toast.makeText(
                                context,
                                "$failedMessage: ${e.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }

    Surface(
        onClick = { if (!isLoading) onProfileClick(profile) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color =
            when {
                isDragging -> MaterialTheme.colorScheme.tertiaryContainer
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
        tonalElevation = animatedElevation.dp,
        border =
            androidx.compose.foundation.BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color =
                    when {
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = animatedBorderAlpha)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = animatedBorderAlpha)
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Profile icon - use custom icon if set, otherwise default
            val profileIcon =
                ProfileIcons.getIconById(profile.icon)
                    ?: Icons.AutoMirrored.Default.InsertDriveFile

            Icon(
                imageVector = profileIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Profile info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Profile name
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Second line: Type and last updated
                val context = LocalContext.current
                Text(
                    text =
                        when (profile.typed.type) {
                            TypedProfile.Type.Local -> stringResource(R.string.profile_type_local)
                            TypedProfile.Type.Remote ->
                                stringResource(
                                    R.string.profile_type_remote_updated,
                                    RelativeTimeFormatter.format(context, profile.typed.lastUpdated),
                                )
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                )
            }

            // Update button for remote profiles
            if (profile.typed.type == TypedProfile.Type.Remote) {
                IconButton(
                    onClick = {
                        if (!isUpdating && !showUpdateSuccess) {
                            onUpdateProfile(profile)
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    enabled = !isUpdating && !showUpdateSuccess,
                ) {
                    when {
                        isUpdating -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        showUpdateSuccess -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.update_successful),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        else -> {
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = stringResource(R.string.update_profile),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // More options button
            Spacer(modifier = Modifier.width(4.dp))

            Box {
                IconButton(
                    onClick = {
                        showMenu = true
                        expandedShareSubmenu = false // Always start with submenu collapsed
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        modifier = Modifier.size(20.dp),
                        tint =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = {
                        showMenu = false
                        expandedShareSubmenu = false // Reset submenu state when closing
                    },
                    modifier = Modifier.widthIn(min = 200.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = {
                            showMenu = false
                            onEditProfile(profile)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )

                    // Share submenu header
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_share)) },
                        onClick = {
                            expandedShareSubmenu = !expandedShareSubmenu
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.IosShare,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector =
                                    if (expandedShareSubmenu) {
                                        Icons.Default.ExpandLess
                                    } else {
                                        Icons.Default.ExpandMore
                                    },
                                contentDescription = null,
                            )
                        },
                    )

                    // Share submenu items (shown inline when expanded)
                    if (expandedShareSubmenu) {
                        // Save As File
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_as_file)) },
                            onClick = {
                                showMenu = false
                                saveFileLauncher.launch("${profile.name}.bpf")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 24.dp),
                                )
                            },
                        )

                        // Share As File
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_as_file)) },
                            onClick = {
                                showMenu = false
                                onShareProfile(profile)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.IosShare,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 24.dp),
                                )
                            },
                        )

                        // Share URL as QR Code (only for remote profiles)
                        if (profile.typed.type == TypedProfile.Type.Remote) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profile_share_url)) },
                                onClick = {
                                    showMenu = false
                                    onShareProfileURL(profile)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.QrCode2,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 24.dp),
                                    )
                                },
                            )
                        }
                    }

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.menu_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDeleteProfile(profile)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
    }
}
