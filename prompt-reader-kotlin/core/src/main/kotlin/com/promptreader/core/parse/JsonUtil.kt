package com.promptreader.core.parse

import org.json.JSONArray
import org.json.JSONObject

internal object JsonUtil {
    fun optString(obj: JSONObject, key: String): String? =
        if (!obj.has(key) || obj.isNull(key)) null else obj.optString(key, null)

    fun optObject(obj: JSONObject, key: String): JSONObject? =
        if (!obj.has(key) || obj.isNull(key)) null else obj.optJSONObject(key)

    fun optArray(obj: JSONObject, key: String): JSONArray? =
        if (!obj.has(key) || obj.isNull(key)) null else obj.optJSONArray(key)

    fun toMap(obj: JSONObject): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.opt(k)
            out[k] = when (v) {
                is JSONObject -> toMap(v)
                is JSONArray -> toList(v)
                JSONObject.NULL -> null
                else -> v
            }
        }
        return out
    }

    fun toList(arr: JSONArray): List<Any?> {
        val out = ArrayList<Any?>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            out.add(
                when (v) {
                    is JSONObject -> toMap(v)
                    is JSONArray -> toList(v)
                    JSONObject.NULL -> null
                    else -> v
                }
            )
        }
        return out
    }
}
