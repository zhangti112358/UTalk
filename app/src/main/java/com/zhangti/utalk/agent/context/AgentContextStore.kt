package com.zhangti.utalk.agent.context

/** 会话上下文存储；首版使用内存实现，之后可替换为持久化存储。 */
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

