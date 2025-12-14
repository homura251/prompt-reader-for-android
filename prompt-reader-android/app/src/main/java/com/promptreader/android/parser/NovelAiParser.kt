package com.promptreader.android.parser

import org.json.JSONObject

object NovelAiParser {
    data class Result(
        val positive: String,
        val negative: String,
        val setting: String,
        val settingEntries: List<SettingEntry>,
        val settingDetail: String,
        val raw: String,
    )

    fun parseLegacy(description: String, commentJson: String): Result {
        val positive = description.trim()
        val data = runCatching { JSONObject(commentJson) }.getOrNull()
        if (data == null) {
            val raw = listOf(positive, commentJson.trim()).filter { it.isNotBlank() }.joinToString("\n")
            return Result(
                positive = positive,
                negative = "",
                setting = "",
                settingEntries = emptyList(),
                settingDetail = "",
                raw = raw,
            )
        }

        val negative = data.optString("uc", "").trim()
        val entries = buildNovelAiSettingEntries(data)
        val setting = buildSummary(entries)

        val detailObj = JSONObject(data.toString()).apply {
            remove("prompt")
            remove("uc")
        }
        val settingDetail = if (detailObj.length() > 0) detailObj.toString(2) else ""
        val raw = listOf(positive, negative, data.toString()).filter { it.isNotBlank() }.joinToString("\n")

        return Result(positive, negative, setting, entries, settingDetail, raw)
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

        val entries = buildNovelAiSettingEntries(merged)
        val setting = buildSummary(entries)

        val detailObj = JSONObject(merged.toString()).apply {
            remove("prompt")
            remove("uc")
            remove("Description")
        }
        val settingDetail = if (detailObj.length() > 0) detailObj.toString(2) else ""
        val raw = listOf(positive, negative, merged.toString()).filter { it.isNotBlank() }.joinToString("\n")

        return Result(positive, negative, setting, entries, settingDetail, raw)
    }

    private fun buildNovelAiSettingEntries(data: JSONObject): List<SettingEntry> {
        val entries = ArrayList<SettingEntry>()

        val sampler = data.optString("sampler", "").trim()
        val steps = data.optInt("steps", -1)
        val scale = data.optDouble("scale", Double.NaN)
        val seed = runCatching { data.getLong("seed") }.getOrNull()
        val width = data.optInt("width", -1)
        val height = data.optInt("height", -1)
        val cfgRescale = data.optDouble("cfg_rescale", Double.NaN)
        val noiseSchedule = data.optString("noise_schedule", "").trim()
        val strength = data.optDouble("strength", Double.NaN)

        if (steps >= 0) entries += SettingEntry("Steps", steps.toString())
        if (sampler.isNotBlank()) entries += SettingEntry("Sampler", sampler)
        if (!scale.isNaN()) entries += SettingEntry("CFG scale", trimNumber(scale))
        if (seed != null) entries += SettingEntry("Seed", seed.toString())
        if (width > 0 && height > 0) entries += SettingEntry("Size", "${width}x${height}")
        if (!cfgRescale.isNaN()) entries += SettingEntry("CFG rescale", trimNumber(cfgRescale))
        if (noiseSchedule.isNotBlank()) entries += SettingEntry("Noise schedule", noiseSchedule)
        if (!strength.isNaN()) entries += SettingEntry("Strength", trimNumber(strength))

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
