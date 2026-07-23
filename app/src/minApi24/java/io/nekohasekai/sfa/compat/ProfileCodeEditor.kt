package io.nekohasekai.sfa.compat

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import com.itsaky.androidide.treesitter.json.TSLanguageJson
import io.github.rosemoe.sora.editor.ts.TsLanguage
import io.github.rosemoe.sora.editor.ts.TsLanguageSpec
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

class ProfileCodeEditor(context: Context) {

    companion object {
        private const val JSON_HIGHLIGHTS = """
(string) @string
(pair key: (string) @string.special.key)
(number) @number
[(null) (true) (false)] @constant.builtin
(escape_sequence) @escape
"""

        private const val JSON_BLOCKS = """
(object) @scope.marked
(array) @scope.marked
"""

        private const val JSON_BRACKETS = """
(object "{" @editor.brackets.open "}" @editor.brackets.close)
(array "[" @editor.brackets.open "]" @editor.brackets.close)
"""

        init {
            System.loadLibrary("android-tree-sitter")
            System.loadLibrary("tree-sitter-json")
        }
    }

    var onTextChanged: (() -> Unit)? = null
    var onSearchResultChanged: ((count: Int, current: Int) -> Unit)? = null

    // The scrollbar rect is a fixed 10dp wide; inset the thumb drawable to slim it down visually
    private val scrollbarThumb =
        GradientDrawable().apply {
            cornerRadius = 2 * context.resources.displayMetrics.density
        }

    private val editor =
        CodeEditor(context).apply {
            typefaceText = Typeface.MONOSPACE
            typefaceLineNumber = Typeface.MONOSPACE
            setTextSize(14f)
            isLineNumberEnabled = true
            isWordwrap = true
            setVerticalScrollbarThumbDrawable(
                InsetDrawable(scrollbarThumb, (6 * context.resources.displayMetrics.density).toInt(), 0, 0, 0),
            )
            setEditorLanguage(
                TsLanguage(TsLanguageSpec(TSLanguageJson.getInstance(), JSON_HIGHLIGHTS, JSON_BLOCKS, JSON_BRACKETS)) {
                    TextStyle.makeStyle(EditorColorScheme.LITERAL) applyTo "string"
                    TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_NAME) applyTo "string.special.key"
                    TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_VALUE) applyTo "number"
                    TextStyle.makeStyle(EditorColorScheme.KEYWORD, true) applyTo "constant.builtin"
                    TextStyle.makeStyle(EditorColorScheme.OPERATOR) applyTo "escape"
                },
            )
            subscribeEvent(ContentChangeEvent::class.java) { _, _ -> onTextChanged?.invoke() }
            subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
                if (searcher.hasQuery() && searcher.matchedPositionCount > 0 && !searcher.isMatchedPositionSelected) {
                    searcher.gotoNext()
                }
                notifySearchResult()
            }
        }

    val view: View
        get() = editor

    fun setText(content: String) = editor.setText(content)

    fun getText(): String = editor.text.toString()

    fun canUndo(): Boolean = editor.canUndo()

    fun canRedo(): Boolean = editor.canRedo()

    fun undo() = editor.undo()

    fun redo() = editor.redo()

    fun setReadOnly(readOnly: Boolean) {
        editor.isEditable = !readOnly
    }

    fun search(query: String) {
        if (query.isEmpty()) {
            editor.searcher.stopSearch()
            onSearchResultChanged?.invoke(0, 0)
        } else {
            editor.searcher.search(query, EditorSearcher.SearchOptions(true, false))
        }
    }

    fun findNext() {
        if (editor.searcher.hasQuery()) {
            editor.searcher.gotoNext()
            notifySearchResult()
        }
    }

    fun findPrevious() {
        if (editor.searcher.hasQuery()) {
            editor.searcher.gotoPrevious()
            notifySearchResult()
        }
    }

    fun focus() {
        editor.requestFocus()
    }

    fun focusWithCurrentSearchResult() {
        editor.requestFocus()
    }

    fun insertSymbol(symbol: String) {
        editor.commitText(symbol)
    }

    fun selectAll() = editor.selectAll()

    fun cut() {
        if (editor.cursor.isSelected) {
            editor.cutText()
        }
    }

    fun copy() {
        if (editor.cursor.isSelected) {
            editor.copyText()
        }
    }

    fun paste() = editor.pasteText()

    fun applyColors(colors: ProfileEditorColors) {
        val scheme = editor.colorScheme
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, colors.background)
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, colors.foreground)
        scheme.setColor(EditorColorScheme.LINE_NUMBER, colors.lineNumber)
        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, colors.foreground)
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, colors.lineNumberBackground)
        scheme.setColor(EditorColorScheme.LINE_DIVIDER, colors.lineNumberBackground)
        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, colors.selectionBackground)
        scheme.setColor(EditorColorScheme.CURRENT_LINE, colors.currentLineBackground)
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, colors.cursor)
        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, colors.cursor)
        scheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, colors.matchedTextBackground)
        scheme.setColor(EditorColorScheme.BLOCK_LINE, colors.lineNumber and 0x00FFFFFF or 0x30000000)
        scheme.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, colors.cursor)
        scheme.setColor(EditorColorScheme.SCROLL_BAR_TRACK, 0)
        scrollbarThumb.setColor(colors.foreground and 0x00FFFFFF or 0x50000000)
        scheme.setColor(EditorColorScheme.ATTRIBUTE_NAME, colors.key)
        scheme.setColor(EditorColorScheme.LITERAL, colors.string)
        scheme.setColor(EditorColorScheme.ATTRIBUTE_VALUE, colors.number)
        scheme.setColor(EditorColorScheme.KEYWORD, colors.literal)
        scheme.setColor(EditorColorScheme.OPERATOR, colors.string)
    }

    fun release() {
        onTextChanged = null
        onSearchResultChanged = null
        editor.release()
    }

    private fun notifySearchResult() {
        val searcher = editor.searcher
        val count = if (searcher.hasQuery()) searcher.matchedPositionCount else 0
        val current = if (count > 0) searcher.currentMatchedPositionIndex + 1 else 0
        onSearchResultChanged?.invoke(count, current)
    }
}
