package com.promptreader.android.parser

import org.json.JSONObject

object FooocusParser {
    data class Result(
        val positive: String,
        val negative: String,
        val setting: String,
        val settingEntries: List<SettingEntry>,
        val settingDetail: String,
        val raw: String,
    )

    fun parse(jsonText: String): Result {
        val data = JSONObject(jsonText)
        val positive = data.optString("prompt", "").trim()
        val negative = data.optString("negative_prompt", "").trim()

        val entries = buildFooocusSettingEntries(data)
        val setting = buildSummary(entries)

        val detailObj = JSONObject(data.toString()).apply {
            remove("prompt")
            remove("negative_prompt")
        }
        val settingDetail = if (detailObj.length() > 0) detailObj.toString(2) else ""
        val raw = listOf(positive, negative, data.toString()).filter { it.isNotBlank() }.joinToString("\n")

        return Result(positive, negative, setting, entries, settingDetail, raw)
    }

    private fun buildFooocusSettingEntries(data: JSONObject): List<SettingEntry> {
        val entries = ArrayList<SettingEntry>()

        // Fooocus variants differ; only show keys that actually exist.
        val steps = data.optInt("steps", -1)
        val sampler = data.optString("sampler", "").trim()
        val seed = runCatching { data.getLong("seed") }.getOrNull()
        val width = data.optInt("width", -1)
        val height = data.optInt("height", -1)

        val cfg = data.optDouble("cfg_scale", Double.NaN)
        val performance = data.optString("performance", "").trim()
        val sharpness = data.optDouble("sharpness", Double.NaN)

        if (steps >= 0) entries += SettingEntry("Steps", steps.toString())
        if (sampler.isNotBlank()) entries += SettingEntry("Sampler", sampler)
        if (!cfg.isNaN()) entries += SettingEntry("CFG scale", trimNumber(cfg))
        if (seed != null) entries += SettingEntry("Seed", seed.toString())
        if (width > 0 && height > 0) entries += SettingEntry("Size", "${width}x${height}")
        if (performance.isNotBlank()) entries += SettingEntry("Performance", performance)
        if (!sharpness.isNaN()) entries += SettingEntry("Sharpness", trimNumber(sharpness))

        return entries
    }

    private fun trimNumber(v: Double): String {
        val s = v.toString()
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    private fun buildSummary(entries: List<SettingEntry>): String {
        if (entries.isEmpty()) return ""
        return entries.joinToString(", ") { "${it.key}: ${it.value}" }
    }
}
