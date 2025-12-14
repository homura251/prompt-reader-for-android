package com.promptreader.android.parser

import org.json.JSONObject

object SwarmUiParser {
    data class Result(
        val positive: String,
        val negative: String,
        val setting: String,
        val raw: String,
    )

    fun parse(jsonText: String): Result {
        val root = JSONObject(jsonText)
        val params = root.optJSONObject("sui_image_params") ?: JSONObject()

        val positive = params.optString("prompt", "").trim()
        val negative = params.optString("negativeprompt", "").trim()

        // Remove prompt/negativeprompt from display settings, similar to Python
        val copy = JSONObject(params.toString())
        copy.remove("prompt")
        copy.remove("negativeprompt")

        val setting = copy.toString().trim('{', '}').replace("\"", "").trim()
        val raw = listOf(positive, negative, params.toString()).filter { it.isNotBlank() }.joinToString("\n")

        return Result(positive, negative, setting, raw)
    }
}
