package io.nekohasekai.sfa.compat

import android.os.Bundle
import io.github.rosemoe.sora.editor.ts.TsLanguage
import io.github.rosemoe.sora.editor.ts.TsLanguageSpec
import io.github.rosemoe.sora.editor.ts.TsThemeBuilder
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionIconDrawer
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentLine
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion

const val JSON_INDENT_WIDTH = 2

class ConfigJsonLanguage(
    languageSpec: TsLanguageSpec,
    themeDescription: TsThemeBuilder.() -> Unit,
) : TsLanguage(languageSpec, false, themeDescription) {

    @Volatile
    private var forceNextCompletion = false

    @Volatile
    private var newlineHandlerCommitPending = false

    private val bracePairGuard =
        object : SymbolPairMatch.SymbolPair.SymbolPairEx {
            override fun shouldReplace(editor: CodeEditor, currentLine: ContentLine, leftColumn: Int): Boolean = !insideStringLiteral(currentLine, leftColumn) &&
                JsonDocumentScanner.balance(editor.text.toString()).orphanClosingBraces == 0
        }

    private val bracketPairGuard =
        object : SymbolPairMatch.SymbolPair.SymbolPairEx {
            override fun shouldReplace(editor: CodeEditor, currentLine: ContentLine, leftColumn: Int): Boolean = !insideStringLiteral(currentLine, leftColumn) &&
                JsonDocumentScanner.balance(editor.text.toString()).orphanClosingBrackets == 0
        }

    private val quotePairGuard =
        object : SymbolPairMatch.SymbolPair.SymbolPairEx {
            override fun shouldReplace(editor: CodeEditor, currentLine: ContentLine, leftColumn: Int): Boolean = !insideStringLiteral(currentLine, leftColumn) &&
                JsonDocumentScanner.balance(editor.text.toString()).quoteCount % 2 == 0
        }

    private val objectPairWithComma =
        object : SymbolPairMatch.SymbolPair.SymbolPairEx {
            override fun shouldReplace(editor: CodeEditor, currentLine: ContentLine, leftColumn: Int): Boolean {
                if (insideStringLiteral(currentLine, leftColumn)) return false
                val text = editor.text.toString()
                if (JsonDocumentScanner.balance(text).orphanClosingBraces > 0) return false
                val cursorIndex = editor.cursor.left().index
                val context = JsonDocumentScanner.scan(text, cursorIndex) ?: return false
                if (context.quoteOpen || !context.enclosingFrame.isArray) return false
                return nextCharBeginsArrayValue(text, cursorIndex)
            }
        }

    private val symbolPairs =
        SymbolPairMatch().apply {
            putPair("{", SymbolPairMatch.SymbolPair("{", "},", objectPairWithComma))
            putPair("{", SymbolPairMatch.SymbolPair("{", "}", bracePairGuard))
            putPair("[", SymbolPairMatch.SymbolPair("[", "]", bracketPairGuard))
            putPair("\"", SymbolPairMatch.SymbolPair("\"", "\"", quotePairGuard))
        }

    private val newlineHandlers = arrayOf<NewlineHandler>(BracketNewlineHandler())

    override fun getSymbolPairs(): SymbolPairMatch = symbolPairs

    override fun getNewlineHandlers(): Array<NewlineHandler> = newlineHandlers

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        val beforeCursor = content.getLine(line).substring(0, column).trimEnd()
        return if (beforeCursor.endsWith("{") || beforeCursor.endsWith("[")) JSON_INDENT_WIDTH else 0
    }

    fun retriggerCompletion(editor: CodeEditor) {
        editor.postInLifecycle {
            forceNextCompletion = true
            editor.getComponent(EditorAutoCompletion::class.java).requireCompletion()
        }
    }

    fun consumeNewlineHandlerCommit(): Boolean {
        val pending = newlineHandlerCommitPending
        newlineHandlerCommitPending = false
        return pending
    }

    fun autoShowCompletionAfterNewline(editor: CodeEditor) {
        editor.postInLifecycle {
            if (!editor.isEditable || editor.cursor.isSelected) return@postInLifecycle
            val cursorPosition = editor.cursor.left()
            if (editor.text.getLineString(cursorPosition.line).isNotBlank()) return@postInLifecycle
            val schema = ConfigSchema.peek() ?: return@postInLifecycle
            val context = JsonDocumentScanner.scan(editor.text.toString(), cursorPosition.index) ?: return@postInLifecycle
            if (context.quoteOpen || context.prefix.isNotEmpty()) return@postInLifecycle
            val hasCandidates =
                if (context.enclosingFrame.isArray) {
                    schema.valueSuggestions(context).isNotEmpty()
                } else {
                    context.inKey && schema.keySuggestions(context).isNotEmpty()
                }
            if (!hasCandidates) return@postInLifecycle
            forceNextCompletion = true
            editor.getComponent(EditorAutoCompletion::class.java).requireCompletion()
        }
    }

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        val forced = forceNextCompletion
        forceNextCompletion = false
        val schema = ConfigSchema.load() ?: return
        val context = JsonDocumentScanner.scan(content.toString(), position.index) ?: return
        if (!forced && !context.quoteOpen && context.prefix.isEmpty()) return
        val replaceLength = context.prefix.length + if (context.quoteOpen) 1 else 0
        val items = mutableListOf<CompletionItem>()
        if (context.inKey) {
            for (suggestion in schema.keySuggestions(context)) {
                if (!suggestion.name.startsWith(context.prefix, ignoreCase = true)) continue
                items += ConfigKeyCompletionItem(this, suggestion, replaceLength, context.quoteOpen, context.enclosingFrame.openIndex)
            }
        } else {
            for (suggestion in schema.valueSuggestions(context)) {
                when (suggestion.kind) {
                    ValueSuggestionKind.VALUE, ValueSuggestionKind.EXAMPLE, ValueSuggestionKind.REFERENCE -> {
                        if (!suggestion.label.startsWith(context.prefix, ignoreCase = true)) continue
                        if (context.quoteOpen && !suggestion.isString) continue
                        items += ConfigValueCompletionItem(suggestion, replaceLength, context.quoteOpen)
                    }

                    ValueSuggestionKind.ARRAY_EXAMPLE -> {
                        if (context.quoteOpen || context.prefix.isNotEmpty()) continue
                        items += ConfigArrayExampleCompletionItem(suggestion, replaceLength)
                    }

                    else -> {
                        if (context.quoteOpen || context.prefix.isNotEmpty()) continue
                        items +=
                            ConfigFormCompletionItem(
                                this,
                                suggestion.kind,
                                replaceLength,
                                context.enclosingFrame.isArray,
                                context.enclosingFrame.openIndex,
                            )
                    }
                }
            }
        }
        if (items.isNotEmpty()) {
            publisher.addItems(items)
        }
    }

    private inner class BracketNewlineHandler : NewlineHandler {

        override fun matchesRequirement(text: Content, position: CharPosition, style: Styles?): Boolean {
            if (position.column == 0) return false
            val line = text.getLineString(position.line)
            val beforeCursor = line.substring(0, position.column).trimEnd()
            if (beforeCursor.isEmpty()) return false
            val afterCursor = line.substring(position.column).trimStart()
            return when (beforeCursor.last()) {
                '{' -> afterCursor.startsWith("}")
                '[' -> afterCursor.startsWith("]")
                else -> false
            }
        }

        override fun handleNewline(text: Content, position: CharPosition, style: Styles?, tabSize: Int): NewlineHandleResult {
            val line = text.getLineString(position.line)
            var indentEnd = 0
            while (indentEnd < position.column && (line[indentEnd] == ' ' || line[indentEnd] == '\t')) {
                indentEnd++
            }
            val indent = line.substring(0, indentEnd)
            val inner = indent + " ".repeat(tabSize)
            newlineHandlerCommitPending = true
            return NewlineHandleResult("\n$inner\n$indent", indent.length + 1)
        }
    }
}

private class ConfigKeyCompletionItem(
    private val language: ConfigJsonLanguage,
    private val suggestion: KeySuggestion,
    replaceLength: Int,
    private val quoteOpen: Boolean,
    private val enclosingOpenIndex: Int,
) : CompletionItem(suggestion.name, suggestion.description) {

    init {
        prefixLength = replaceLength
        kind = CompletionItemKind.Property
        icon = SimpleCompletionIconDrawer.draw(CompletionItemKind.Property)
    }

    override fun performCompletion(editor: CodeEditor, text: Content, line: Int, column: Int) {
        var cursorIndex = text.getCharIndex(line, column)
        if (insertLeadingComma(text, cursorIndex - prefixLength)) {
            cursorIndex++
        }
        val start = cursorIndex - prefixLength
        var afterToken = cursorIndex
        while (afterToken < text.length && isKeyChar(text[afterToken])) {
            afterToken++
        }
        val remainderEmpty = afterToken == cursorIndex
        var hasClosingQuote = false
        if (afterToken < text.length && text[afterToken] == '"') {
            hasClosingQuote = true
            afterToken++
        }
        var afterSpace = afterToken
        while (afterSpace < text.length && (text[afterSpace] == ' ' || text[afterSpace] == '\t')) {
            afterSpace++
        }
        val quotedKey = "\"${suggestion.name}\""
        if (afterSpace < text.length && text[afterSpace] == ':') {
            replaceRange(text, start, afterToken, quotedKey)
            selectIndex(editor, text, start + quotedKey.length)
            return
        }
        val end = if (quoteOpen && remainderEmpty && hasClosingQuote) cursorIndex + 1 else cursorIndex
        val linePrefix = expandSingleLineContainer(text, enclosingOpenIndex)
        val baseIndent = linePrefix?.substring(1) ?: lineIndentAt(text, start)
        var insert: String
        var cursorBack: Int
        when (suggestion.scaffold) {
            ScaffoldKind.OBJECT -> {
                insert = "$quotedKey: " + expandedPair('{', '}', baseIndent)
                cursorBack = baseIndent.length + 2
            }

            ScaffoldKind.ARRAY -> {
                insert = "$quotedKey: " + expandedPair('[', ']', baseIndent)
                cursorBack = baseIndent.length + 2
            }

            ScaffoldKind.STRING -> {
                insert = "$quotedKey: \"\""
                cursorBack = 1
            }

            ScaffoldKind.PLAIN -> {
                insert = "$quotedKey: "
                cursorBack = 0
            }
        }
        if (nextSiblingKeyFollows(text, end)) {
            insert += ","
            cursorBack++
            val multiLineScaffold = suggestion.scaffold == ScaffoldKind.OBJECT || suggestion.scaffold == ScaffoldKind.ARRAY
            if (multiLineScaffold && end < text.length && text[end] == '"') {
                insert += "\n" + baseIndent
                cursorBack += 1 + baseIndent.length
            }
        }
        if (linePrefix != null) {
            insert = linePrefix + insert
        }
        replaceRange(text, start, end, insert)
        selectIndex(editor, text, start + insert.length - cursorBack)
        language.retriggerCompletion(editor)
    }

    private fun isKeyChar(character: Char): Boolean = character.isLetterOrDigit() || character == '_' || character == '-' || character == '$' || character == '.'
}

private fun nextSiblingKeyFollows(text: Content, index: Int): Boolean {
    val scan = JsonDocumentScanner.nextMeaningfulIndex(text, index)
    return scan >= 0 && text[scan] == '"'
}

private class ConfigArrayExampleCompletionItem(
    suggestion: ValueSuggestion,
    replaceLength: Int,
) : CompletionItem(suggestion.label, "example") {

    private val elements = suggestion.arrayElements.orEmpty()

    init {
        prefixLength = replaceLength
        kind = CompletionItemKind.Snippet
        icon = SimpleCompletionIconDrawer.draw(CompletionItemKind.Snippet)
    }

    override fun performCompletion(editor: CodeEditor, text: Content, line: Int, column: Int) {
        var cursorIndex = text.getCharIndex(line, column)
        if (insertLeadingComma(text, cursorIndex - prefixLength)) {
            cursorIndex++
        }
        val start = cursorIndex - prefixLength
        val baseIndent = lineIndentAt(text, start)
        val innerIndent = baseIndent + " ".repeat(JSON_INDENT_WIDTH)
        var insert = elements.joinToString(",\n$innerIndent", "[\n$innerIndent", "\n$baseIndent]")
        if (nextSiblingKeyFollows(text, cursorIndex)) {
            insert += ","
        }
        replaceRange(text, start, cursorIndex, insert)
        selectIndex(editor, text, start + insert.length)
    }
}

private class ConfigValueCompletionItem(
    suggestion: ValueSuggestion,
    replaceLength: Int,
    quoteOpen: Boolean,
) : CompletionItem(
    suggestion.label,
    when (suggestion.kind) {
        ValueSuggestionKind.EXAMPLE -> "example"
        ValueSuggestionKind.REFERENCE -> suggestion.description
        else -> null
    },
) {

    private val commitText = if (suggestion.isString) "\"${suggestion.label}\"" else suggestion.label
    private val absorbClosingQuote = quoteOpen && suggestion.isString

    init {
        prefixLength = replaceLength
        kind = if (suggestion.isString) CompletionItemKind.Value else CompletionItemKind.Keyword
        icon = SimpleCompletionIconDrawer.draw(kind!!)
    }

    override fun performCompletion(editor: CodeEditor, text: Content, line: Int, column: Int) {
        var cursorIndex = text.getCharIndex(line, column)
        if (insertLeadingComma(text, cursorIndex - prefixLength)) {
            cursorIndex++
        }
        val start = cursorIndex - prefixLength
        var end = cursorIndex
        if (absorbClosingQuote && end < text.length && text[end] == '"') {
            end++
        }
        replaceRange(text, start, end, commitText)
        selectIndex(editor, text, start + commitText.length)
    }
}

private class ConfigFormCompletionItem(
    private val language: ConfigJsonLanguage,
    private val formKind: ValueSuggestionKind,
    replaceLength: Int,
    private val enclosingIsArray: Boolean,
    private val enclosingOpenIndex: Int,
) : CompletionItem(
    when (formKind) {
        ValueSuggestionKind.ARRAY_FORM -> "[]"
        ValueSuggestionKind.OBJECT_FORM -> "{}"
        else -> "\"\""
    },
    when (formKind) {
        ValueSuggestionKind.ARRAY_FORM -> "array"
        ValueSuggestionKind.OBJECT_FORM -> "object"
        else -> "string"
    },
) {

    init {
        prefixLength = replaceLength
        kind = CompletionItemKind.Snippet
        icon = SimpleCompletionIconDrawer.draw(CompletionItemKind.Snippet)
    }

    override fun performCompletion(editor: CodeEditor, text: Content, line: Int, column: Int) {
        var cursorIndex = text.getCharIndex(line, column)
        if (insertLeadingComma(text, cursorIndex - prefixLength)) {
            cursorIndex++
        }
        val start = cursorIndex - prefixLength
        var insert = label.toString()
        var cursorBack = 1
        if (formKind == ValueSuggestionKind.OBJECT_FORM || formKind == ValueSuggestionKind.ARRAY_FORM) {
            val linePrefix = if (enclosingIsArray) expandSingleLineContainer(text, enclosingOpenIndex) else null
            val baseIndent = linePrefix?.substring(1) ?: lineIndentAt(text, start)
            insert =
                if (formKind == ValueSuggestionKind.OBJECT_FORM) {
                    expandedPair('{', '}', baseIndent)
                } else {
                    expandedPair('[', ']', baseIndent)
                }
            cursorBack = baseIndent.length + 2
            if (linePrefix != null) {
                insert = linePrefix + insert
            }
        }
        replaceRange(text, start, cursorIndex, insert)
        selectIndex(editor, text, start + insert.length - cursorBack)
        language.retriggerCompletion(editor)
    }
}

private fun expandedPair(open: Char, close: Char, baseIndent: String): String {
    val innerIndent = baseIndent + " ".repeat(JSON_INDENT_WIDTH)
    return "$open\n$innerIndent\n$baseIndent$close"
}

private fun lineIndentAt(text: Content, index: Int): String {
    val lineText = text.getLineString(text.indexer.getCharPosition(index).line)
    var indentEnd = 0
    while (indentEnd < lineText.length && (lineText[indentEnd] == ' ' || lineText[indentEnd] == '\t')) {
        indentEnd++
    }
    return lineText.substring(0, indentEnd)
}

private fun expandSingleLineContainer(text: Content, openIndex: Int): String? {
    if (openIndex < 0 || openIndex >= text.length) return null
    val open = text[openIndex]
    if (open != '{' && open != '[') return null
    val closeIndex = JsonDocumentScanner.matchingCloseIndex(text, openIndex)
    if (closeIndex < 0) return null
    val openPosition = text.indexer.getCharPosition(openIndex)
    val closePosition = text.indexer.getCharPosition(closeIndex)
    if (openPosition.line != closePosition.line) return null
    val indent = lineIndentAt(text, openIndex)
    text.insert(closePosition.line, closePosition.column, "\n" + indent)
    return "\n" + indent + " ".repeat(JSON_INDENT_WIDTH)
}

private fun precedingValueEndIndex(text: Content, start: Int): Int {
    val scan = JsonDocumentScanner.lastMeaningfulIndexBefore(text, start)
    if (scan < 0) return -1
    val character = text[scan]
    val afterValueEnd =
        character == '"' || character == '}' || character == ']' ||
            character.isDigit() || character == 'e' || character == 'l'
    return if (afterValueEnd) scan + 1 else -1
}

private fun insertLeadingComma(text: Content, start: Int): Boolean {
    val commaIndex = precedingValueEndIndex(text, start)
    if (commaIndex < 0) return false
    val position = text.indexer.getCharPosition(commaIndex)
    text.insert(position.line, position.column, ",")
    return true
}

internal fun siblingCommaInsertIndex(content: Content, cursorIndex: Int, typed: Char): Int {
    val valueStart =
        typed == '{' || typed == '[' || typed == '"' || typed == '-' ||
            typed == 't' || typed == 'f' || typed == 'n' || typed.isDigit()
    if (!valueStart) return -1
    val commaIndex = precedingValueEndIndex(content, cursorIndex)
    if (commaIndex < 0) return -1
    val context = JsonDocumentScanner.scan(content.toString(), cursorIndex) ?: return -1
    if (context.quoteOpen || context.prefix.isNotEmpty()) return -1
    if (!context.enclosingFrame.isArray && !(context.inKey && typed == '"')) return -1
    return commaIndex
}

private fun replaceRange(text: Content, start: Int, end: Int, replacement: String) {
    val startPosition = text.indexer.getCharPosition(start)
    val endPosition = text.indexer.getCharPosition(end)
    text.replace(startPosition.line, startPosition.column, endPosition.line, endPosition.column, replacement)
}

private fun selectIndex(editor: CodeEditor, text: Content, index: Int) {
    val position = text.indexer.getCharPosition(index)
    editor.setSelection(position.line, position.column)
}

private fun insideStringLiteral(line: CharSequence, column: Int): Boolean {
    var inString = false
    var index = 0
    while (index < column) {
        val character = line[index]
        if (character == '\\' && inString) {
            index += 2
            continue
        }
        if (!inString && character == '/' && index + 1 < line.length) {
            if (line[index + 1] == '/') return false
            if (line[index + 1] == '*') {
                var close = index + 2
                while (close + 1 < column && !(line[close] == '*' && line[close + 1] == '/')) {
                    close++
                }
                if (close + 1 >= column) return false
                index = close + 2
                continue
            }
        }
        if (character == '"') {
            inString = !inString
        }
        index++
    }
    return inString
}

private fun nextCharBeginsArrayValue(text: CharSequence, index: Int): Boolean {
    val scan = JsonDocumentScanner.nextMeaningfulIndex(text, index)
    if (scan < 0) return false
    return when (text[scan]) {
        '"', '{', '[', '-', 't', 'f', 'n' -> true
        else -> text[scan].isDigit()
    }
}
