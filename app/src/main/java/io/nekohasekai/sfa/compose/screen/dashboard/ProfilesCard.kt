package io.nekohasekai.sfa.compose.screen.dashboard

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.ProfileContent
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.NewProfileActivity
import io.nekohasekai.sfa.compose.screen.configuration.ProfileImportHandler
import io.nekohasekai.sfa.compose.screen.configuration.QRCodeDialog
import io.nekohasekai.sfa.compose.util.QRCodeGenerator
import io.nekohasekai.sfa.compose.util.RelativeTimeFormatter
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.shareProfile
import io.nekohasekai.sfa.ui.profile.QRScanActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesCard(
    profiles: List<Profile>,
    selectedProfileId: Long,
    isLoading: Boolean,
    showAddProfileSheet: Boolean,
    showProfilePickerSheet: Boolean,
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
    onShowProfilePickerSheet: () -> Unit,
    onHideProfilePickerSheet: () -> Unit,
    onImportFromFile: () -> Unit,
    onScanQrCode: () -> Unit,
    onCreateManually: () -> Unit,
    shareQRCodeImage: suspend (Bitmap, String) -> Unit,
    saveQRCodeToGallery: suspend (Bitmap, String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val importHandler = remember { ProfileImportHandler(context) }

    var showQRCodeDialog by remember { mutableStateOf(false) }
    var qrCodeProfile by remember { mutableStateOf<Profile?>(null) }

    val newProfileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val profileId = result.data?.getLongExtra(NewProfileActivity.EXTRA_PROFILE_ID, -1L)
                if (profileId != null && profileId != -1L) {
                    coroutineScope.launch {
                        val profile =
                            withContext(Dispatchers.IO) {
                                io.nekohasekai.sfa.database.ProfileManager.get(profileId)
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
                                        Intent(context, NewProfileActivity::class.java).apply {
                                            putExtra(NewProfileActivity.EXTRA_IMPORT_NAME, parseResult.name)
                                            putExtra(NewProfileActivity.EXTRA_IMPORT_URL, parseResult.url)
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

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            val selectedProfile = profiles.find { it.id == selectedProfileId }
            if (selectedProfile != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val profileData = createProfileContent(selectedProfile)
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(profileData)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.profile_saved_successfully),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "${context.getString(R.string.profile_save_failed)}: ${e.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(onImportFromFile, onScanQrCode) {
    }

    val selectedProfile = profiles.find { it.id == selectedProfileId }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
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

                Surface(
                    onClick = onShowAddProfileSheet,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSystemInDarkTheme()) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceDim
                    },
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_profile),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_profiles),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ProfileSelectorButton(
                    selectedProfile = selectedProfile,
                    onClick = onShowProfilePickerSheet,
                )

                Spacer(modifier = Modifier.height(12.dp))

                ProfileInfoRow(profile = selectedProfile)

                Spacer(modifier = Modifier.height(16.dp))

                ProfileActionRow(
                    profile = selectedProfile,
                    isUpdating = selectedProfile?.id == updatingProfileId,
                    showUpdateSuccess = selectedProfile?.id == updatedProfileId,
                    onEdit = { selectedProfile?.let { onProfileEdit(it) } },
                    onUpdate = { selectedProfile?.let { onProfileUpdate(it) } },
                    onShareFile = {
                        selectedProfile?.let {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    context.shareProfile(it)
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        context.errorDialogBuilder(e).show()
                                    }
                                }
                            }
                        }
                    },
                    onSaveFile = {
                        selectedProfile?.let {
                            saveFileLauncher.launch("${it.name}.bpf")
                        }
                    },
                    onShareURL = {
                        selectedProfile?.let {
                            qrCodeProfile = it
                            showQRCodeDialog = true
                        }
                    },
                )
            }
        }
    }

    if (showProfilePickerSheet) {
        ProfilePickerSheet(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            onProfileSelected = { profile -> onProfileSelected(profile.id) },
            onProfileEdit = onProfileEdit,
            onProfileDelete = onProfileDelete,
            onProfileMove = onProfileMove,
            onDismiss = onHideProfilePickerSheet,
            shareQRCodeImage = shareQRCodeImage,
            saveQRCodeToGallery = saveQRCodeToGallery,
        )
    }

    if (showAddProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = onHideAddProfileSheet,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = stringResource(R.string.add_profile),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )

                ListItem(
                    modifier = Modifier.clickable {
                        onHideAddProfileSheet()
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
                    modifier = Modifier.clickable {
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
                    modifier = Modifier.clickable {
                        onHideAddProfileSheet()
                        val intent = Intent(context, NewProfileActivity::class.java)
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

    if (showQRCodeDialog && qrCodeProfile != null) {
        val profile = qrCodeProfile!!
        val link = remember(profile) {
            Libbox.generateRemoteProfileImportLink(
                profile.name,
                profile.typed.remoteURL,
            )
        }
        val qrBitmap = remember(link) {
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

@Composable
private fun ProfileInfoRow(profile: Profile?) {
    if (profile == null) return

    val context = LocalContext.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (profile.typed.type == TypedProfile.Type.Remote) {
                    Icons.Default.Cloud
                } else {
                    Icons.Outlined.Description
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (profile.typed.type == TypedProfile.Type.Remote) {
                    stringResource(R.string.profile_type_remote)
                } else {
                    stringResource(R.string.profile_type_local)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (profile.typed.type == TypedProfile.Type.Remote) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = RelativeTimeFormatter.format(context, profile.typed.lastUpdated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileActionRow(
    profile: Profile?,
    isUpdating: Boolean,
    showUpdateSuccess: Boolean,
    onEdit: () -> Unit,
    onUpdate: () -> Unit,
    onShareFile: () -> Unit,
    onSaveFile: () -> Unit,
    onShareURL: () -> Unit,
) {
    if (profile == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionButton(
            icon = Icons.Default.Edit,
            contentDescription = stringResource(R.string.edit),
            onClick = onEdit,
        )

        if (profile.typed.type == TypedProfile.Type.Remote) {
            ActionButton(
                icon = when {
                    showUpdateSuccess -> Icons.Default.Check
                    else -> Icons.Default.Refresh
                },
                contentDescription = stringResource(R.string.update_profile),
                onClick = onUpdate,
                enabled = !isUpdating && !showUpdateSuccess,
                isLoading = isUpdating,
            )
        }

        ShareButton(
            profile = profile,
            onShareFile = onShareFile,
            onSaveFile = onSaveFile,
            onShareURL = onShareURL,
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceDim
        },
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}

@Composable
private fun ShareButton(
    profile: Profile,
    onShareFile: () -> Unit,
    onSaveFile: () -> Unit,
    onShareURL: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ActionButton(
            icon = Icons.Default.IosShare,
            contentDescription = stringResource(R.string.menu_share),
            onClick = { expanded = true },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.save_as_file)) },
                onClick = {
                    expanded = false
                    onSaveFile()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_as_file)) },
                onClick = {
                    expanded = false
                    onShareFile()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            if (profile.typed.type == TypedProfile.Type.Remote) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_share_url)) },
                    onClick = {
                        expanded = false
                        onShareURL()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }
}
