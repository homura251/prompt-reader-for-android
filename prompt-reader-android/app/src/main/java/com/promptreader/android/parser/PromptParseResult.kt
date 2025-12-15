package com.promptreader.android.parser

data class SettingEntry(
    val key: String,
    val value: String,
)

data class RawPart(
    val title: String,
    val text: String,
    val mime: String = "text/plain",
)

data class ParseEvidence(
    val stage: String,
    val detail: String,
)

data class PromptParseResult(
    val tool: String,
    val positive: String,
    val negative: String,
    val setting: String,
    val raw: String,
    val rawParts: List<RawPart> = emptyList(),
    val settingEntries: List<SettingEntry> = emptyList(),
    val settingDetail: String = "",
    val detectionPath: String = "",
    val detectionEvidence: List<ParseEvidence> = emptyList(),
)
