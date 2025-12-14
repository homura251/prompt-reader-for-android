package com.promptreader.core.model

data class ParsedPrompt(
    val status: ParseStatus,
    val tool: String = "",
    val format: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val isSdxl: Boolean = false,
    val positive: String = "",
    val negative: String = "",
    val positiveSdxl: Map<String, String> = emptyMap(),
    val negativeSdxl: Map<String, String> = emptyMap(),
    val setting: String = "",
    val parameter: Map<String, String> = ParameterKeys.keys.associateWith { "" },
    val raw: String = "",
    val propsJson: String = "",
)
