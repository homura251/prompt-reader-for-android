package com.promptreader.android.parser

import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal port of sd_prompt_reader/format/comfyui.py.
 *
 * This is not a perfect clone, but it follows the same strategy:
 * - find end nodes (SaveImage / KSampler variants)
 * - traverse upstream following linked inputs
 * - extract positive/negative text via CLIPTextEncode nodes
 */
object ComfyUiParser {
    private val KSAMPLER_TYPES = setOf("KSampler", "KSamplerAdvanced", "KSampler (Efficient)")
    private val SAVE_IMAGE_TYPES = setOf("SaveImage", "Image Save", "SDPromptSaver")
    private val CLIP_TEXT_TYPES = setOf("CLIPTextEncode", "CLIPTextEncodeSDXL", "CLIPTextEncodeSDXLRefiner")

    data class Result(
        val positive: String,
        val negative: String,
        val setting: String,
        val raw: String,
        val tool: String = "ComfyUI",
    )

    fun parse(promptJsonText: String, workflowText: String? = null, width: Int? = null, height: Int? = null): Result {
        val promptObj = JSONObject(promptJsonText)

        val endNodeIds = promptObj.keys().asSequence()
            .filter { id ->
                val node = promptObj.optJSONObject(id) ?: return@filter false
                val classType = node.optString("class_type", "")
                SAVE_IMAGE_TYPES.contains(classType) || KSAMPLER_TYPES.contains(classType)
            }
            .toList()

        var positive = ""
        var negative = ""
        var setting = ""

        // Pick the traversal that visits the most nodes.
        var bestVisited = emptySet<String>()
        for (endId in endNodeIds) {
            val ctx = TraverseContext(promptObj)
            ctx.traverse(endId)
            if (ctx.visited.size > bestVisited.size) {
                bestVisited = ctx.visited
                positive = ctx.positive
                negative = ctx.negative
                setting = buildSetting(ctx.flow, width, height)
            }
        }

        val rawParts = mutableListOf<String>()
        if (positive.isNotBlank()) rawParts += positive.trim()
        if (negative.isNotBlank()) rawParts += negative.trim()
        rawParts += promptObj.toString()
        if (!workflowText.isNullOrBlank()) rawParts += workflowText

        return Result(
            positive = positive.trim(),
            negative = negative.trim(),
            setting = setting,
            raw = rawParts.joinToString("\n"),
        )
    }

    private fun buildSetting(flow: Map<String, Any?>, width: Int?, height: Int?): String {
        val steps = flow["steps"]?.toString()?.takeIf { it != "null" }
        val sampler = flow["sampler_name"]?.toString()?.trim('"', '\'')?.takeIf { it != "null" }
        val cfg = flow["cfg"]?.toString()?.takeIf { it != "null" }
        val seed = (flow["seed"] ?: flow["noise_seed"])?.toString()?.takeIf { it != "null" }
        val model = flow["ckpt_name"]?.toString()?.trim('"', '\'')?.takeIf { it != "null" }

        val parts = ArrayList<String>()
        if (steps != null) parts += "Steps: $steps"
        if (sampler != null) parts += "Sampler: $sampler"
        if (cfg != null) parts += "CFG scale: $cfg"
        if (seed != null) parts += "Seed: $seed"
        if (width != null && height != null) parts += "Size: ${width}x${height}"
        if (model != null) parts += "Model: $model"
        return parts.joinToString(", ")
    }

    private class TraverseContext(private val prompt: JSONObject) {
        val visited = LinkedHashSet<String>()
        val flow = LinkedHashMap<String, Any?>()

        var positive: String = ""
        var negative: String = ""

        fun traverse(nodeId: String) {
            if (visited.contains(nodeId)) return
            visited.add(nodeId)

            val node = prompt.optJSONObject(nodeId) ?: return
            val classType = node.optString("class_type", "")
            val inputs = node.optJSONObject("inputs") ?: JSONObject()

            when {
                SAVE_IMAGE_TYPES.contains(classType) -> {
                    // images: [<nodeId>, index]
                    val images = inputs.optJSONArray("images")
                    val upstream = firstLink(images)
                    if (upstream != null) traverse(upstream)
                }

                KSAMPLER_TYPES.contains(classType) -> {
                    // Collect scalar params + follow upstream links.
                    for (key in inputs.keys()) {
                        val v = inputs.opt(key)
                        when (key) {
                            "positive" -> {
                                val upstream = firstLink(v)
                                if (upstream != null) {
                                    val text = traverseToText(upstream)
                                    if (text != null) positive = text
                                }
                            }

                            "negative" -> {
                                val upstream = firstLink(v)
                                if (upstream != null) {
                                    val text = traverseToText(upstream)
                                    if (text != null) negative = text
                                }
                            }

                            else -> {
                                val upstream = firstLink(v)
                                if (upstream != null) {
                                    // Follow for dict params like model loaders.
                                    traverse(upstream)
                                } else {
                                    flow[key] = v
                                }
                            }
                        }
                    }

                    // Some params are nested upstream outputs (e.g. model loader returns ckpt_name).
                    // Our generic traversal will record loader inputs; additionally, try to pull ckpt_name.
                    val modelUpstream = firstLink(inputs.opt("model"))
                    if (modelUpstream != null) {
                        val modelNode = prompt.optJSONObject(modelUpstream)
                        val modelInputs = modelNode?.optJSONObject("inputs")
                        val ckpt = modelInputs?.optString("ckpt_name", null)
                        if (!ckpt.isNullOrBlank()) flow["ckpt_name"] = ckpt
                    }
                }

                CLIP_TEXT_TYPES.contains(classType) -> {
                    // CLIPTextEncode: text can be string or link
                    val textVal = inputs.opt("text") ?: inputs.opt("text_g") ?: inputs.opt("text_l")
                    val upstream = firstLink(textVal)
                    if (upstream != null) {
                        val text = traverseToText(upstream)
                        if (text != null) {
                            // Caller decides if it's positive/negative.
                        }
                    }
                }

                else -> {
                    // Generic pass-through: try to follow the first link-like input.
                    for (key in inputs.keys()) {
                        val upstream = firstLink(inputs.opt(key))
                        if (upstream != null) {
                            traverse(upstream)
                            break
                        }
                    }
                }
            }
        }

        private fun traverseToText(nodeId: String): String? {
            traverse(nodeId)
            val node = prompt.optJSONObject(nodeId) ?: return null
            val classType = node.optString("class_type", "")
            val inputs = node.optJSONObject("inputs") ?: JSONObject()

            return when (classType) {
                "CLIPTextEncode" -> {
                    val text = inputs.opt("text")
                    when (text) {
                        is String -> text
                        else -> {
                            val upstream = firstLink(text)
                            if (upstream != null) traverseToText(upstream) else null
                        }
                    }
                }

                else -> {
                    // Many custom nodes output a string into CLIPTextEncode via a link; follow heuristically.
                    // If current node has a string-y input named positive/text/etc, use it.
                    val candidates = listOf("text", "positive", "prompt", "string")
                    for (k in candidates) {
                        val v = inputs.opt(k)
                        if (v is String) return v
                    }
                    // Otherwise follow any link.
                    for (k in inputs.keys()) {
                        val upstream = firstLink(inputs.opt(k))
                        if (upstream != null) return traverseToText(upstream)
                    }
                    null
                }
            }
        }

        private fun firstLink(value: Any?): String? {
            return when (value) {
                is JSONArray -> {
                    // ComfyUI link: ["nodeId", outputIndex]
                    if (value.length() > 0) value.optString(0, null) else null
                }
                is List<*> -> {
                    value.firstOrNull()?.toString()
                }
                else -> null
            }
        }
    }
}
