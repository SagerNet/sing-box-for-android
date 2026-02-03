package io.nekohasekai.sfa.compat

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

object WindowSizeClassCompat {
    const val WIDTH_DP_MEDIUM_LOWER_BOUND = 600
    const val WIDTH_DP_EXPANDED_LOWER_BOUND = 840
}

fun WindowSizeClass.isWidthAtLeastBreakpointCompat(breakpointDp: Int): Boolean = when {
    breakpointDp <= WindowSizeClassCompat.WIDTH_DP_MEDIUM_LOWER_BOUND ->
        windowWidthSizeClass != WindowWidthSizeClass.COMPACT
    breakpointDp <= WindowSizeClassCompat.WIDTH_DP_EXPANDED_LOWER_BOUND ->
        windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    else -> windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
}
