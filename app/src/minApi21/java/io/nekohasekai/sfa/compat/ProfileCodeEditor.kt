package io.nekohasekai.sfa.compat

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.widget.addTextChangedListener
import com.blacksquircle.ui.language.json.JsonLanguage

class ProfileCodeEditor(context: Context) {

    var onTextChanged: (() -> Unit)? = null
    var onSearchResultChanged: ((count: Int, current: Int) -> Unit)? = null

    private val editor =
        ManualScrollTextProcessor(context).apply {
            language = JsonLanguage()
            textSize = 14f
            setPadding(16, 16, 16, 16)
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.TRANSPARENT)
            isEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
            setTextIsSelectable(true)
            setSingleLine(false)
            maxLines = Integer.MAX_VALUE
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isCursorVisible = true
            isLongClickable = true
        }

    private val textWatcher: TextWatcher = editor.addTextChangedListener { onTextChanged?.invoke() }

    private var readOnly = false
    private var searchQuery = ""
    private var searchResultCount = 0

    private val readOnlyKeyListener = View.OnKeyListener { _, _, _ -> true }

    private val readOnlySelectionCallback =
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = true

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menu?.let { m ->
                    m.removeItem(android.R.id.cut)
                    m.removeItem(android.R.id.paste)
                    m.removeItem(android.R.id.pasteAsPlainText)
                    m.removeItem(android.R.id.replaceText)
                    m.removeItem(android.R.id.undo)
                    m.removeItem(android.R.id.redo)
                    m.removeItem(android.R.id.autofill)
                    m.removeItem(android.R.id.textAssist)
                }
                return true
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

            override fun onDestroyActionMode(mode: ActionMode?) {}
        }

    val view: View
        get() = editor

    fun setText(content: String) {
        editor.resumeAutoScroll()
        editor.setTextContent(content)
    }

    fun getText(): String = editor.text?.toString().orEmpty()

    fun canUndo(): Boolean = editor.canUndo()

    fun canRedo(): Boolean = editor.canRedo()

    fun undo() {
        if (editor.canUndo()) {
            editor.resumeAutoScroll()
            editor.undo()
        }
    }

    fun redo() {
        if (editor.canRedo()) {
            editor.resumeAutoScroll()
            editor.redo()
        }
    }

    fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
        if (readOnly) {
            editor.setOnKeyListener(readOnlyKeyListener)
            editor.customSelectionActionModeCallback = readOnlySelectionCallback
        } else {
            editor.setOnKeyListener(null)
            editor.customSelectionActionModeCallback = null
        }
    }

    fun search(query: String) {
        searchQuery = query
        if (query.isEmpty()) {
            searchResultCount = 0
            onSearchResultChanged?.invoke(0, 0)
            return
        }
        val text = getText()
        var count = 0
        val first = text.indexOf(query, ignoreCase = true)
        var index = first
        while (index != -1) {
            count++
            index = text.indexOf(query, index + 1, ignoreCase = true)
        }
        searchResultCount = count
        if (count > 0) {
            editor.resumeAutoScroll()
            editor.setSelection(first, first + query.length)
        }
        onSearchResultChanged?.invoke(count, if (count > 0) 1 else 0)
    }

    fun findNext() {
        if (searchResultCount == 0 || searchQuery.isEmpty()) return
        val text = getText()
        var next = text.indexOf(searchQuery, editor.selectionEnd, ignoreCase = true)
        if (next == -1) {
            next = text.indexOf(searchQuery, 0, ignoreCase = true)
        }
        if (next != -1) {
            editor.resumeAutoScroll()
            editor.setSelection(next, next + searchQuery.length)
            onSearchResultChanged?.invoke(searchResultCount, matchIndexAt(text, next))
        }
    }

    fun findPrevious() {
        if (searchResultCount == 0 || searchQuery.isEmpty()) return
        val text = getText()
        var previous = text.lastIndexOf(searchQuery, editor.selectionStart - 1, ignoreCase = true)
        if (previous == -1) {
            previous = text.lastIndexOf(searchQuery, ignoreCase = true)
        }
        if (previous != -1) {
            editor.resumeAutoScroll()
            editor.setSelection(previous, previous + searchQuery.length)
            onSearchResultChanged?.invoke(searchResultCount, matchIndexAt(text, previous))
        }
    }

    fun focus() {
        editor.isFocusable = true
        editor.isFocusableInTouchMode = true
        editor.resumeAutoScroll()
        editor.requestFocus()
        if (searchQuery.isEmpty() || searchResultCount == 0) {
            if (!readOnly) {
                editor.setSelection(editor.selectionEnd)
            }
        }
    }

    fun focusWithCurrentSearchResult() {
        editor.isFocusable = true
        editor.isFocusableInTouchMode = true
        editor.resumeAutoScroll()
        if (searchQuery.isNotEmpty() && searchResultCount > 0) {
            val text = getText()
            var matchIndex = text.indexOf(searchQuery, editor.selectionStart, ignoreCase = true)
            if (matchIndex == -1 && editor.selectionStart > 0) {
                matchIndex = text.indexOf(searchQuery, 0, ignoreCase = true)
            }
            if (matchIndex != -1) {
                editor.setSelection(matchIndex, matchIndex + searchQuery.length)
            }
        }
        editor.requestFocus()
    }

    fun insertSymbol(symbol: String) {
        val text = editor.text ?: return
        val rawStart = editor.selectionStart
        val rawEnd = editor.selectionEnd
        // selectionStart/End can be reversed (backward drag-selection) or -1 (no cursor)
        val start: Int
        val end: Int
        if (rawStart < 0 || rawEnd < 0) {
            start = text.length
            end = text.length
        } else {
            start = minOf(rawStart, rawEnd).coerceIn(0, text.length)
            end = maxOf(rawStart, rawEnd).coerceIn(0, text.length)
        }

        val newText =
            StringBuilder(text)
                .replace(start, end, symbol)
                .toString()

        editor.resumeAutoScroll()
        editor.setTextContent(newText)
        editor.setSelection(start + symbol.length)
    }

    fun selectAll() {
        val text = getText()
        if (text.isNotEmpty()) {
            editor.resumeAutoScroll()
            editor.setSelection(0, text.length)
            editor.requestFocus()
        }
    }

    fun cut() {
        if (editor.hasSelection()) {
            editor.onTextContextMenuItem(android.R.id.cut)
        }
    }

    fun copy() {
        if (editor.hasSelection()) {
            editor.onTextContextMenuItem(android.R.id.copy)
        }
    }

    fun paste() {
        editor.onTextContextMenuItem(android.R.id.paste)
    }

    fun applyColors(colors: ProfileEditorColors) {
        // EditorKit keeps its built-in theme on the legacy flavor
    }

    fun release() {
        editor.removeTextChangedListener(textWatcher)
        onTextChanged = null
        onSearchResultChanged = null
    }

    private fun matchIndexAt(text: String, matchStart: Int): Int {
        var index = text.indexOf(searchQuery, ignoreCase = true)
        var counter = 0
        while (index != -1) {
            counter++
            if (index == matchStart) return counter
            index = text.indexOf(searchQuery, index + 1, ignoreCase = true)
        }
        return 0
    }
}
