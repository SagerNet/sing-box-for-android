package io.nekohasekai.sfa.compose.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object AnsiColorUtils {
    private val ansiRegex = Regex("\u001B\\[[;\\d]*m")

    private val logRed = Color(0xFFFF2158)
    private val logGreen = Color(0xFF2ECC71)
    private val logYellow = Color(0xFFE5E500)
    private val logBlue = Color(0xFF3498DB)
    private val logPurple = Color(0xFF9B59B6)
    private val logBlueLight = Color(0xFF5DADE2)
    private val logWhite = Color(0xFFECF0F1)

    fun ansiToAnnotatedString(text: String): AnnotatedString {
        val cleanText = stripAnsi(text)
        val matches = ansiRegex.findAll(text).toList()

        if (matches.isEmpty()) {
            return AnnotatedString(cleanText)
        }

        return buildAnnotatedString {
            append(cleanText)

            var currentStyle: SpanStyle? = null
            var currentStart = 0
            var offset = 0

            matches.forEach { match ->
                val code = match.value
                val codeStart = match.range.first - offset
                val decoration = parseAnsiCode(code)

                if (decoration == null) {
                    // Reset code
                    if (currentStyle != null && currentStart < codeStart) {
                        addStyle(currentStyle!!, currentStart, codeStart)
                    }
                    currentStyle = null
                    currentStart = codeStart
                } else {
                    // Apply previous style if exists
                    if (currentStyle != null && currentStart < codeStart) {
                        addStyle(currentStyle!!, currentStart, codeStart)
                    }
                    currentStyle = decoration
                    currentStart = codeStart
                }

                offset += code.length
            }

            // Apply remaining style
            if (currentStyle != null && currentStart < cleanText.length) {
                addStyle(currentStyle!!, currentStart, cleanText.length)
            }
        }
    }

    fun stripAnsi(text: String): String = text.replace(ansiRegex, "")

    private fun parseAnsiCode(code: String): SpanStyle? {
        val colorCodes = code.substringAfter('[').substringBefore('m').split(';')

        var color: Color? = null
        var fontWeight: FontWeight? = null
        var fontStyle: FontStyle? = null
        var textDecoration: TextDecoration? = null

        colorCodes.forEach { codeStr ->
            when (codeStr) {
                "0" -> return null // Reset
                "1" -> fontWeight = FontWeight.Bold
                "3" -> fontStyle = FontStyle.Italic
                "4" -> textDecoration = TextDecoration.Underline
                "30" -> color = Color.Black
                "31" -> color = logRed
                "32" -> color = logGreen
                "33" -> color = logYellow
                "34" -> color = logBlue
                "35" -> color = logPurple
                "36" -> color = logBlueLight
                "37" -> color = logWhite
                else -> {
                    val codeInt = codeStr.toIntOrNull()
                    if (codeInt != null && codeInt in 38..125) {
                        val adjustedCode = codeInt % 125
                        val row = adjustedCode / 36
                        val column = adjustedCode % 36
                        color =
                            Color(
                                red = row * 51,
                                green = (column / 6) * 51,
                                blue = (column % 6) * 51,
                            )
                    }
                }
            }
        }

        return if (color != null || fontWeight != null || fontStyle != null || textDecoration != null) {
            SpanStyle(
                color = color ?: Color.Unspecified,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration,
            )
        } else {
            null
        }
    }
}
