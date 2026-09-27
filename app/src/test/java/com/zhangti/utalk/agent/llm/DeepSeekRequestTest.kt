package com.zhangti.utalk.agent.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.core.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekRequestTest {
    @Test
    fun `thinking is explicitly disabled with and without tools`() {
        val llm = DeepSeekLlm(apiKey = "test-key")
        try {
            val expected = JsonValue.fromJsonNode(ObjectMapper().valueToTree(mapOf("type" to "disabled")))
            val messages = listOf(LlmMessage(LlmRole.USER, "现在几点"))
            for (tools in listOf(emptyList(), listOf(LlmTool("device_current_time", "时间", mapOf("type" to "object"))))) {
                val params = llm.buildParams(LlmRequest(messages, tools))
                assertEquals(expected, params._additionalBodyProperties()["thinking"])
            }
        } finally { llm.close() }
    }
}
