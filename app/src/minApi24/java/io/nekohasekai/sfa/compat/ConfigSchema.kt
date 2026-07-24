package io.nekohasekai.sfa.compat

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.ktx.unwrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

enum class ScaffoldKind {
    OBJECT,
    ARRAY,
    STRING,
    PLAIN,
}

data class KeySuggestion(val name: String, val description: String?, val scaffold: ScaffoldKind)

enum class ValueSuggestionKind {
    VALUE,
    EXAMPLE,
    REFERENCE,
    ARRAY_EXAMPLE,
    ARRAY_FORM,
    OBJECT_FORM,
    STRING_FORM,
}

data class ValueSuggestion(
    val label: String,
    val isString: Boolean,
    val kind: ValueSuggestionKind = ValueSuggestionKind.VALUE,
    val description: String? = null,
    val arrayElements: List<String>? = null,
)

class ConfigSchema(private val root: JsonObject) {

    companion object {
        private val loadAccess = Any()

        @Volatile
        private var loadAttempted = false
        private var loadedSchema: ConfigSchema? = null

        fun preload() {
            if (loadAttempted) return
            CoroutineScope(Dispatchers.IO).launch { load() }
        }

        fun peek(): ConfigSchema? = if (loadAttempted) loadedSchema else null

        fun load(): ConfigSchema? {
            if (!loadAttempted) {
                synchronized(loadAccess) {
                    if (!loadAttempted) {
                        loadedSchema =
                            runCatching {
                                ConfigSchema(Json.parseToJsonElement(Libbox.generateConfigSchema().unwrap).jsonObject)
                            }.getOrNull()
                        loadAttempted = true
                    }
                }
            }
            return loadedSchema
        }
    }

    private val definitions = root["\$defs"] as? JsonObject
    private val discriminatorNames = listOf("type", "action", "provider", "version", "mode")

    private val referenceContainers =
        mapOf(
            "outbound" to listOf("outbounds", "endpoints"),
            "inbound" to listOf("inbounds"),
            "dns_server" to listOf("dns.servers"),
            "rule_set" to listOf("route.rule_set"),
            "certificate_provider" to listOf("certificate_providers"),
            "http_client" to listOf("http_clients"),
            "network_namespace" to listOf("network_namespaces"),
        )

    fun keySuggestions(context: JsonCursorContext): List<KeySuggestion> {
        val frames = context.frames
        val frame = context.enclosingFrame
        val rawNodes =
            if (frames.size == 1) {
                listOf(root)
            } else {
                val parentParts = partsAt(frames.dropLast(1))
                val entryKey = frame.entryKey
                if (entryKey != null) {
                    parentParts.mapNotNull { (it["properties"] as? JsonObject)?.get(entryKey) as? JsonObject }
                } else {
                    parentParts.mapNotNull { it["items"] as? JsonObject }
                }
            }
        val collected = LinkedHashMap<String, JsonObject>()
        for (node in rawNodes) {
            collectKeys(node, frame.stringValues, collected, 0)
        }
        return collected.mapNotNull { (name, propertySchema) ->
            if (name in frame.keys) {
                null
            } else {
                KeySuggestion(name, describe(propertySchema), scaffoldKindOf(propertySchema))
            }
        }
    }

    private fun scaffoldKindOf(node: JsonObject): ScaffoldKind {
        val kinds = mutableSetOf<ScaffoldKind>()
        for (part in expand(node, emptyMap(), 0)) {
            when {
                part.containsKey("properties") || typeContains(part, "object") -> kinds += ScaffoldKind.OBJECT
                typeContains(part, "array") -> kinds += ScaffoldKind.ARRAY
                typeContains(part, "string") || hasStringValues(part) -> kinds += ScaffoldKind.STRING

                typeContains(part, "boolean") || typeContains(part, "integer") || typeContains(part, "number") ->
                    kinds += ScaffoldKind.PLAIN
            }
        }
        return kinds.singleOrNull() ?: ScaffoldKind.PLAIN
    }

    private fun hasStringValues(part: JsonObject): Boolean {
        val const = part["const"] as? JsonPrimitive
        if (const != null && const.isString) return true
        val enumValues = part["enum"] as? JsonArray ?: return false
        return enumValues.any { (it as? JsonPrimitive)?.isString == true }
    }

    private fun collectKeys(
        node: JsonObject,
        siblingValues: Map<String, String>,
        result: LinkedHashMap<String, JsonObject>,
        depth: Int,
    ) {
        if (depth > 8) return
        val resolved = resolve(node)
        val variants = (resolved["oneOf"] ?: resolved["anyOf"]) as? JsonArray
        if (variants != null) {
            val leaves = leafVariants(variants, depth + 1)
            val matched = leaves.filter { matchesSiblings(it, siblingValues, depth + 1) }
            val discriminator = if (matched.isEmpty()) detectDiscriminator(leaves, depth + 1) else null
            when {
                matched.isNotEmpty() -> {
                    for (variant in matched) {
                        collectKeys(variant, siblingValues, result, depth + 1)
                    }
                }

                discriminator == null -> {
                    for (variant in leaves) {
                        collectKeys(variant, siblingValues, result, depth + 1)
                    }
                }

                else -> {
                    val admitting = leaves.filter { admitsAbsence(it, discriminator, depth + 1) }
                    if (admitting.isNotEmpty()) {
                        for (variant in admitting) {
                            collectKeys(variant, siblingValues, result, depth + 1)
                        }
                    } else {
                        discriminatorSchema(leaves.first(), discriminator, depth + 1)?.let { schema ->
                            result.getOrPut(discriminator) { schema }
                        }
                    }
                }
            }
        }
        (resolved["allOf"] as? JsonArray)?.filterIsInstance<JsonObject>()?.forEach { branch ->
            collectKeys(branch, siblingValues, result, depth + 1)
        }
        (resolved["properties"] as? JsonObject)?.forEach { (name, propertySchema) ->
            if (propertySchema is JsonObject) {
                result.getOrPut(name) { propertySchema }
            }
        }
    }

    private fun leafVariants(variants: JsonArray, depth: Int): List<JsonObject> {
        if (depth > 8) return emptyList()
        val result = mutableListOf<JsonObject>()
        for (variant in variants.filterIsInstance<JsonObject>()) {
            val resolved = resolve(variant)
            val nested = (resolved["oneOf"] ?: resolved["anyOf"]) as? JsonArray
            if (nested != null && !resolved.containsKey("properties") && !resolved.containsKey("allOf")) {
                result += leafVariants(nested, depth + 1)
            } else {
                result += resolved
            }
        }
        return result
    }

    private fun detectDiscriminator(leaves: List<JsonObject>, depth: Int): String? {
        if (leaves.isEmpty()) return null
        return discriminatorNames.firstOrNull { name ->
            leaves.all { leaf -> discriminatorSchema(leaf, name, depth) != null }
        }
    }

    private fun discriminatorSchema(variant: JsonObject, name: String, depth: Int): JsonObject? {
        for (part in expand(variant, emptyMap(), depth)) {
            val propertySchema = (part["properties"] as? JsonObject)?.get(name) as? JsonObject ?: continue
            val resolvedProperty = resolve(propertySchema)
            if (resolvedProperty.containsKey("const") || resolvedProperty["enum"] is JsonArray) {
                return resolvedProperty
            }
        }
        return null
    }

    private fun admitsAbsence(variant: JsonObject, name: String, depth: Int): Boolean {
        var required = false
        var emptyAllowed = false
        for (part in expand(variant, emptyMap(), depth)) {
            (part["required"] as? JsonArray)?.forEach { element ->
                if ((element as? JsonPrimitive)?.contentOrNull == name) {
                    required = true
                }
            }
            val propertySchema = (part["properties"] as? JsonObject)?.get(name) as? JsonObject
            if (propertySchema != null) {
                val enumValues = (resolve(propertySchema)["enum"] as? JsonArray)
                if (enumValues?.any { (it as? JsonPrimitive)?.contentOrNull == "" } == true) {
                    emptyAllowed = true
                }
            }
        }
        return !required || emptyAllowed
    }

    fun valueSuggestions(context: JsonCursorContext): List<ValueSuggestion> {
        val containerParts = partsAt(context.frames, context.valueKey)
        val frame = context.enclosingFrame
        val raw =
            if (frame.isArray) {
                containerParts.mapNotNull { it["items"] as? JsonObject }
            } else {
                val key = context.valueKey ?: return emptyList()
                containerParts.mapNotNull { (it["properties"] as? JsonObject)?.get(key) as? JsonObject }
            }
        val parts = raw.flatMap { expand(it, emptyMap(), 0) }
        val values = LinkedHashMap<String, ValueSuggestion>()
        val examples = LinkedHashMap<String, ValueSuggestion>()
        for (kind in parts.mapNotNull { (it["x-tag-reference"] as? JsonPrimitive)?.contentOrNull }.distinct()) {
            val containers = referenceContainers[kind] ?: continue
            val ownTags = enclosingEntityTags(context, containers)
            for (container in containers) {
                for (tag in context.documentTags[container].orEmpty()) {
                    if (tag.isEmpty() || tag in ownTags) continue
                    values.getOrPut(tag) { ValueSuggestion(tag, true, ValueSuggestionKind.REFERENCE, kind) }
                }
            }
        }
        for (part in parts) {
            (part["const"] as? JsonPrimitive)?.let { addValue(values, it, ValueSuggestionKind.VALUE) }
            (part["enum"] as? JsonArray)?.forEach { element ->
                (element as? JsonPrimitive)?.let { addValue(values, it, ValueSuggestionKind.VALUE) }
            }
            if (typeContains(part, "boolean")) {
                values.getOrPut("true") { ValueSuggestion("true", false) }
                values.getOrPut("false") { ValueSuggestion("false", false) }
            }
            (part["examples"] as? JsonArray)?.forEach { element ->
                when (element) {
                    is JsonPrimitive -> addValue(examples, element, ValueSuggestionKind.EXAMPLE)
                    is JsonArray -> addArrayExample(examples, element)
                    else -> {}
                }
            }
        }
        val result = values.values.toMutableList()
        for (example in examples.values) {
            if (example.label !in values) {
                result += example
            }
        }
        if (!frame.isArray) {
            result += formSuggestions(parts)
        } else if (parts.any { it.containsKey("properties") || typeContains(it, "object") }) {
            result += ValueSuggestion("{}", false, ValueSuggestionKind.OBJECT_FORM)
        }
        return result
    }

    private fun enclosingEntityTags(context: JsonCursorContext, containers: List<String>): Set<String> {
        val frames = context.frames
        val result = mutableSetOf<String>()
        val path = StringBuilder()
        for (i in 1 until frames.size) {
            val entryKey = frames[i].entryKey
            if (entryKey != null) {
                if (path.isNotEmpty()) path.append('.')
                path.append(entryKey)
            } else if (frames[i - 1].isArray && path.toString() in containers) {
                frames[i].stringValues["tag"]?.let { result += it }
            }
        }
        return result
    }

    private fun formSuggestions(parts: List<JsonObject>): List<ValueSuggestion> {
        var hasArray = false
        var hasObject = false
        var hasString = false
        var stringHasValues = false
        for (part in parts) {
            when {
                typeContains(part, "array") -> hasArray = true
                part.containsKey("properties") || typeContains(part, "object") -> hasObject = true

                typeContains(part, "string") || hasStringValues(part) -> {
                    hasString = true
                    if (part.containsKey("enum") || part.containsKey("const") || part.containsKey("examples")) {
                        stringHasValues = true
                    }
                }
            }
        }
        val formCount = listOf(hasArray, hasObject, hasString).count { it }
        if (formCount < 2) return emptyList()
        val result = mutableListOf<ValueSuggestion>()
        if (hasArray) result += ValueSuggestion("[]", false, ValueSuggestionKind.ARRAY_FORM)
        if (hasObject) result += ValueSuggestion("{}", false, ValueSuggestionKind.OBJECT_FORM)
        if (hasString && !stringHasValues) result += ValueSuggestion("\"\"", false, ValueSuggestionKind.STRING_FORM)
        return result
    }

    private fun addArrayExample(result: LinkedHashMap<String, ValueSuggestion>, example: JsonArray) {
        if (example.isEmpty()) return
        val elements =
            example.map { element ->
                (element as? JsonPrimitive ?: return).toString()
            }
        val compact = elements.joinToString(", ", "[", "]")
        val label = if (compact.length > 48) compact.take(45) + "..." else compact
        result.getOrPut(compact) { ValueSuggestion(label, false, ValueSuggestionKind.ARRAY_EXAMPLE, "example", elements) }
    }

    private fun addValue(result: LinkedHashMap<String, ValueSuggestion>, primitive: JsonPrimitive, kind: ValueSuggestionKind) {
        val content = primitive.contentOrNull ?: return
        if (content.isEmpty()) return
        result.getOrPut(content) { ValueSuggestion(content, primitive.isString, kind) }
    }

    private fun partsAt(frames: List<JsonFrame>, excludeValueKey: String? = null): List<JsonObject> {
        if (frames.isEmpty()) return emptyList()
        var parts = expand(root, siblingValuesOf(frames, 0, excludeValueKey), 0)
        for (i in 1 until frames.size) {
            val frame = frames[i]
            val entryKey = frame.entryKey
            val raw =
                if (entryKey != null) {
                    parts.mapNotNull { (it["properties"] as? JsonObject)?.get(entryKey) as? JsonObject }
                } else {
                    parts.mapNotNull { it["items"] as? JsonObject }
                }
            parts = raw.flatMap { expand(it, siblingValuesOf(frames, i, excludeValueKey), 0) }
            if (parts.isEmpty()) return emptyList()
        }
        return parts
    }

    private fun siblingValuesOf(frames: List<JsonFrame>, index: Int, excludeValueKey: String?): Map<String, String> {
        val values = frames[index].stringValues
        if (excludeValueKey == null || index != frames.size - 1) return values
        return values - excludeValueKey
    }

    private fun expand(node: JsonObject, siblingValues: Map<String, String>, depth: Int): List<JsonObject> {
        if (depth > 8) return emptyList()
        val resolved = resolve(node)
        val parts = mutableListOf<JsonObject>()
        val variants = (resolved["oneOf"] ?: resolved["anyOf"]) as? JsonArray
        if (variants != null) {
            val objects = variants.filterIsInstance<JsonObject>()
            val selected = objects.filter { matchesSiblings(it, siblingValues, depth + 1) }.ifEmpty { objects }
            for (variant in selected) {
                parts += expand(variant, siblingValues, depth + 1)
            }
        }
        (resolved["allOf"] as? JsonArray)?.filterIsInstance<JsonObject>()?.forEach { branch ->
            parts += expand(branch, siblingValues, depth + 1)
        }
        parts += resolved
        return parts
    }

    private fun matchesSiblings(variant: JsonObject, siblingValues: Map<String, String>, depth: Int): Boolean {
        if (siblingValues.isEmpty()) return false
        for (part in expand(variant, emptyMap(), depth)) {
            val properties = part["properties"] as? JsonObject ?: continue
            for ((name, value) in siblingValues) {
                val propertySchema = properties[name] as? JsonObject ?: continue
                val resolvedProperty = resolve(propertySchema)
                (resolvedProperty["const"] as? JsonPrimitive)?.contentOrNull?.let { constValue ->
                    return constValue == value
                }
                val enumValues =
                    (resolvedProperty["enum"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                if (!enumValues.isNullOrEmpty()) {
                    return value in enumValues
                }
            }
        }
        return false
    }

    private fun resolve(node: JsonObject): JsonObject {
        var current = node
        var depth = 0
        while (depth < 8) {
            val reference = (current["\$ref"] as? JsonPrimitive)?.contentOrNull ?: return current
            current = definitions?.get(reference.substringAfterLast('/')) as? JsonObject ?: return current
            depth++
        }
        return current
    }

    private fun describe(node: JsonObject, depth: Int = 0): String? {
        if (depth > 3) return null
        (node["\$ref"] as? JsonPrimitive)?.contentOrNull?.let { return it.substringAfterLast('/') }
        (node["type"] as? JsonPrimitive)?.contentOrNull?.let { type ->
            if (type == "array") {
                val itemDescription = (node["items"] as? JsonObject)?.let { describe(it, depth + 1) }
                return if (itemDescription != null) "$itemDescription[]" else "array"
            }
            return type
        }
        if (node.containsKey("const") || node.containsKey("enum")) return "enum"
        val variants = (node["oneOf"] ?: node["anyOf"]) as? JsonArray
        variants?.filterIsInstance<JsonObject>()?.forEach { variant ->
            describe(variant, depth + 1)?.let { return it }
        }
        if (node.containsKey("properties") || node.containsKey("allOf")) return "object"
        return null
    }

    private fun typeContains(part: JsonObject, type: String): Boolean = when (val typeElement = part["type"]) {
        is JsonPrimitive -> typeElement.contentOrNull == type
        is JsonArray -> typeElement.any { (it as? JsonPrimitive)?.contentOrNull == type }
        else -> false
    }
}
