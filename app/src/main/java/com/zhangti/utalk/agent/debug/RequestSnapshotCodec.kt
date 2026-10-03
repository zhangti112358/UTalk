package com.zhangti.utalk.agent.debug

import com.zhangti.utalk.agent.context.ContextProjectionReport
import com.zhangti.utalk.agent.llm.LlmRequest
import org.json.JSONArray
import org.json.JSONObject

/** 记录组装完成的实际输入；图片使用文件引用，不复制 Base64 或鉴权信息。 */
object RequestSnapshotCodec {
    fun encode(request: LlmRequest, model: String, thinkingMode: String): String = JSONObject().apply {
        put("model", model)
        put("thinking", JSONObject().put("type", thinkingMode))
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
    }.toString()
}
