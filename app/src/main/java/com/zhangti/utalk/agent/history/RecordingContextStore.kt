package com.zhangti.utalk.agent.history

import com.zhangti.utalk.agent.context.AgentContextItem
import com.zhangti.utalk.agent.context.AgentContextSnapshot
import com.zhangti.utalk.agent.context.AgentContextStore
import com.zhangti.utalk.agent.context.UserInputContext
import com.zhangti.utalk.agent.tool.local.CurrentLocationSource
import com.zhangti.utalk.agent.tool.local.DeviceLocation
import java.util.UUID
import kotlinx.coroutines.runBlocking

/** 同步写入当前会话与永久日志；定位单独异步补齐，不拖慢模型响应。 */
class RecordingContextStore(
    private val active: AgentContextStore,
    private val history: HistoryStore,
    private val locationSource: CurrentLocationSource? = null,
    private val now: () -> Long = System::currentTimeMillis,
    val sessionId: String = UUID.randomUUID().toString(),
) : AgentContextStore {
    private var turn = 0
    private val turnLocations = mutableMapOf<Int, DeviceLocation>()

    @Synchronized
    override fun append(item: AgentContextItem) {
        if (item is UserInputContext) {
            turn++
            turnLocations.keys.removeAll { it < turn - 1 }
        }
        history.append(sessionId, turn, now(), item)
        turnLocations[turn]?.let { history.updateTurnLocation(sessionId, turn, it) }
        active.append(item)
        val source = locationSource
        if (item is UserInputContext && source != null) {
            val turnToLocate = turn
            Thread({
                runCatching { runBlocking { source.read() } }
                    .onSuccess { fix -> runCatching { applyLocation(turnToLocate, fix) } }
            }, "AgentTurnLocation").apply { isDaemon = true; start() }
        }
    }

    override fun snapshot(): AgentContextSnapshot = active.snapshot()

    @Synchronized
    private fun applyLocation(turn: Int, fix: DeviceLocation) {
        turnLocations[turn] = fix
        history.updateTurnLocation(sessionId, turn, fix)
    }

    /** 清空模型会话不清除数据库记录。 */
    override fun clear() = active.clear()
}
