package com.promptreader.android.parser

data class SettingEntry(
    val key: String,
    val value: String,
)

data class PromptParseResult(
    val tool: String,
    val positive: String,
    val negative: String,
    val setting: String,
    val raw: String,
    val settingEntries: List<SettingEntry> = emptyList(),
    val settingDetail: String = "",
    val detectionPath: String = "",
)
