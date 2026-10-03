package com.zhangti.utalk.agent.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangti.utalk.agent.context.AssistantOutputContext
import com.zhangti.utalk.agent.context.ToolResultContext
import com.zhangti.utalk.agent.context.UserInputContext
import com.zhangti.utalk.agent.tool.local.DeviceLocation
import com.zhangti.utalk.agent.context.AssembledContext
import com.zhangti.utalk.agent.context.ContextProjectionReport
import com.zhangti.utalk.agent.llm.LlmMessage
import com.zhangti.utalk.agent.llm.LlmRequest
import com.zhangti.utalk.agent.llm.LlmRole
import com.zhangti.utalk.agent.llm.LlmToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteHistoryStoreTest {
    @Test fun versionOneUpgradePreservesFullHistoryAndAddsRequestSnapshots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "agent_debug_migration_test.db"
        context.deleteDatabase(name)
        val fullResult = "完整结果".repeat(3_000)
        try {
            SqliteHistoryStore(context, name).use { store ->
                store.append("old-session", 1, 1_000L, UserInputContext("查询地点"))
                store.append("old-session", 1, 1_001L, AssistantOutputContext(null,
                    listOf(LlmToolCall("call-id", "maps_search", "{\"query\":\"虹桥\"}"))))
                store.append("old-session", 1, 1_002L, ToolResultContext("call-id", "maps_search", fullResult, false))
                // 只回退隔离测试库，模拟升级前的 v1 数据。
                store.writableDatabase.execSQL("DROP TABLE model_requests")
                store.writableDatabase.version = 1
            }
            SqliteHistoryStore(context, name).use { store ->
                assertEquals("old-session", store.sessions(10).single().id)
                assertEquals(3, store.turns("old-session", 10).single().eventCount)
                val events = store.events("old-session", 1)
                assertEquals("{\"query\":\"虹桥\"}", events[1].toolCalls.single().arguments)
                assertEquals(fullResult, events[2].content)
                assertEquals("call-id", events[2].toolCallId)
                store.recordRequest("old-session", 1, AssembledContext(
                    LlmRequest(listOf(LlmMessage(LlmRole.USER, "查询地点"))),
                    ContextProjectionReport(3, 1, mapOf("ToolResultContext" to 1), emptyList()),
                ), "deepseek-flash", "disabled")
                val summary = store.requests("old-session", 10).single()
                assertEquals(1, summary.removedItems)
                assertTrue(store.request(summary.id)!!.payload.contains("查询地点"))
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun eventsSurviveReopenAndAreSearchableWithLocation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "agent_history_instrumented_test.db"
        context.deleteDatabase(name)
        try {
            SqliteHistoryStore(context, name).use { store ->
                store.append("session-a", 1, 1_000L, UserInputContext("昨天去虹桥机场"))
                store.append("session-a", 1, 1_001L, ToolResultContext("call", "map", "路线约 20 公里", false))
                store.append("session-a", 1, 1_002L, AssistantOutputContext("建议提前出发"))
                store.updateTurnLocation("session-a", 1, DeviceLocation(31.2, 121.3, 15f, "gps", 999L))
            }
            SqliteHistoryStore(context, name).use { reopened ->
                val byWords = reopened.search("虹桥", null, null)
                assertEquals(1, byWords.size)
                assertTrue(byWords.single().conversation.contains("路线约 20 公里"))
                assertEquals(31.2, byWords.single().latitude!!, 0.0001)
                assertTrue(reopened.search("虹桥", 2_000L, null).isEmpty())
                assertFalse(reopened.search("20 公里", null, null).isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }
}
