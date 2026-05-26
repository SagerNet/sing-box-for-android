package io.nekohasekai.sfa.terminal

import android.content.Context
import android.graphics.Color
import com.termux.terminal.TerminalColorScheme
import com.termux.terminal.TerminalColors
import java.util.Properties

object TerminalColorSchemeLoader {

    fun listSchemes(context: Context): List<String> {
        val files = context.assets.list("termux-colors") ?: return emptyList()
        return files
            .filter { it.endsWith(".properties") }
            .map { it.removeSuffix(".properties") }
            .sorted()
    }

    fun listSchemes(context: Context, isDark: Boolean): List<String> {
        return listSchemes(context).filter { name ->
            val props = loadScheme(context, name) ?: return@filter false
            val background = props.getProperty("background") ?: return@filter isDark
            val luminance = backgroundLuminance(background)
            if (isDark) luminance <= 128 else luminance > 128
        }
    }

    fun loadScheme(context: Context, name: String): Properties? = try {
        val props = Properties()
        context.assets.open("termux-colors/$name.properties").use { props.load(it) }
        props
    } catch (_: Exception) {
        null
    }

    fun applyScheme(context: Context, name: String) {
        val props = loadScheme(context, name) ?: return
        TerminalColors.COLOR_SCHEME.updateWith(props)
    }

    fun applySchemeToEmulator(emulator: com.termux.terminal.TerminalEmulator, context: Context, name: String) {
        applyScheme(context, name)
        emulator.mColors.reset()
    }

    private fun backgroundLuminance(hex: String): Int = try {
        val color = Color.parseColor(hex)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    } catch (_: Exception) {
        0
    }
}
