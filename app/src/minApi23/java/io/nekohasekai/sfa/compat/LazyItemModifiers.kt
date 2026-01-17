package io.nekohasekai.sfa.compat

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

fun LazyItemScope.animateItemCompat(placementSpec: FiniteAnimationSpec<IntOffset>): Modifier = Modifier.animateItem(placementSpec = placementSpec)
