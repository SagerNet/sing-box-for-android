package io.nekohasekai.sfa.terminal

import io.github.sagernet.libghostty.GhosttyColors
import io.github.sagernet.libghostty.GhosttyTheme

data class GhosttyCustomConfig(
    val theme: GhosttyTheme,
    val fontFamily: String,
) {
    companion object {
        fun parse(text: String): GhosttyCustomConfig {
            var foreground: Int? = null
            var background: Int? = null
            var cursorColor: Int? = null
            var selectionBackground: Int? = null
            var selectionForeground: Int? = null
            var fontFamily = ""
            val palette = arrayOfNulls<Int>(256)
            text.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val fields = line.split('=', limit = 2)
                if (fields.size < 2) return@forEach
                val key = fields[0].trim()
                val value = fields[1].trim()
                when (key) {
                    "palette" -> {
                        val entry = value.split('=', limit = 2)
                        val index = if (entry.size < 2) null else entry[0].trim().toIntOrNull()
                        if (index != null && index in 0..255) {
                            palette[index] = GhosttyColors.parse(entry[1].trim())
                        }
                    }
                    "foreground" -> foreground = GhosttyColors.parse(value)
                    "background" -> background = GhosttyColors.parse(value)
                    "cursor-color" -> cursorColor = GhosttyColors.parse(value)
                    "selection-background" -> selectionBackground = GhosttyColors.parse(value)
                    "selection-foreground" -> selectionForeground = GhosttyColors.parse(value)
                    "font-family" -> fontFamily = value.trim('"')
                }
            }
            return GhosttyCustomConfig(
                theme = GhosttyTheme(
                    foreground,
                    background,
                    cursorColor,
                    selectionBackground,
                    selectionForeground,
                    palette.toList(),
                ),
                fontFamily = fontFamily,
            )
        }
    }
}
