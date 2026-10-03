package com.zhangti.utalk.agent.context

/** 跨轮任务保护扩展点；返回需要保留完整内容的轮次索引（从 0 开始）。 */
fun interface ContextProtection {
    fun protectedTurns(turns: List<List<AgentContextItem>>): Set<Int>
}

/** 预估成功后保留该轮，直至下单/取消成功；失败仍保留，便于后续重试。 */
class RideContextProtection : ContextProtection {
    override fun protectedTurns(turns: List<List<AgentContextItem>>): Set<Int> {
        var pending: Int? = null
        turns.forEachIndexed { index, items -> items.forEach { item ->
            if (item is ToolResultContext) when (item.toolName) {
                "ride__taxi_estimate" -> if (!item.isError) pending = index
                "ride__taxi_create_order", "ride__taxi_cancel_order" -> if (!item.isError) pending = null
            }
        } }
        return setOfNotNull(pending)
    }
}
