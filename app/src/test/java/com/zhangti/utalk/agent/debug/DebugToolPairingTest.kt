package com.zhangti.utalk.agent.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugToolPairingTest {
    @Test fun repeatedCallIdsAcrossModelOutputsDoNotOverwriteResults() {
        val first = event(1, "assistant", calls = listOf(DebugToolCall("call-0", "tool", "first")))
        val firstResult = event(2, "tool_result", callId = "call-0")
        val second = event(3, "assistant", calls = listOf(DebugToolCall("call-0", "tool", "second")))
        val secondResult = event(4, "tool_result", callId = "call-0")
        val pairs = pairToolResults(listOf(first, firstResult, second, secondResult))
        assertEquals(2L, pairs[1L to 0]!!.id)
        assertEquals(4L, pairs[3L to 0]!!.id)
    }

    private fun event(id: Long, kind: String, calls: List<DebugToolCall> = emptyList(), callId: String? = null) =
        DebugEvent(id, kind, 0, null, calls, callId, "tool", false, null, null, null, null, null, null)
}
