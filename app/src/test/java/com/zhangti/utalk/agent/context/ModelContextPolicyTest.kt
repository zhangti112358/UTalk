package com.zhangti.utalk.agent.context

import com.zhangti.utalk.agent.llm.LlmToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelContextPolicyTest {
    private val call = LlmToolCall("call-1", "maps_search", "{\"query\":\"上海\"}")

    @Test fun olderTurnsKeepOnlyUserAndFinalAnswer() {
        val original = AgentContextSnapshot(listOf(
            SystemPromptContext("system"),
            ToolAvailabilityContext("maps_search", ToolAvailabilitySource.CORE),
            UserInputContext("旧问题"),
            AssistantOutputContext(null, listOf(call)),
            ToolResultContext("call-1", "maps_search", "很长的工具结果", false),
            AssistantOutputContext("旧回答"),
            UserInputContext("中间问题"), AssistantOutputContext("中间回答"),
            UserInputContext("新问题"), AssistantOutputContext("新回答"),
        ))
        val visible = ModelContextPolicy(softBudget = 1_035, recentFullTurns = 2).transform(original).items
        assertTrue(visible.contains(UserInputContext("旧问题")))
        assertTrue(visible.contains(AssistantOutputContext("旧回答")))
        assertFalse(visible.any { it is ToolResultContext })
        assertFalse(visible.any { it is AssistantOutputContext && it.toolCalls.isNotEmpty() })
        assertEquals(10, original.items.size) // 原始记录不被裁剪。
    }

    @Test fun budgetDropsOldTurnsButKeepsCurrentAndToolAvailability() {
        val original = AgentContextSnapshot(listOf(
            SystemPromptContext("s"),
            ToolAvailabilityContext("tool", ToolAvailabilitySource.DISCOVERED),
            UserInputContext("最早的问题"), AssistantOutputContext("最早的回答"),
            UserInputContext("当前问题"), AssistantOutputContext("当前回答"),
        ))
        val visible = ModelContextPolicy(softBudget = 1_012, recentFullTurns = 1).transform(original).items
        assertFalse(visible.contains(UserInputContext("最早的问题")))
        assertTrue(visible.contains(UserInputContext("当前问题")))
        assertTrue(visible.any { it is ToolAvailabilityContext && it.toolId == "tool" })
    }

    @Test fun toolResultIsTruncatedOnlyInModelView() {
        val result = ToolResultContext("id", "tool", "abcdefghij", false)
        val original = AgentContextSnapshot(listOf(UserInputContext("问题"), result))
        val visible = ModelContextPolicy(softBudget = 100_000, maxToolResultChars = 4).transform(original).items
        assertTrue((visible.last() as ToolResultContext).content.startsWith("abcd"))
        assertEquals("abcdefghij", result.content)
    }
}
