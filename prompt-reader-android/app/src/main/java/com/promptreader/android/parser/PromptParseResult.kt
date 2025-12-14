package com.promptreader.android.parser

data class PromptParseResult(
    val tool: String,
    val positive: String,
    val negative: String,
    val setting: String,
    val raw: String,
)
