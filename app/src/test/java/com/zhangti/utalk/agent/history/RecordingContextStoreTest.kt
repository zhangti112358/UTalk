package com.zhangti.utalk.agent.history

import com.zhangti.utalk.agent.context.AgentContextItem
import com.zhangti.utalk.agent.context.AssistantOutputContext
import com.zhangti.utalk.agent.context.InMemoryAgentContextStore
import com.zhangti.utalk.agent.context.UserInputContext
import com.zhangti.utalk.agent.tool.local.DeviceLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingContextStoreTest {
    @Test fun recordsFullEventsWithoutRestoringOldSession() {
        val history = FakeHistory()
        val first = RecordingContextStore(InMemoryAgentContextStore(), history, now = { 123L }, sessionId = "a")
        first.append(UserInputContext("你好"))
        first.append(AssistantOutputContext("你好"))
        assertEquals(listOf(1, 1), history.events.map { it.second })

        val second = RecordingContextStore(InMemoryAgentContextStore(), history, now = { 456L }, sessionId = "b")
        assertEquals(emptyList<AgentContextItem>(), second.snapshot().items)
        second.append(UserInputContext("新会话"))
        assertEquals(listOf("a", "a", "b"), history.events.map { it.first })
    }

    private class FakeHistory : HistoryStore {
        val events = mutableListOf<Triple<String, Int, AgentContextItem>>()
        override fun append(sessionId: String, turn: Int, occurredAtMillis: Long, item: AgentContextItem) {
            events += Triple(sessionId, turn, item)
        }
        override fun updateTurnLocation(sessionId: String, turn: Int, location: DeviceLocation) = Unit
        override fun search(query: String, afterMillis: Long?, beforeMillis: Long?, limit: Int) = emptyList<HistoryMatch>()
    }
}
