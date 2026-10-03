package com.zhangti.utalk.agent.debug

import com.zhangti.utalk.agent.context.ContextProjectionReport
import com.zhangti.utalk.agent.llm.LlmRequest
import com.zhangti.utalk.agent.llm.LlmUsage
import com.zhangti.utalk.agent.context.TokenEstimate
import org.json.JSONArray
import org.json.JSONObject

/** 记录组装完成的实际输入；图片使用文件引用，不复制 Base64 或鉴权信息。 */
object RequestSnapshotCodec {
    fun encode(request: LlmRequest, model: String, thinkingMode: String): String = JSONObject().apply {
        put("model", model)
        put("thinking", JSONObject().put("type", thinkingMode))
        put("stream_options", JSONObject().put("include_usage", true))
        put("messages", JSONArray().apply {
            request.messages.forEach { message -> put(JSONObject().apply {
                put("role", message.role.name.lowercase())
                put("content", message.content ?: JSONObject.NULL)
                message.toolCallId?.let { put("tool_call_id", it) }
                if (message.toolCalls.isNotEmpty()) put("tool_calls", JSONArray().apply {
                    message.toolCalls.forEach { call -> put(JSONObject().apply {
                        put("id", call.id); put("type", "function")
                        put("function", JSONObject().put("name", call.name).put("arguments", call.arguments))
                    }) }
                })
                if (message.images.isNotEmpty()) put("local_image_references", JSONArray().apply {
                    message.images.forEach { image -> put(JSONObject().put("path", image.path).put("mime_type", image.mimeType)) }
                })
            }) }
        })
        put("tools", JSONArray().apply {
            request.tools.forEach { tool -> put(JSONObject().put("type", "function").put("function",
                JSONObject().put("name", tool.name).put("description", tool.description).put("parameters", JSONObject(tool.schema)))) }
        })
        request.temperature?.let { put("temperature", it.toDouble()) }
        request.maxTokens?.let { put("max_completion_tokens", it) }
    }.toString()

    fun encode(report: ContextProjectionReport): String = JSONObject().apply {
        put("original_items", report.originalItems)
        put("included_items", report.includedItems)
        put("removed_by_type", JSONObject(report.removedByType))
        put("truncated_tool_call_ids", JSONArray(report.truncatedToolCallIds))
        report.budget?.let { budget ->
            put("token_budget", JSONObject().apply {
                put("counting", "保守估算，非服务端实际 Token 数")
                put("calibration_factor", budget.calibrationFactor)
                put("before", estimate(budget.before)); put("after", estimate(budget.after))
                put("soft_limit", budget.config.softLimit); put("tool_eviction_target", budget.config.toolEvictionTarget)
                put("dialogue_limit", budget.config.dialogueLimit); put("dialogue_target", budget.config.dialogueTarget)
                put("recent_full_turns", budget.config.recentFullTurns)
                put("model_limit", budget.config.modelLimit); put("hard_input_limit", budget.config.hardInputLimit)
                put("output_reserve", budget.config.outputReserve)
                put("decisions", JSONArray(budget.decisions))
            })
        }
    }.toString()

    fun encode(usage: LlmUsage): String = JSONObject().apply {
        put("prompt_tokens", usage.promptTokens); put("completion_tokens", usage.completionTokens)
        put("total_tokens", usage.totalTokens)
    }.toString()

    private fun estimate(value: TokenEstimate) = JSONObject().apply {
        put("total", value.total); put("system", value.system); put("tool_definitions", value.toolDefinitions)
        put("dialogue", value.dialogue); put("tool_history", value.toolHistory)
        put("images", value.images); put("other", value.other); put("overhead", value.overhead)
    }
}
