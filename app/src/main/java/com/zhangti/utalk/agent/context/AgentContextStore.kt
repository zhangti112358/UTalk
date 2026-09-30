package com.zhangti.utalk.agent.context

/** 当前会话上下文存储；永久历史由独立的 RecordingContextStore/HistoryStore 记录。 */
interface AgentContextStore {
    fun append(item: AgentContextItem)
    fun snapshot(): AgentContextSnapshot
    fun clear()
}

class InMemoryAgentContextStore(
    initialItems: List<AgentContextItem> = emptyList(),
) : AgentContextStore {
    private val items = initialItems.toMutableList()

    @Synchronized
    override fun append(item: AgentContextItem) {
        // 同一工具只需要插入上下文一次。
        if (item is ToolAvailabilityContext && items.any {
                it is ToolAvailabilityContext && it.toolId == item.toolId
            }
        ) return
        items += item
    }

    @Synchronized
    override fun snapshot(): AgentContextSnapshot = AgentContextSnapshot(items.toList())

    @Synchronized
    override fun clear() = items.clear()
}
