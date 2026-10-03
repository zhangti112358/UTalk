package com.zhangti.utalk.agent.debug

import kotlinx.coroutines.flow.StateFlow

/** 调试只读接口，独立于模型上下文与历史搜索工具。 */
interface DebugRepository {
    val changes: StateFlow<Long>
    fun sessions(limit: Int): List<DebugSession>
    fun turns(sessionId: String, limit: Int): List<DebugTurn>
    fun events(sessionId: String, turn: Int): List<DebugEvent>
    fun requests(sessionId: String, limit: Int): List<DebugRequestSummary>
    fun request(id: Long): DebugRequest?
}

data class DebugSession(val id: String, val startedAt: Long, val lastEventAt: Long, val turns: Int, val preview: String)
data class DebugTurn(val number: Int, val startedAt: Long, val eventCount: Int, val preview: String)
data class DebugToolCall(val id: String, val name: String, val arguments: String)
data class DebugEvent(
    val id: Long, val kind: String, val occurredAt: Long, val content: String?,
    val toolCalls: List<DebugToolCall>, val toolCallId: String?, val toolName: String?, val isError: Boolean,
    val imagePath: String?, val capturedAt: String?,
    val latitude: Double?, val longitude: Double?, val accuracyMeters: Float?, val locationAt: Long?,
)
data class DebugRequestSummary(
    val id: Long, val turn: Int, val occurredAt: Long, val messageCount: Int, val toolCount: Int,
    val removedItems: Int, val truncatedResults: Int,
)
data class DebugRequest(val summary: DebugRequestSummary, val payload: String, val projection: String, val usage: String? = null)
