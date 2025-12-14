package com.promptreader.core.parse

internal object MergeUtil {
    fun mergeDict(a: Map<String, Any?>, b: Map<String, Any?>): Map<String, Any?> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val out = LinkedHashMap<String, Any?>(a.size + b.size)
        out.putAll(a)
        for ((k, v) in b) {
            val existing = out[k]
            if (existing == null) {
                out[k] = v
            } else {
                out[k] = mergeValue(existing, v)
            }
        }
        return out
    }

    private fun mergeValue(existing: Any?, incoming: Any?): Any? {
        if (existing is List<*>) return existing + incoming
        if (incoming is List<*>) return listOf(incoming, existing)
        return listOf(incoming, existing)
    }
}
