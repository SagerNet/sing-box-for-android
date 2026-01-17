package io.nekohasekai.sfa.compat

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

@Suppress("UNUSED_PARAMETER")
fun LazyItemScope.animateItemCompat(placementSpec: FiniteAnimationSpec<IntOffset>): Modifier = Modifier
