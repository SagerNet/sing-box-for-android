package io.nekohasekai.sfa.compat

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class GhosttyConfigLanguage : EmptyLanguage() {

    private val analyzeManager = object : SimpleAnalyzeManager<Any>() {
        override fun analyze(text: StringBuilder, delegate: Delegate<Any>): Styles {
            val builder = MappedSpans.Builder()
            val lines = text.lineSequence().toList()
            lines.forEachIndexed { line, content ->
                val start = content.indexOfFirst { !it.isWhitespace() }
                val separator = if (start < 0) -1 else content.indexOf('=')
                when {
                    start < 0 -> builder.addIfNeeded(line, 0, NORMAL)
                    content[start] == '#' -> builder.addIfNeeded(line, 0, COMMENT)
                    separator < 0 -> builder.addIfNeeded(line, 0, NORMAL)
                    else -> {
                        builder.addIfNeeded(line, 0, KEY)
                        builder.addIfNeeded(line, separator, OPERATOR)
                        builder.addIfNeeded(line, separator + 1, VALUE)
                    }
                }
            }
            builder.determine(lines.size - 1)
            return Styles(builder.build())
        }
    }

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    private companion object {
        val NORMAL = TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
        val COMMENT = TextStyle.makeStyle(EditorColorScheme.COMMENT)
        val KEY = TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_NAME)
        val OPERATOR = TextStyle.makeStyle(EditorColorScheme.OPERATOR)
        val VALUE = TextStyle.makeStyle(EditorColorScheme.LITERAL)
    }
}
