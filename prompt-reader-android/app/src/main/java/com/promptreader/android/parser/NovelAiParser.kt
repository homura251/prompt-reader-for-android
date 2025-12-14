package com.promptreader.android.parser

import org.json.JSONObject

object NovelAiParser {
    data class Result(
        val positive: String,
        val negative: String,
        val setting: String,
        val raw: String,
    )

    fun parseLegacy(description: String, commentJson: String): Result {
        val positive = description.trim()
        val data = JSONObject(commentJson)
        val negative = data.optString("uc", "").trim()

        val copy = JSONObject(data.toString())
        copy.remove("uc")

        val setting = copy.toString().trim('{', '}').replace("\"", "").trim()
        val raw = listOf(positive, negative, data.toString()).filter { it.isNotBlank() }.joinToString("\n")

        return Result(positive, negative, setting, raw)
    }

    fun parseStealth(json: JSONObject): Result {
        val merged = JSONObject(json.toString())

        // Python: if "Comment" in json_data -> merge json_data | json.loads(json_data["Comment"])
        if (merged.has("Comment")) {
            val commentStr = merged.optString("Comment", "")
            runCatching {
                val commentObj = JSONObject(commentStr)
                for (k in commentObj.keys()) {
                    merged.put(k, commentObj.get(k))
                }
            }
            merged.remove("Comment")
        }

        val positive = merged.optString("prompt", merged.optString("Description", "")).trim()
        val negative = merged.optString("uc", "").trim()

        val copy = JSONObject(merged.toString())
        copy.remove("prompt")
        copy.remove("uc")
        copy.remove("Description")

        val setting = copy.toString().trim('{', '}').replace("\"", "").trim()
        val raw = listOf(positive, negative, merged.toString()).filter { it.isNotBlank() }.joinToString("\n")

        return Result(positive, negative, setting, raw)
    }
}
