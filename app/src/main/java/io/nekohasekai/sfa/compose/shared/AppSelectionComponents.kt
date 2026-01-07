package io.nekohasekai.sfa.compose.shared

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R

enum class SortMode {
    NAME,
    PACKAGE_NAME,
    UID,
    INSTALL_TIME,
    UPDATE_TIME,
}

class PackageCache(
    private val packageInfo: PackageInfo,
    private val appInfo: ApplicationInfo,
    private val packageManager: PackageManager,
) {
    val packageName: String get() = packageInfo.packageName

    val uid: Int get() = packageInfo.applicationInfo!!.uid

    val installTime: Long get() = packageInfo.firstInstallTime
    val updateTime: Long get() = packageInfo.lastUpdateTime
    val isSystem: Boolean get() = appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 1
    val isOffline: Boolean
        get() = packageInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) != true
    val isDisabled: Boolean get() = appInfo.flags and ApplicationInfo.FLAG_INSTALLED == 0

    val applicationIcon by lazy {
        val drawable = appInfo.loadIcon(packageManager)
        val bitmap =
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val imageBitmap =
                    Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                val canvas = Canvas(imageBitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                imageBitmap
            }
        bitmap.asImageBitmap()
    }

    val applicationLabel by lazy {
        appInfo.loadLabel(packageManager).toString()
    }

    val info: PackageInfo get() = packageInfo
}

fun buildDisplayPackages(
    packages: List<PackageCache>,
    selectedUids: Set<Int> = emptySet(),
    selectedFirst: Boolean = false,
    hideSystemApps: Boolean,
    hideOfflineApps: Boolean,
    hideDisabledApps: Boolean,
    sortMode: SortMode,
    sortReverse: Boolean,
): List<PackageCache> {
    val displayPackages =
        packages.filter { packageCache ->
            if (hideSystemApps && packageCache.isSystem) {
                return@filter false
            }
            if (hideOfflineApps && packageCache.isOffline) {
                return@filter false
            }
            if (hideDisabledApps && packageCache.isDisabled) {
                return@filter false
            }
            true
        }
    val sortComparator =
        Comparator<PackageCache> { left, right ->
            if (selectedFirst) {
                val selectedCompare =
                    compareValues(
                        !selectedUids.contains(left.uid),
                        !selectedUids.contains(right.uid),
                    )
                if (selectedCompare != 0) {
                    return@Comparator selectedCompare
                }
            }
            val value =
                when (sortMode) {
                    SortMode.NAME -> compareValues(left.applicationLabel, right.applicationLabel)
                    SortMode.PACKAGE_NAME -> compareValues(left.packageName, right.packageName)
                    SortMode.UID -> compareValues(left.uid, right.uid)
                    SortMode.INSTALL_TIME -> compareValues(left.installTime, right.installTime)
                    SortMode.UPDATE_TIME -> compareValues(left.updateTime, right.updateTime)
                }
            if (sortReverse) -value else value
        }
    return displayPackages.sortedWith(sortComparator)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppSelectionCard(
    packageCache: PackageCache,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    enableCopyActions: Boolean = true,
    onCopyLabel: (() -> Unit)? = null,
    onCopyPackage: (() -> Unit)? = null,
    onCopyUid: (() -> Unit)? = null,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showCopyMenu by remember { mutableStateOf(false) }
    val cardShape = MaterialTheme.shapes.medium
    val cardModifier =
        if (enableCopyActions) {
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .combinedClickable(
                    onClick = { onToggle(!selected) },
                    onLongClick = { showContextMenu = true },
                )
        } else {
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .clickable { onToggle(!selected) }
        }

    Box {
        Card(
            modifier = cardModifier,
            shape = cardShape,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    bitmap = packageCache.applicationIcon,
                    contentDescription = stringResource(R.string.content_description_app_icon),
                    modifier = Modifier.size(40.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = packageCache.applicationLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${packageCache.packageName} (${packageCache.uid})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = true,
                    )
                }
                Switch(
                    checked = selected,
                    onCheckedChange = { onToggle(it) },
                )
            }
        }

        if (enableCopyActions) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = {
                    showContextMenu = false
                    showCopyMenu = false
                },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.per_app_proxy_action_copy)) },
                    onClick = { showCopyMenu = !showCopyMenu },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector =
                                if (showCopyMenu) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                            contentDescription = null,
                        )
                    },
                )
                if (showCopyMenu) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_name)) },
                        onClick = {
                            showContextMenu = false
                            showCopyMenu = false
                            onCopyLabel?.invoke()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.per_app_proxy_action_copy_package_name)) },
                        onClick = {
                            showContextMenu = false
                            showCopyMenu = false
                            onCopyPackage?.invoke()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.per_app_proxy_action_copy_uid)) },
                        onClick = {
                            showContextMenu = false
                            showCopyMenu = false
                            onCopyUid?.invoke()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}
