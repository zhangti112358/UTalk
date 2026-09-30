package com.zhangti.utalk.agent.history

import com.zhangti.utalk.agent.context.AgentContextItem
import com.zhangti.utalk.agent.tool.local.DeviceLocation

/** 完整事件日志与检索的抽象；与送给模型的短上下文分离。 */
interface HistoryStore {
    fun append(sessionId: String, turn: Int, occurredAtMillis: Long, item: AgentContextItem)
    fun updateTurnLocation(sessionId: String, turn: Int, location: DeviceLocation)
    fun search(query: String, afterMillis: Long?, beforeMillis: Long?, limit: Int = 5): List<HistoryMatch>
}

data class HistoryMatch(
    val sessionId: String,
    val turn: Int,
    val occurredAtMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val conversation: String,
)
