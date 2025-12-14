package com.promptreader.android.parser

object A1111Parser {
    data class Result(
        val positive: String,
        val negative: String,
        val setting: String,
        val raw: String,
    )

    fun parse(raw: String): Result {
        if (raw.isBlank()) return Result("", "", "", "")

        val stepsIndex = raw.indexOf("\nSteps:")
        var positive = ""
        var negative = ""
        var setting = ""

        if (stepsIndex != -1) {
            positive = raw.substring(0, stepsIndex).trim()
            setting = raw.substring(stepsIndex + 1).trim()
        } else {
            positive = raw.trim()
        }

        val negMarker = "\nNegative prompt:"
        val negIndex = raw.indexOf(negMarker)
        if (negIndex != -1) {
            positive = raw.substring(0, negIndex).trim()
            if (stepsIndex != -1) {
                negative = raw.substring(negIndex + negMarker.length, stepsIndex).trim()
            } else {
                negative = raw.substring(negIndex + negMarker.length).trim()
            }
        }

        return Result(positive, negative, setting, raw.trim())
    }
}
