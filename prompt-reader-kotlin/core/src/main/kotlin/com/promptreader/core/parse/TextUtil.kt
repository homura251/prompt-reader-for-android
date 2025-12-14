package com.promptreader.core.parse

internal object TextUtil {
    fun removeQuotes(value: Any?): String =
        value?.toString()?.replace("\"", "")?.replace("'", "") ?: ""

    fun concat(base: String, addition: String, separator: String = ", "): String {
        if (base.isBlank()) return addition
        if (addition.isBlank()) return base
        return base + separator + addition
    }

    fun mapToSettingString(map: Map<String, Any?>): String {
        // Similar to Python's remove_quotes(str(dict)).strip("{ }")
        return removeQuotes(map.toString()).removePrefix("{").removeSuffix("}").trim()
    }
}
