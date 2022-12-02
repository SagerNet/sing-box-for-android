package io.nekohasekai.sfa.ktx

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.material.elevation.SurfaceColors

fun Context.launchCustomTab(link: String) {
    val color = SurfaceColors.SURFACE_2.getColor(this)
    CustomTabsIntent.Builder().apply {
        setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
        setColorSchemeParams(
            CustomTabsIntent.COLOR_SCHEME_LIGHT,
            CustomTabColorSchemeParams.Builder().apply {
                setToolbarColor(color)
            }.build()
        )
        setColorSchemeParams(
            CustomTabsIntent.COLOR_SCHEME_DARK,
            CustomTabColorSchemeParams.Builder().apply {
                setToolbarColor(color)
            }.build()
        )
    }.build().launchUrl(this, Uri.parse(link))
}