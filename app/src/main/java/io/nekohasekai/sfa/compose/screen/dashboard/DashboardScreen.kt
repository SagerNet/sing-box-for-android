package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.util.saveQRCodeToGallery
import io.nekohasekai.sfa.compose.util.shareQRCodeImage
import io.nekohasekai.sfa.constant.Status
import kotlinx.coroutines.launch

data class CardRenderItem(
    val cards: List<CardGroup>,
    val isRow: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    serviceStatus: Status = Status.Stopped,
    viewModel: DashboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Update service status in ViewModel
    LaunchedEffect(serviceStatus) {
        viewModel.updateServiceStatus(serviceStatus)
    }

    // Events are now handled globally in ComposeActivity via GlobalEventBus

    // Show deprecated notes dialog
    if (uiState.showDeprecatedDialog && uiState.deprecatedNotes.isNotEmpty()) {
        val note = uiState.deprecatedNotes.first()
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.service_error_title_deprecated_warning)) },
            text = { Text(note.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDeprecatedNote() }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton =
                if (!note.migrationLink.isNullOrBlank()) {
                    {
                        TextButton(onClick = {
                            viewModel.sendGlobalEvent(UiEvent.OpenUrl(note.migrationLink))
                            viewModel.dismissDeprecatedNote()
                        }) {
                            Text(stringResource(R.string.service_error_deprecated_warning_documentation))
                        }
                    }
                } else {
                    null
                },
        )
    }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Show dashboard settings bottom sheet
    if (uiState.showCardSettingsDialog) {
        DashboardSettingsBottomSheet(
            sheetState = sheetState,
            visibleCards = uiState.visibleCards,
            cardOrder = uiState.cardOrder,
            onToggleCard = viewModel::toggleCardVisibility,
            onReorderCards = viewModel::reorderCards,
            onResetOrder = viewModel::resetCardOrder,
            onDismiss = {
                scope.launch {
                    sheetState.hide()
                    viewModel.closeCardSettingsDialog()
                }
            },
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding =
                PaddingValues(
                    bottom = 88.dp, // Increased to accommodate FAB (56dp height + 32dp padding)
                ),
        ) {
            // Dynamic dashboard cards
            // Show cards when service is running OR if it's the Profiles card (always available)
            val serviceRunning = uiState.isStatusVisible

            // Filter cards based on availability
            val actuallyVisibleCards =
                uiState.visibleCards.filter { cardGroup ->
                    when (cardGroup) {
                        CardGroup.Profiles -> true // Profiles card is always available
                        else -> serviceRunning && isCardAvailableWhenServiceRunning(cardGroup, uiState)
                    }
                }.toSet()

            // Process cards to group half-width cards together
            val cardRenderItems =
                processCardsForRendering(
                    cardOrder = uiState.cardOrder,
                    visibleCards = actuallyVisibleCards,
                    cardWidths = uiState.cardWidths,
                )

            items(cardRenderItems) { renderItem ->
                if (renderItem.isRow && renderItem.cards.size >= 2) {
                    // Render two half-width cards in a row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        renderItem.cards.forEach { cardGroup ->
                            DashboardCardRenderer(
                                cardGroup = cardGroup,
                                cardWidth =
                                    uiState.cardWidths[cardGroup]
                                        ?: CardWidth.Full,
                                uiState = uiState,
                                onClashModeSelected = viewModel::selectClashMode,
                                onSystemProxyToggle = viewModel::toggleSystemProxy,
                                // Profile card specific props
                                profiles = uiState.profiles,
                                selectedProfileId = uiState.selectedProfileId,
                                isLoading = uiState.isLoading,
                                showAddProfileSheet = uiState.showAddProfileSheet,
                                updatingProfileId = uiState.updatingProfileId,
                                updatedProfileId = uiState.updatedProfileId,
                                onProfileSelected = viewModel::selectProfile,
                                onProfileEdit = viewModel::editProfile,
                                onProfileDelete = viewModel::deleteProfile,
                                onProfileShare = viewModel::shareProfile,
                                onProfileShareURL = viewModel::shareProfileURL,
                                onProfileUpdate = viewModel::updateProfile,
                                onProfileMove = viewModel::moveProfile,
                                onShowAddProfileSheet = viewModel::showAddProfileSheet,
                                onHideAddProfileSheet = viewModel::hideAddProfileSheet,
                                shareQRCodeImage = { bitmap, name ->
                                    scope.launch {
                                        shareQRCodeImage(context, bitmap, name)
                                    }
                                },
                                saveQRCodeToGallery = { bitmap, name ->
                                    scope.launch {
                                        saveQRCodeToGallery(context, bitmap, name)
                                    }
                                },
                                commandClient = viewModel.commandClient,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    // Render single card (full-width or single half-width)
                    renderItem.cards.forEach { cardGroup ->
                        DashboardCardRenderer(
                            cardGroup = cardGroup,
                            cardWidth =
                                uiState.cardWidths[cardGroup]
                                    ?: CardWidth.Full,
                            uiState = uiState,
                            serviceStatus = serviceStatus,
                            onClashModeSelected = viewModel::selectClashMode,
                            onSystemProxyToggle = viewModel::toggleSystemProxy,
                            // Profile card specific props
                            profiles = uiState.profiles,
                            selectedProfileId = uiState.selectedProfileId,
                            isLoading = uiState.isLoading,
                            showAddProfileSheet = uiState.showAddProfileSheet,
                            updatingProfileId = uiState.updatingProfileId,
                            updatedProfileId = uiState.updatedProfileId,
                            onProfileSelected = viewModel::selectProfile,
                            onProfileEdit = viewModel::editProfile,
                            onProfileDelete = viewModel::deleteProfile,
                            onProfileShare = viewModel::shareProfile,
                            onProfileShareURL = viewModel::shareProfileURL,
                            onProfileUpdate = viewModel::updateProfile,
                            onProfileMove = viewModel::moveProfile,
                            onShowAddProfileSheet = viewModel::showAddProfileSheet,
                            onHideAddProfileSheet = viewModel::hideAddProfileSheet,
                            shareQRCodeImage = { bitmap, name ->
                                scope.launch {
                                    shareQRCodeImage(context, bitmap, name)
                                }
                            },
                            saveQRCodeToGallery = { bitmap, name ->
                                scope.launch {
                                    saveQRCodeToGallery(context, bitmap, name)
                                }
                            },
                            commandClient = viewModel.commandClient,
                        )
                    }
                }
            }
        }

        // FAB
        AnimatedVisibility(
            visible = uiState.serviceStatus != Status.Stopping,
            enter = androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.scaleOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
        ) {
            ServiceControlFAB(
                status = uiState.serviceStatus,
                onToggle = { viewModel.toggleService() },
            )
        }
    }
}

@Composable
fun ServiceControlFAB(
    status: Status,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onToggle,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
    ) {
        Icon(
            imageVector =
                when (status) {
                    Status.Started, Status.Starting -> Icons.Default.Stop
                    else -> Icons.Default.PlayArrow
                },
            contentDescription =
                when (status) {
                    Status.Started, Status.Starting -> stringResource(R.string.stop)
                    else -> stringResource(R.string.action_start)
                },
        )
    }
}

/**
 * Process cards for rendering, grouping consecutive half-width cards into rows
 */
fun processCardsForRendering(
    cardOrder: List<CardGroup>,
    visibleCards: Set<CardGroup>,
    cardWidths: Map<CardGroup, CardWidth>,
): List<CardRenderItem> {
    val renderItems = mutableListOf<CardRenderItem>()
    val visibleOrderedCards = cardOrder.filter { visibleCards.contains(it) }

    var i = 0
    while (i < visibleOrderedCards.size) {
        val currentCard = visibleOrderedCards[i]
        val currentWidth = cardWidths[currentCard] ?: CardWidth.Full

        if (currentWidth == CardWidth.Half) {
            // Check if next card is also half-width
            if (i + 1 < visibleOrderedCards.size) {
                val nextCard = visibleOrderedCards[i + 1]
                val nextWidth = cardWidths[nextCard] ?: CardWidth.Full

                if (nextWidth == CardWidth.Half) {
                    // Group two half-width cards together
                    renderItems.add(
                        CardRenderItem(
                            cards = listOf(currentCard, nextCard),
                            isRow = true,
                        ),
                    )
                    i += 2
                    continue
                }
            }
            // Single half-width card
            renderItems.add(
                CardRenderItem(
                    cards = listOf(currentCard),
                    isRow = false,
                ),
            )
        } else {
            // Full-width card
            renderItems.add(
                CardRenderItem(
                    cards = listOf(currentCard),
                    isRow = false,
                ),
            )
        }
        i++
    }

    return renderItems
}

/**
 * Determine if a service-dependent card has data available to display.
 * This function is only relevant when the service is running.
 * Note: Profiles card is always available and should not use this function.
 */
fun isCardAvailableWhenServiceRunning(
    cardGroup: CardGroup,
    uiState: DashboardUiState,
): Boolean {
    return when (cardGroup) {
        CardGroup.ClashMode -> uiState.clashModeVisible
        CardGroup.UploadTraffic -> uiState.trafficVisible
        CardGroup.DownloadTraffic -> uiState.trafficVisible
        CardGroup.Debug -> true // Debug info is always available when service is running
        CardGroup.Connections -> uiState.trafficVisible
        CardGroup.SystemProxy -> uiState.systemProxyVisible
        CardGroup.Profiles -> true // This shouldn't be called for Profiles, but return true for safety
        CardGroup.Groups -> uiState.hasGroups // Groups card available when groups exist
    }
}
