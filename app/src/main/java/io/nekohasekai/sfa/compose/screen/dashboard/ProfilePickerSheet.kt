package io.nekohasekai.sfa.compose.screen.dashboard

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.ProfileContent
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.qr.QRCodeDialog
import io.nekohasekai.sfa.compose.util.ProfileIcons
import io.nekohasekai.sfa.compose.util.QRCodeGenerator
import io.nekohasekai.sfa.compose.util.RelativeTimeFormatter
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.ktx.shareProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfilePickerSheet(
    profiles: List<Profile>,
    selectedProfileId: Long,
    onProfileSelected: (Profile) -> Unit,
    onProfileEdit: (Profile) -> Unit,
    onProfileDelete: (Profile) -> Unit,
    onProfileMove: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showQRCodeDialog by remember { mutableStateOf(false) }
    var qrCodeProfile by remember { mutableStateOf<Profile?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.title_configuration),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 8.dp,
                    bottom = 16.dp,
                ),
            )

            val lazyListState = rememberLazyListState()
            val reorderableLazyListState =
                rememberReorderableLazyListState(lazyListState) { from, to ->
                    onProfileMove(from.index, to.index)
                }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 400.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(profiles, key = { _, profile -> profile.id }) { _, profile ->
                    ReorderableItem(
                        reorderableLazyListState,
                        key = profile.id,
                    ) { isDragging ->
                        ProfilePickerRow(
                            profile = profile,
                            isSelected = profile.id == selectedProfileId,
                            isDragging = isDragging,
                            onSelect = {
                                onProfileSelected(profile)
                                onDismiss()
                            },
                            onEdit = { onProfileEdit(profile) },
                            onShare = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        context.shareProfile(profile)
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            onShareURL = {
                                qrCodeProfile = profile
                                showQRCodeDialog = true
                            },
                            onDelete = { onProfileDelete(profile) },
                            modifier = Modifier.longPressDraggableHandle(),
                        )
                    }
                }
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
        val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
        val qrBitmap = QRCodeGenerator.rememberPrimaryBitmap(link, backgroundColor = surfaceColor)

        QRCodeDialog(
            bitmap = qrBitmap,
            onDismiss = {
                showQRCodeDialog = false
                qrCodeProfile = null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePickerRow(
    profile: Profile,
    isSelected: Boolean,
    isDragging: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onShareURL: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var expandedShareSubmenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val animatedElevation by animateFloatAsState(
        targetValue = when {
            isDragging -> 8.dp.value
            isSelected -> 2.dp.value
            else -> 0.dp.value
        },
        animationSpec = tween(300),
        label = "Elevation",
    )

    val saveFileLauncher = rememberLauncherForActivityResult(
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
                        Toast.makeText(
                            context,
                            context.getString(R.string.success_profile_saved),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "${context.getString(R.string.failed_save_profile)}: ${e.message}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = when {
            isDragging -> MaterialTheme.colorScheme.tertiaryContainer
            isSelected -> if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceDim
            }
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        tonalElevation = animatedElevation.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val profileIcon =
                ProfileIcons.getIconById(profile.icon)
                    ?: Icons.AutoMirrored.Default.InsertDriveFile

            Icon(
                imageVector = profileIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = when (profile.typed.type) {
                        TypedProfile.Type.Local -> stringResource(R.string.profile_type_local)
                        TypedProfile.Type.Remote -> stringResource(
                            R.string.profile_type_remote_updated,
                            RelativeTimeFormatter.format(context, profile.typed.lastUpdated),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                Box {
                    IconButton(
                        onClick = {
                            showMenu = true
                            expandedShareSubmenu = false
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = {
                            showMenu = false
                            expandedShareSubmenu = false
                        },
                        modifier = Modifier.widthIn(min = 200.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )

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
                                    imageVector = if (expandedShareSubmenu) {
                                        Icons.Default.ExpandLess
                                    } else {
                                        Icons.Default.ExpandMore
                                    },
                                    contentDescription = null,
                                )
                            },
                        )

                        if (expandedShareSubmenu) {
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

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_as_file)) },
                                onClick = {
                                    showMenu = false
                                    onShare()
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

                            if (profile.typed.type == TypedProfile.Type.Remote) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.profile_share_url)) },
                                    onClick = {
                                        showMenu = false
                                        onShareURL()
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

                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.menu_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
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
}
