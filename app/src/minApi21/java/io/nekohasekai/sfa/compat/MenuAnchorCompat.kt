package io.nekohasekai.sfa.compat

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.MenuAnchorType
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
fun ExposedDropdownMenuBoxScope.menuAnchorCompat(enabled: Boolean): Modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
