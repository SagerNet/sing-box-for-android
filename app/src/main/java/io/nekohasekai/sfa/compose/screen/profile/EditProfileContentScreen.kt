package io.nekohasekai.sfa.compose.screen.profile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blacksquircle.ui.language.json.JsonLanguage
import io.nekohasekai.sfa.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun EditProfileContentScreen(
    profileId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    profileName: String = "",
    isReadOnly: Boolean = false,
) {
    val viewModel: EditProfileContentViewModel =
        viewModel(
            factory = EditProfileContentViewModel.Factory(profileId, profileName, isReadOnly),
        )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Handle error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Focus search field when search bar is shown
    LaunchedEffect(uiState.showSearchBar) {
        if (uiState.showSearchBar) {
            searchFocusRequester.requestFocus()
        }
    }

    // Handle save success message
    LaunchedEffect(uiState.showSaveSuccessMessage) {
        if (uiState.showSaveSuccessMessage) {
            Toast.makeText(
                context,
                context.getString(R.string.success_configuration_saved),
                Toast.LENGTH_SHORT,
            ).show()
            viewModel.clearSaveSuccessMessage()
        }
    }

    // Handle back press when there are unsaved changes (not in read-only mode)
    BackHandler(enabled = uiState.hasUnsavedChanges && !uiState.isReadOnly) {
        showUnsavedChangesDialog = true
    }

    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        // Support both Ctrl (Windows/Linux) and Cmd (macOS)
                        val modifierPressed = event.isCtrlPressed || event.isMetaPressed

                        when {
                            // Ctrl/Cmd+Z - Undo
                            modifierPressed && event.key == Key.Z && !event.isShiftPressed && !uiState.isReadOnly -> {
                                viewModel.undo()
                                true
                            }
                            // Ctrl/Cmd+Shift+Z or Ctrl/Cmd+Y - Redo
                            (
                                modifierPressed && event.isShiftPressed && event.key == Key.Z ||
                                    modifierPressed && event.key == Key.Y
                            ) && !uiState.isReadOnly -> {
                                viewModel.redo()
                                true
                            }
                            // Ctrl/Cmd+S - Save
                            modifierPressed && event.key == Key.S && !uiState.isReadOnly -> {
                                if (uiState.hasUnsavedChanges && !uiState.isLoading) {
                                    viewModel.saveConfiguration()
                                }
                                true
                            }
                            // Ctrl/Cmd+F - Search
                            modifierPressed && event.key == Key.F -> {
                                viewModel.toggleSearchBar()
                                true
                            }
                            // Ctrl/Cmd+A - Select All
                            modifierPressed && event.key == Key.A -> {
                                viewModel.selectAll()
                                true
                            }
                            // Ctrl/Cmd+X - Cut (only in edit mode)
                            modifierPressed && event.key == Key.X && !uiState.isReadOnly -> {
                                viewModel.cut()
                                true
                            }
                            // Ctrl/Cmd+C - Copy
                            modifierPressed && event.key == Key.C -> {
                                viewModel.copy()
                                true
                            }
                            // Ctrl/Cmd+V - Paste (only in edit mode)
                            modifierPressed && event.key == Key.V && !uiState.isReadOnly -> {
                                viewModel.paste()
                                true
                            }
                            // Escape - Close search bar if open
                            event.key == Key.Escape && uiState.showSearchBar -> {
                                viewModel.toggleSearchBar()
                                true
                            }
                            // F3 or Ctrl/Cmd+G - Find next (when search is active)
                            (event.key == Key.F3 || (modifierPressed && event.key == Key.G && !event.isShiftPressed)) &&
                                uiState.searchQuery.isNotEmpty() -> {
                                viewModel.findNext()
                                viewModel.focusEditor()
                                true
                            }
                            // Shift+F3 or Ctrl/Cmd+Shift+G - Find previous (when search is active)
                            (
                                (event.isShiftPressed && event.key == Key.F3) ||
                                    (modifierPressed && event.isShiftPressed && event.key == Key.G)
                            ) &&
                                uiState.searchQuery.isNotEmpty() -> {
                                viewModel.findPrevious()
                                viewModel.focusEditor()
                                true
                            }

                            else -> false
                        }
                    } else {
                        false
                    }
                },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (uiState.isReadOnly) {
                                stringResource(R.string.view_configuration)
                            } else {
                                stringResource(R.string.title_edit_configuration)
                            },
                        )
                        if (uiState.profileName.isNotEmpty()) {
                            Text(
                                text = uiState.profileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.hasUnsavedChanges && !uiState.isReadOnly) {
                                showUnsavedChangesDialog = true
                            } else {
                                onNavigateBack()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    // Search/Collapse button (Ctrl/Cmd+F)
                    IconButton(
                        onClick = { viewModel.toggleSearchBar() },
                    ) {
                        Icon(
                            imageVector = if (uiState.showSearchBar) Icons.Default.ExpandLess else Icons.Default.Search,
                            contentDescription =
                                if (uiState.showSearchBar) {
                                    stringResource(R.string.content_description_collapse_search)
                                } else {
                                    stringResource(R.string.search)
                                },
                            tint =
                                if (uiState.showSearchBar) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }

                    // Save button (only show if not read-only) (Ctrl/Cmd+S)
                    if (!uiState.isReadOnly) {
                        IconButton(
                            onClick = { viewModel.saveConfiguration() },
                            enabled = uiState.hasUnsavedChanges && !uiState.isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = stringResource(R.string.save),
                                tint =
                                    if (uiState.hasUnsavedChanges) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Search bar (appears at top when activated)
            AnimatedVisibility(
                visible = uiState.showSearchBar,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn() + expandVertically(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                            coroutineScope.launch {
                                                // Clear focus from search field first
                                                focusManager.clearFocus()
                                                // Small delay to let UI update
                                                delay(100)
                                                // Then focus editor with current search result selection
                                                viewModel.focusEditorWithCurrentSearchResult()
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    },
                            label = { Text(stringResource(R.string.search)) },
                            placeholder = { Text(stringResource(R.string.search_placeholder)) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text =
                                                if (uiState.searchResultCount > 0) {
                                                    "${uiState.currentSearchIndex}/${uiState.searchResultCount}"
                                                } else {
                                                    "0/0"
                                                },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                        IconButton(
                                            onClick = {
                                                // Focus editor with current selection before clearing search
                                                viewModel.focusEditorWithCurrentSearchResult()
                                                viewModel.updateSearchQuery("")
                                                focusManager.clearFocus()
                                            },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.clear),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            },
                        )

                        // Only show navigation buttons when there are search results
                        if (uiState.searchQuery.isNotEmpty() && uiState.searchResultCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    viewModel.findPrevious()
                                    viewModel.focusEditor()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = stringResource(R.string.previous),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.findNext()
                                    viewModel.focusEditor()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = stringResource(R.string.next),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            // Editor in a Box with floating elements
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
            ) {
                // Editor
                AndroidView(
                    factory = { context ->
                        ManualScrollTextProcessor(context).apply {
                            language = JsonLanguage()
                            setTextSize(14f)
                            setPadding(16, 16, 16, if (uiState.isReadOnly) 16 else 120) // Less padding for read-only
                            typeface = android.graphics.Typeface.MONOSPACE
                            setBackgroundColor(
                                androidx.core.content.ContextCompat.getColor(context, android.R.color.transparent),
                            )
                            // Set up the editor with read-only state - this handles all configuration
                            viewModel.setEditor(this, uiState.isReadOnly)
                        }
                    },
                    update = { textProcessor ->
                        // Re-apply configuration when read-only state changes
                        viewModel.setEditor(textProcessor, uiState.isReadOnly)
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                )

                // Simple loading indicator at the top
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                    )
                }

                // Floating bottom editor bar with error banner (only show if not read-only)
                if (!uiState.isReadOnly) {
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .imePadding(),
                    ) {
                        // Configuration error banner (appears above the symbol bar)
                        AnimatedVisibility(
                            visible = uiState.configurationError != null,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                        ) {
                            Surface(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .padding(bottom = 2.dp),
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = 6.dp,
                                shadowElevation = 4.dp,
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    // Match symbol bar padding
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = uiState.configurationError ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(
                                        onClick = { viewModel.dismissConfigurationError() },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.dismiss),
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Symbol input bar
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 4.dp,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Undo button with text
                                TextButton(
                                    onClick = { viewModel.undo() },
                                    enabled = uiState.canUndo,
                                    modifier = Modifier.padding(end = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Default.Undo,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint =
                                            if (uiState.canUndo) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            },
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.menu_undo),
                                        style = MaterialTheme.typography.labelLarge,
                                        color =
                                            if (uiState.canUndo) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            },
                                    )
                                }

                                // Redo button with text
                                TextButton(
                                    onClick = { viewModel.redo() },
                                    enabled = uiState.canRedo,
                                    modifier = Modifier.padding(end = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Default.Redo,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint =
                                            if (uiState.canRedo) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            },
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.menu_redo),
                                        style = MaterialTheme.typography.labelLarge,
                                        color =
                                            if (uiState.canRedo) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            },
                                    )
                                }

                                // Format button with text
                                TextButton(
                                    onClick = { viewModel.formatConfiguration() },
                                    modifier = Modifier.padding(end = 8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.menu_format),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                VerticalDivider(
                                    modifier =
                                        Modifier
                                            .height(24.dp)
                                            .padding(horizontal = 8.dp),
                                )

                                // Symbols ranked by frequency of use in JSON

                                // Most common - quotes and colon (used for every key-value pair)
                                TextButton(
                                    onClick = { viewModel.insertSymbol("\"") },
                                    modifier =
                                        Modifier
                                            .padding(0.dp)
                                            .height(36.dp)
                                            .width(36.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = "\"",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                TextButton(
                                    onClick = { viewModel.insertSymbol(":") },
                                    modifier =
                                        Modifier
                                            .padding(0.dp)
                                            .height(36.dp)
                                            .width(36.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = ":",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                TextButton(
                                    onClick = { viewModel.insertSymbol(",") },
                                    modifier =
                                        Modifier
                                            .padding(0.dp)
                                            .height(36.dp)
                                            .width(36.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = ",",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Object brackets (very common)
                                TextButton(
                                    onClick = { viewModel.insertSymbol("{") },
                                    modifier =
                                        Modifier
                                            .padding(0.dp)
                                            .height(36.dp)
                                            .width(36.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = "{",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                TextButton(
                                    onClick = { viewModel.insertSymbol("}") },
                                    modifier =
                                        Modifier
                                            .padding(0.dp)
                                            .height(36.dp)
                                            .width(36.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = "}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Array brackets (common)
                                TextButton(
                                    onClick = { viewModel.insertSymbol("[") },
                                    modifier =
                                        Modifier
                                            .padding(0.dp)
                                            .height(36.dp)
                                            .width(36.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = "[",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                TextButton(
                                    onClick = { viewModel.insertSymbol("]") },
                                    modifier =
                                        Modifier
                                            .padding(0.dp)
                                            .height(36.dp)
                                            .width(36.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = "]",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Common values - using same TextButton style for keywords
                                listOf("true", "false").forEach { text ->
                                    TextButton(
                                        onClick = { viewModel.insertSymbol(text) },
                                        modifier =
                                            Modifier
                                                .padding(0.dp)
                                                .height(36.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    ) {
                                        Text(
                                            text = text,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Less common symbols - same TextButton style
                                listOf("-", "_", "/", "\\", "(", ")", "@", "#", "$", "%", "&", "*").forEach { symbol ->
                                    TextButton(
                                        onClick = { viewModel.insertSymbol(symbol) },
                                        modifier =
                                            Modifier
                                                .padding(0.dp)
                                                .height(36.dp)
                                                .width(36.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Text(
                                            text = symbol,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                // End padding for scroll
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                }
            }
        }
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
                    Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUnsavedChangesDialog = false },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Initial loading
    LaunchedEffect(profileId) {
        viewModel.loadConfiguration()
    }
}
