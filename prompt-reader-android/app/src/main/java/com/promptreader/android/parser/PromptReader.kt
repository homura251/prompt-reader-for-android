package com.promptreader.android.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.promptreader.android.novelai.NovelAiStealthDecoder
import com.promptreader.android.png.PngTextChunkReader
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream

object PromptReader {
    fun parse(context: Context, uri: Uri): PromptParseResult {
        val resolver = context.contentResolver

        // Detect format by magic bytes
        val header = resolver.openInputStream(uri)?.use { it.readNBytesCompat(16) } ?: ByteArray(0)
        val isPng = header.size >= 8 && header.sliceArray(0 until 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        )
        val isJpeg = header.size >= 2 && (header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xFF) == 0xD8
        val isWebp = header.size >= 12 && String(header, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(header, 8, 4, Charsets.ISO_8859_1) == "WEBP"

        val evidence = mutableListOf<ParseEvidence>()

        return when {
            isPng -> {
                evidence += ParseEvidence("File", "PNG")
                parsePng(context, uri, evidence)
            }
            isJpeg -> {
                evidence += ParseEvidence("File", "JPEG")
                parseExifLike(context, uri, "JPEG", evidence)
            }
            isWebp -> {
                evidence += ParseEvidence("File", "WEBP")
                parseExifLike(context, uri, "WEBP", evidence)
            }
            else -> PromptParseResult(
                tool = "Unknown",
                positive = "",
                negative = "",
                setting = "",
                raw = "Unsupported file",
                rawParts = listOf(RawPart("Error", "Unsupported file")),
                detectionPath = "Unknown",
                detectionEvidence = evidence + ParseEvidence("Error", "Unsupported file"),
            )
        }
    }

    private fun parsePng(context: Context, uri: Uri, evidence: MutableList<ParseEvidence>): PromptParseResult {
        val resolver = context.contentResolver
        val textMap = resolver.openInputStream(uri)?.use { PngTextChunkReader.readTextChunks(it) } ?: emptyMap()

        val parameters = textMap["parameters"]
        val prompt = textMap["prompt"]
        val workflow = textMap["workflow"]
        val comment = textMap["Comment"]
        val software = textMap["Software"]
        val description = textMap["Description"]

        val presentKeys = listOf("parameters", "prompt", "workflow", "Comment", "Software", "Description")
            .filter { textMap.containsKey(it) }
            .joinToString(",")
        val base = "PNG -> tEXt($presentKeys)"

        evidence += ParseEvidence("PNG tEXt", presentKeys.ifBlank { "(none)" })

        fun rawPartsForPng(vararg items: Pair<String, String?>): List<RawPart> {
            val parts = ArrayList<RawPart>()
            for ((k, v) in items) {
                if (v.isNullOrBlank()) continue
                val trimmed = v.trimStart()
                val mime = if (trimmed.startsWith("{") || trimmed.startsWith("[")) "application/json" else "text/plain"
                parts += RawPart("PNG tEXt: $k", v, mime)
            }
            return parts
        }

        // SwarmUI in PNG: parameters contains sui_image_params
        if (!parameters.isNullOrBlank() && parameters.contains("sui_image_params")) {
            val r = SwarmUiParser.parse(parameters)
            return PromptParseResult(
                tool = "StableSwarmUI",
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = rawPartsForPng("parameters" to parameters),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> SwarmUiParser(parameters:sui_image_params)",
                detectionEvidence = evidence + ParseEvidence("Parser", "SwarmUiParser(parameters:sui_image_params)"),
            )
        }

        // NovelAI legacy (must run before Fooocus; NovelAI Comment is also JSON)
        if (software == "NovelAI" && !description.isNullOrBlank() && !comment.isNullOrBlank()) {
            val r = NovelAiParser.parseLegacy(description, comment)
            return PromptParseResult(
                tool = "NovelAI",
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = rawPartsForPng("Software" to software, "Description" to description, "Comment" to comment),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> NovelAiParser(legacy:Software+Description+Comment)",
                detectionEvidence = evidence + ParseEvidence("Parser", "NovelAiParser(legacy:Software+Description+Comment)"),
            )
        }

        // Fooocus in PNG: Comment is JSON (but not every JSON comment is Fooocus)
        if (!comment.isNullOrBlank() && looksLikeFooocusComment(comment)) {
            val r = FooocusParser.parse(comment)
            return PromptParseResult(
                tool = "Fooocus",
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = rawPartsForPng("Comment" to comment),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> FooocusParser(Comment:json+negative_prompt)",
                detectionEvidence = evidence + ParseEvidence("Parser", "FooocusParser(Comment:json+negative_prompt)"),
            )
        }

        // ComfyUI workflow-only (some tools only embed workflow, not prompt graph)
        if (prompt.isNullOrBlank() && !workflow.isNullOrBlank()) {
            val r = ComfyUiParser.parseWorkflow(workflowText = workflow)
            return PromptParseResult(
                tool = r.tool,
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = rawPartsForPng("workflow" to workflow),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> ComfyUiParser(workflow)",
                detectionEvidence = evidence + ParseEvidence("Parser", "ComfyUiParser(workflow)"),
            )
        }

        // ComfyUI
        if (!prompt.isNullOrBlank()) {
            val r = ComfyUiParser.parse(promptJsonText = prompt, workflowText = workflow)
            return PromptParseResult(
                tool = r.tool,
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = rawPartsForPng("prompt" to prompt, "workflow" to workflow),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> ComfyUiParser(prompt${if (!workflow.isNullOrBlank()) "+workflow" else ""})",
                detectionEvidence = evidence + ParseEvidence("Parser", "ComfyUiParser(prompt${if (!workflow.isNullOrBlank()) "+workflow" else ""})"),
            )
        }

        // A1111
        if (!parameters.isNullOrBlank()) {
            extractComfyUiWorkflowJson(parameters)?.let { workflowJson ->
                val r = ComfyUiParser.parseWorkflow(workflowText = workflowJson)
                return PromptParseResult(
                    tool = r.tool,
                    positive = r.positive,
                    negative = r.negative,
                    setting = r.setting,
                    raw = r.raw,
                    rawParts = rawPartsForPng("parameters" to parameters),
                    settingEntries = r.settingEntries,
                    settingDetail = r.settingDetail,
                    detectionPath = "$base -> ComfyUiParser(parameters:workflow-json)",
                    detectionEvidence = evidence + ParseEvidence("Parser", "ComfyUiParser(parameters:workflow-json)"),
                )
            }

            val r = A1111Parser.parse(parameters)
            val tool = if (textMap.containsKey("prompt")) "ComfyUI (A1111 compatible)" else "A1111 webUI"
            return PromptParseResult(
                tool = tool,
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = rawPartsForPng("parameters" to parameters),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> A1111Parser(parameters)",
                detectionEvidence = evidence + ParseEvidence("Parser", "A1111Parser(parameters)"),
            )
        }

        // NovelAI stealth: decode bitmap and try
        val stealth = tryDecodeStealth(context, uri)
        if (stealth != null) {
            val r = NovelAiParser.parseStealth(stealth)
            return PromptParseResult(
                tool = "NovelAI",
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = listOf(RawPart("NovelAI stealth JSON", stealth.toString(), "application/json")),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> NovelAiParser(stealth:alpha-lsb)",
                detectionEvidence = evidence + ParseEvidence("Parser", "NovelAiParser(stealth:alpha-lsb)"),
            )
        }

        return PromptParseResult(
            tool = "Unknown",
            positive = "",
            negative = "",
            setting = "",
            raw = textMap.toString(),
            rawParts = rawPartsForPng(
                "parameters" to parameters,
                "prompt" to prompt,
                "workflow" to workflow,
                "Comment" to comment,
                "Software" to software,
                "Description" to description,
            ),
            detectionPath = "$base -> Unknown",
            detectionEvidence = evidence + ParseEvidence("Parser", "Unknown"),
        )
    }

    private fun looksLikeFooocusComment(comment: String): Boolean {
        val trimmed = comment.trim()
        if (!trimmed.startsWith("{")) return false
        val obj = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull() as? JSONObject ?: return false
        // Fooocus typically uses these keys.
        if (!obj.has("negative_prompt")) return false
        // Prompt might be named 'prompt' in Fooocus.
        return obj.has("prompt") || obj.has("styles") || obj.has("performance")
    }

    private fun parseExifLike(
        context: Context,
        uri: Uri,
        formatLabel: String,
        evidence: MutableList<ParseEvidence>,
    ): PromptParseResult {
        val resolver = context.contentResolver
        val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }

        val userComment = exif?.getAttribute(ExifInterface.TAG_USER_COMMENT)
        val software = exif?.getAttribute(ExifInterface.TAG_SOFTWARE)
        val imageDescription = exif?.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)

        val present = buildList {
            if (!userComment.isNullOrBlank()) add("UserComment")
            if (!software.isNullOrBlank()) add("Software")
            if (!imageDescription.isNullOrBlank()) add("ImageDescription")
        }.joinToString(",")
        val base = "$formatLabel -> EXIF($present)"

        evidence += ParseEvidence("EXIF", present.ifBlank { "(none)" })

        fun rawPartsForExif(): List<RawPart> {
            val parts = ArrayList<RawPart>()
            fun add(title: String, text: String?) {
                if (text.isNullOrBlank()) return
                val trimmed = text.trimStart()
                val mime = if (trimmed.startsWith("{") || trimmed.startsWith("[")) "application/json" else "text/plain"
                parts += RawPart("$formatLabel EXIF: $title", text, mime)
            }
            add("Software", software)
            add("ImageDescription", imageDescription)
            add("UserComment", userComment)
            return parts
        }

        // SwarmUI in JPEG: user comment contains sui_image_params JSON
        if (!userComment.isNullOrBlank() && userComment.contains("sui_image_params")) {
            val r = SwarmUiParser.parse(userComment)
            return PromptParseResult(
                tool = "StableSwarmUI",
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = rawPartsForExif(),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> SwarmUiParser(UserComment:sui_image_params)",
                detectionEvidence = evidence + ParseEvidence("Parser", "SwarmUiParser(UserComment:sui_image_params)"),
            )
        }

        // ComfyUI workflow-only in EXIF user comment (some exporters store workflow JSON here)
        if (!userComment.isNullOrBlank()) {
            extractComfyUiWorkflowJson(userComment)?.let { workflowJson ->
                val r = ComfyUiParser.parseWorkflow(workflowText = workflowJson)
                return PromptParseResult(
                    tool = r.tool,
                    positive = r.positive,
                    negative = r.negative,
                    setting = r.setting,
                    raw = r.raw,
                    rawParts = rawPartsForExif(),
                    settingEntries = r.settingEntries,
                    settingDetail = r.settingDetail,
                    detectionPath = "$base -> ComfyUiParser(UserComment:workflow-json)",
                    detectionEvidence = evidence + ParseEvidence("Parser", "ComfyUiParser(UserComment:workflow-json)"),
                )
            }
        }

        // Fooocus: some variants store JSON in comment/usercomment
        if (!userComment.isNullOrBlank() && userComment.trim().startsWith("{") && userComment.contains("negative_prompt")) {
            val r = FooocusParser.parse(userComment)
            return PromptParseResult(
                tool = "Fooocus",
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = rawPartsForExif(),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> FooocusParser(UserComment:json+negative_prompt)",
                detectionEvidence = evidence + ParseEvidence("Parser", "FooocusParser(UserComment:json+negative_prompt)"),
            )
        }

        // A1111 / EasyDiffusion: usercomment often contains the full text
        if (!userComment.isNullOrBlank()) {
            val r = A1111Parser.parse(userComment)
            return PromptParseResult(
                tool = "A1111 webUI",
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = rawPartsForExif(),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> A1111Parser(UserComment)",
                detectionEvidence = evidence + ParseEvidence("Parser", "A1111Parser(UserComment)"),
            )
        }

        // NovelAI stealth may exist in WEBP: try decode
        val stealth = tryDecodeStealth(context, uri)
        if (stealth != null) {
            val r = NovelAiParser.parseStealth(stealth)
            return PromptParseResult(
                tool = "NovelAI",
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = listOf(RawPart("NovelAI stealth JSON", stealth.toString(), "application/json")),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> NovelAiParser(stealth:alpha-lsb)",
                detectionEvidence = evidence + ParseEvidence("Parser", "NovelAiParser(stealth:alpha-lsb)"),
            )
        }

        // NovelAI legacy sometimes shows up via EXIF tags.
        if (software == "NovelAI" && !imageDescription.isNullOrBlank()) {
            val comment = userComment ?: "{}"
            val r = NovelAiParser.parseLegacy(imageDescription, comment)
            return PromptParseResult(
                tool = "NovelAI",
                positive = r.positive,
                negative = r.negative,
                setting = r.setting,
                raw = r.raw,
                rawParts = rawPartsForExif(),
                settingEntries = r.settingEntries,
                settingDetail = r.settingDetail,
                detectionPath = "$base -> NovelAiParser(legacy:Software+ImageDescription)",
                detectionEvidence = evidence + ParseEvidence("Parser", "NovelAiParser(legacy:Software+ImageDescription)"),
            )
        }

        return PromptParseResult(
            tool = "Unknown",
            positive = "",
            negative = "",
            setting = "",
            raw = "No readable metadata",
            rawParts = rawPartsForExif(),
            detectionPath = "$base -> Unknown",
            detectionEvidence = evidence + ParseEvidence("Parser", "Unknown"),
        )
    }

    private fun extractComfyUiWorkflowJson(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        val candidate = text.substring(start).trim()
        val obj = runCatching { JSONTokener(candidate).nextValue() }.getOrNull() as? JSONObject ?: return null
        val nodes = obj.optJSONArray("nodes")
        val links = obj.optJSONArray("links")
        return if (nodes != null && links != null) candidate else null
    }

    private fun tryDecodeStealth(context: Context, uri: Uri): JSONObject? {
        val resolver = context.contentResolver
        val bitmap: Bitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        return NovelAiStealthDecoder.tryDecode(bitmap)
    }

    private fun InputStream.readNBytesCompat(n: Int): ByteArray {
        val buf = ByteArray(n)
        var readTotal = 0
        while (readTotal < n) {
            val r = this.read(buf, readTotal, n - readTotal)
            if (r <= 0) break
            readTotal += r
        }
        return if (readTotal == n) buf else buf.copyOf(readTotal)
    }
}
