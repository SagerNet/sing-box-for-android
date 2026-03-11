package io.nekohasekai.sfa.compose.screen.settings

import android.webkit.URLUtil
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MirrorEntry(
    val url: String,
    val name: String,
    val country: String,
    val isCustom: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FDroidMirrorScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.fdroid_mirror)) },
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

    val scope = rememberCoroutineScope()
    var selectedMirrorUrl by remember { mutableStateOf(Settings.fdroidMirrorUrl) }
    var isTesting by remember { mutableStateOf(false) }
    val latencyResults = remember { mutableStateMapOf<String, Int>() }
    val latencyErrors = remember { mutableStateMapOf<String, Boolean>() }
    var showAddForm by remember { mutableStateOf(false) }
    var newMirrorName by remember { mutableStateOf("") }
    var newMirrorUrl by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    val invalidUrlMessage = stringResource(R.string.fdroid_mirror_invalid_url)
    var customMirrors by remember { mutableStateOf(Settings.fdroidCustomMirrors) }

    val builtinMirrors = remember {
        val mirrors = mutableListOf<MirrorEntry>()
        val iter = Libbox.getFDroidMirrors()
        while (iter.hasNext()) {
            val m = iter.next()
            mirrors.add(MirrorEntry(url = m.url, name = m.name, country = m.country))
        }
        mirrors
    }

    val parsedCustomMirrors = remember(customMirrors) {
        customMirrors.map { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                MirrorEntry(url = parts[1], name = parts[0], country = "", isCustom = true)
            } else {
                MirrorEntry(url = entry, name = entry, country = "", isCustom = true)
            }
        }
    }

    val allMirrors = builtinMirrors + parsedCustomMirrors

    fun selectMirror(url: String) {
        selectedMirrorUrl = url
        Settings.fdroidMirrorUrl = url
    }

    fun testAllMirrors() {
        isTesting = true
        latencyResults.clear()
        latencyErrors.clear()
        scope.launch {
            allMirrors.map { mirror ->
                async(Dispatchers.IO) {
                    val r = Libbox.pingFDroidMirror(mirror.url)
                    withContext(Dispatchers.Main) {
                        if (r.latencyMs < 0) {
                            latencyErrors[r.url] = true
                        } else {
                            latencyResults[r.url] = r.latencyMs
                        }
                    }
                }
            }.awaitAll()
            val fastest = latencyResults.minByOrNull { it.value }
            if (fastest != null) {
                selectMirror(fastest.key)
            }
            isTesting = false
        }
    }

    val grouped = remember(builtinMirrors) {
        builtinMirrors.groupBy { it.country }
    }
    val countryOrder = remember(grouped) { grouped.keys.toList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        FilledTonalButton(
            onClick = { testAllMirrors() },
            enabled = !isTesting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.fdroid_mirror_testing))
            } else {
                Icon(
                    imageVector = Icons.Outlined.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.fdroid_mirror_test_all))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        countryOrder.forEach { country ->
            val mirrors = grouped[country] ?: return@forEach

            Text(
                text = country,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column {
                    mirrors.forEachIndexed { index, mirror ->
                        val shape = when {
                            mirrors.size == 1 -> RoundedCornerShape(12.dp)
                            index == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            index == mirrors.lastIndex -> RoundedCornerShape(
                                bottomStart = 12.dp,
                                bottomEnd = 12.dp,
                            )
                            else -> RoundedCornerShape(0.dp)
                        }
                        ListItem(
                            headlineContent = {
                                Text(
                                    mirror.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedMirrorUrl == mirror.url,
                                    onClick = { selectMirror(mirror.url) },
                                )
                            },
                            trailingContent = {
                                LatencyBadge(
                                    url = mirror.url,
                                    latencyResults = latencyResults,
                                    latencyErrors = latencyErrors,
                                )
                            },
                            modifier = Modifier
                                .clip(shape)
                                .clickable { selectMirror(mirror.url) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = stringResource(R.string.fdroid_mirror_custom),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column {
                parsedCustomMirrors.forEachIndexed { index, mirror ->
                    val isLast = index == parsedCustomMirrors.lastIndex && !showAddForm
                    val shape = when {
                        index == 0 && isLast -> RoundedCornerShape(12.dp)
                        index == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        isLast -> RoundedCornerShape(
                            bottomStart = 12.dp,
                            bottomEnd = 12.dp,
                        )
                        else -> RoundedCornerShape(0.dp)
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                mirror.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                mirror.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = {
                            RadioButton(
                                selected = selectedMirrorUrl == mirror.url,
                                onClick = { selectMirror(mirror.url) },
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LatencyBadge(
                                    url = mirror.url,
                                    latencyResults = latencyResults,
                                    latencyErrors = latencyErrors,
                                )
                                IconButton(onClick = {
                                    val encoded = "${mirror.name}|${mirror.url}"
                                    val newSet = customMirrors.toMutableSet()
                                    newSet.remove(encoded)
                                    customMirrors = newSet
                                    Settings.fdroidCustomMirrors = newSet
                                    if (selectedMirrorUrl == mirror.url) {
                                        selectMirror("https://f-droid.org/repo")
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.fdroid_mirror_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .clip(shape)
                            .clickable { selectMirror(mirror.url) },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }

                if (showAddForm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = newMirrorName,
                            onValueChange = { newMirrorName = it },
                            label = { Text(stringResource(R.string.fdroid_mirror_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newMirrorUrl,
                            onValueChange = {
                                newMirrorUrl = it
                                urlError = null
                            },
                            label = { Text(stringResource(R.string.fdroid_mirror_url_hint)) },
                            singleLine = true,
                            isError = urlError != null,
                            supportingText = urlError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = {
                                val url = newMirrorUrl.trim().trimEnd('/')
                                if (!URLUtil.isHttpsUrl(url)) {
                                    urlError = invalidUrlMessage
                                    return@Button
                                }
                                val name = newMirrorName.trim().ifEmpty { url }
                                val encoded = "$name|$url"
                                val newSet = customMirrors.toMutableSet()
                                newSet.add(encoded)
                                customMirrors = newSet
                                Settings.fdroidCustomMirrors = newSet
                                newMirrorName = ""
                                newMirrorUrl = ""
                                urlError = null
                                showAddForm = false
                            }) {
                                Text(stringResource(R.string.fdroid_mirror_add_action))
                            }
                        }
                    }
                } else {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.fdroid_mirror_add),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier
                            .clip(
                                if (parsedCustomMirrors.isEmpty()) {
                                    RoundedCornerShape(12.dp)
                                } else {
                                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                },
                            )
                            .clickable { showAddForm = true },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LatencyBadge(
    url: String,
    latencyResults: Map<String, Int>,
    latencyErrors: Map<String, Boolean>,
) {
    val latency = latencyResults[url]
    val failed = latencyErrors[url] == true
    when {
        latency != null -> {
            Text(
                text = stringResource(R.string.fdroid_mirror_latency, latency),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    latency < 100 -> MaterialTheme.colorScheme.primary
                    latency < 500 -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                },
            )
        }
        failed -> {
            Text(
                text = stringResource(R.string.fdroid_mirror_failed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        else -> {}
    }
}
