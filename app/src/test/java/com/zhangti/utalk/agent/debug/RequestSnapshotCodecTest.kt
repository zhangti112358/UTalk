package com.zhangti.utalk.agent.debug

import com.zhangti.utalk.agent.llm.LlmImage
import com.zhangti.utalk.agent.llm.LlmMessage
import com.zhangti.utalk.agent.llm.LlmRequest
import com.zhangti.utalk.agent.llm.LlmRole
import com.zhangti.utalk.agent.llm.LlmTool
import com.zhangti.utalk.agent.llm.LlmToolCall
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RequestSnapshotCodecTest {
    @Test fun snapshotPreservesArgumentsAndSchemasWithoutReadingImageBytes() {
        val arguments = "{\"query\":\"虹桥\",\"limit\":3}"
        val request = LlmRequest(listOf(
            LlmMessage(LlmRole.SYSTEM, "系统提示词"),
            LlmMessage(LlmRole.ASSISTANT, toolCalls = listOf(LlmToolCall("call-1", "maps_search", arguments))),
            LlmMessage(LlmRole.USER, "照片", images = listOf(LlmImage("/nonexistent/photo.jpg"))),
        ), tools = listOf(LlmTool("maps_search", "地图", mapOf("type" to "object", "properties" to mapOf(
            "query" to mapOf("type" to "string"))))))
        val encoded = RequestSnapshotCodec.encode(request, "deepseek-flash", "disabled")
        val json = JSONObject(encoded)
        assertEquals(arguments, json.getJSONArray("messages").getJSONObject(1).getJSONArray("tool_calls")
            .getJSONObject(0).getJSONObject("function").getString("arguments"))
        assertEquals("/nonexistent/photo.jpg", json.getJSONArray("messages").getJSONObject(2)
            .getJSONArray("local_image_references").getJSONObject(0).getString("path"))
        assertEquals("string", json.getJSONArray("tools").getJSONObject(0).getJSONObject("function")
            .getJSONObject("parameters").getJSONObject("properties").getJSONObject("query").getString("type"))
        assertFalse(encoded.contains("base64"))
    }
}
