package io.nekohasekai.sfa.compat

class JsonFrame(val entryKey: String?, val isArray: Boolean, val openIndex: Int) {
    val keys = mutableSetOf<String>()
    val stringValues = mutableMapOf<String, String>()
    var currentKey: String? = null
    var expectingValue = false
    var pendingKey: String? = null
}

class JsonCursorContext(
    val frames: List<JsonFrame>,
    val inKey: Boolean,
    val valueKey: String?,
    val prefix: String,
    val quoteOpen: Boolean,
    val documentTags: Map<String, List<String>>,
) {
    val enclosingFrame: JsonFrame
        get() = frames.last()
}

class JsonBalance(
    val orphanClosingBraces: Int,
    val orphanClosingBrackets: Int,
    val quoteCount: Int,
)

private class CommentState {
    var active = false
        private set
    private var lineComment = false
    private var star = false
    private var opening = false

    fun consume(text: CharSequence, index: Int): Boolean {
        val character = text[index]
        if (active) {
            if (lineComment) {
                if (character == '\n') {
                    active = false
                    return false
                }
                return true
            }
            when {
                opening -> opening = false
                character == '/' && star -> active = false
                else -> star = character == '*'
            }
            return true
        }
        if (character == '/' && index + 1 < text.length) {
            when (text[index + 1]) {
                '/' -> {
                    active = true
                    lineComment = true
                    return true
                }

                '*' -> {
                    active = true
                    lineComment = false
                    opening = true
                    star = false
                    return true
                }
            }
        }
        return false
    }
}

object JsonDocumentScanner {

    fun balance(text: String): JsonBalance {
        var inString = false
        var escaped = false
        val comment = CommentState()
        var braceDepth = 0
        var orphanClosingBraces = 0
        var bracketDepth = 0
        var orphanClosingBrackets = 0
        var quoteCount = 0
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true

                    character == '"' -> {
                        inString = false
                        quoteCount++
                    }

                    character == '\n' -> inString = false
                }
            } else if (!comment.consume(text, index)) {
                when (character) {
                    '"' -> {
                        inString = true
                        quoteCount++
                    }

                    '{' -> braceDepth++
                    '}' -> if (braceDepth > 0) braceDepth-- else orphanClosingBraces++
                    '[' -> bracketDepth++
                    ']' -> if (bracketDepth > 0) bracketDepth-- else orphanClosingBrackets++
                }
            }
            index++
        }
        return JsonBalance(orphanClosingBraces, orphanClosingBrackets, quoteCount)
    }

    fun lastMeaningfulIndexBefore(text: CharSequence, end: Int): Int {
        val limit = minOf(end, text.length)
        var inString = false
        var escaped = false
        val comment = CommentState()
        var last = -1
        var index = 0
        while (index < limit) {
            val character = text[index]
            if (inString) {
                when {
                    escaped -> {
                        escaped = false
                        last = index
                    }

                    character == '\\' -> {
                        escaped = true
                        last = index
                    }

                    character == '"' -> {
                        inString = false
                        last = index
                    }

                    character == '\n' -> inString = false
                    else -> last = index
                }
            } else if (!comment.consume(text, index)) {
                if (character == '"') {
                    inString = true
                    last = index
                } else if (!character.isWhitespace()) {
                    last = index
                }
            }
            index++
        }
        return last
    }

    fun nextMeaningfulIndex(text: CharSequence, from: Int): Int {
        val comment = CommentState()
        var index = maxOf(from, 0)
        while (index < text.length) {
            if (!comment.consume(text, index) && !text[index].isWhitespace()) {
                return index
            }
            index++
        }
        return -1
    }

    fun matchingCloseIndex(text: CharSequence, openIndex: Int): Int {
        if (openIndex < 0 || openIndex >= text.length) return -1
        val open = text[openIndex]
        val close =
            when (open) {
                '{' -> '}'
                '[' -> ']'
                else -> return -1
            }
        var depth = 1
        var inString = false
        var escaped = false
        val comment = CommentState()
        var index = openIndex + 1
        while (index < text.length) {
            val character = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                    character == '\n' -> inString = false
                }
            } else if (!comment.consume(text, index)) {
                when (character) {
                    '"' -> inString = true
                    open -> depth++

                    close -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
            }
            index++
        }
        return -1
    }

    fun scan(text: String, cursor: Int): JsonCursorContext? {
        val stack = ArrayList<JsonFrame>()
        var inString = false
        var escaped = false
        val stringBuffer = StringBuilder()
        val literalBuffer = StringBuilder()
        var captured: JsonCursorContext? = null
        var capturedDone = false
        val documentTags = LinkedHashMap<String, MutableList<String>>()

        fun currentFrame(): JsonFrame? = stack.lastOrNull()

        fun recordTag(value: String) {
            if (stack.size < 3 || !stack[stack.size - 2].isArray) return
            val path = StringBuilder()
            for (i in 1 until stack.size - 1) {
                val entryKey = stack[i].entryKey ?: return
                if (path.isNotEmpty()) path.append('.')
                path.append(entryKey)
            }
            documentTags.getOrPut(path.toString()) { mutableListOf() }.add(value)
        }

        fun finishValue(stringValue: String?) {
            val frame = currentFrame() ?: return
            if (frame.isArray) return
            val key = frame.currentKey
            if (key != null) {
                frame.keys += key
                if (stringValue != null) {
                    frame.stringValues[key] = stringValue
                    if (key == "tag") {
                        recordTag(stringValue)
                    }
                }
            }
            frame.currentKey = null
            frame.expectingValue = false
        }

        fun flushLiteral() {
            if (literalBuffer.isEmpty()) return
            literalBuffer.setLength(0)
            val frame = currentFrame() ?: return
            if (!frame.isArray && frame.expectingValue) {
                finishValue(null)
            }
        }

        fun endString(value: String) {
            val frame = currentFrame() ?: return
            if (frame.isArray) return
            if (frame.expectingValue) {
                finishValue(value)
            } else {
                frame.pendingKey = value
            }
        }

        fun capture(): JsonCursorContext? {
            val frame = currentFrame() ?: return null
            val prefix: String
            val quoteOpen: Boolean
            if (inString) {
                prefix = stringBuffer.toString()
                quoteOpen = true
            } else {
                prefix = literalBuffer.toString()
                quoteOpen = false
            }
            val inKey = !frame.isArray && !frame.expectingValue
            val valueKey = if (!frame.isArray && frame.expectingValue) frame.currentKey else null
            return JsonCursorContext(stack.toList(), inKey, valueKey, prefix, quoteOpen, documentTags)
        }

        val comment = CommentState()
        var index = 0
        while (index < text.length) {
            if (index == cursor) {
                captured = if (comment.active) null else capture()
                capturedDone = true
            }
            val character = text[index]
            if (inString) {
                when {
                    escaped -> {
                        stringBuffer.append(character)
                        escaped = false
                    }

                    character == '\\' -> escaped = true

                    character == '"' -> {
                        inString = false
                        endString(stringBuffer.toString())
                        stringBuffer.setLength(0)
                    }

                    character == '\n' -> {
                        inString = false
                        val frame = currentFrame()
                        if (frame != null && !frame.isArray && frame.expectingValue) {
                            finishValue(stringBuffer.toString())
                        }
                        stringBuffer.setLength(0)
                    }

                    else -> stringBuffer.append(character)
                }
            } else if (comment.consume(text, index)) {
                flushLiteral()
            } else {
                when (character) {
                    '"' -> {
                        flushLiteral()
                        inString = true
                    }

                    '{', '[' -> {
                        flushLiteral()
                        val parent = currentFrame()
                        val entryKey = if (parent != null && !parent.isArray) parent.currentKey else null
                        stack.add(JsonFrame(entryKey, character == '[', index))
                    }

                    '}', ']' -> {
                        flushLiteral()
                        if (stack.isNotEmpty()) {
                            stack.removeAt(stack.size - 1)
                            finishValue(null)
                        }
                    }

                    ':' -> {
                        val frame = currentFrame()
                        if (frame != null && !frame.isArray) {
                            frame.currentKey = frame.pendingKey
                            frame.pendingKey = null
                            frame.expectingValue = true
                        }
                    }

                    ',' -> {
                        flushLiteral()
                        val frame = currentFrame()
                        if (frame != null && !frame.isArray) {
                            if (frame.expectingValue) {
                                finishValue(null)
                            }
                            frame.pendingKey = null
                        }
                    }

                    ' ', '\t', '\r', '\n' -> flushLiteral()

                    else -> literalBuffer.append(character)
                }
            }
            index++
        }
        if (!capturedDone && cursor >= text.length) {
            captured = if (comment.active) null else capture()
        }
        return captured
    }
}
