package com.promptreader.android.parser

import org.json.JSONObject

object FooocusParser {
    data class Result(
        val positive: String,
        val negative: String,
        val setting: String,
        val raw: String,
    )

    fun parse(jsonText: String): Result {
        val data = JSONObject(jsonText)
        val positive = data.optString("prompt", "").trim()
        val negative = data.optString("negative_prompt", "").trim()

        val copy = JSONObject(data.toString())
        copy.remove("prompt")
        copy.remove("negative_prompt")

        val setting = copy.toString().trim('{', '}').replace("\"", "").trim()
        val raw = listOf(positive, negative, data.toString()).filter { it.isNotBlank() }.joinToString("\n")

        return Result(positive, negative, setting, raw)
    }
}
