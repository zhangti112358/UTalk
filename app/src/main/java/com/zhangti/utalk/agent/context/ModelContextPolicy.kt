package com.zhangti.utalk.agent.context

/** 只裁剪送给模型的视图；持久化历史从不经过这里。 */
class ModelContextPolicy(
    private val softBudget: Int = 32_000,
    private val recentFullTurns: Int = 2,
    private val maxToolResultChars: Int = 6_000,
) : ContextTransformer {
    override fun transform(context: AgentContextSnapshot): AgentContextSnapshot {
        val items = context.items
        val starts = items.indices.filter { items[it] is UserInputContext }
        if (starts.isEmpty()) return context

        val fixed = items.filter { it is SystemPromptContext || it is ToolAvailabilityContext }
        val turns = starts.mapIndexed { index, start ->
            items.subList(start, starts.getOrElse(index + 1) { items.size })
        }
        val projected = turns.map(::capToolResults).toMutableList()

        // 未达到预算时不碰历史。超出后从最早轮次开始移出工具链。
        val compactable = (turns.size - recentFullTurns).coerceAtLeast(0)
        for (index in 0 until compactable) {
            if (cost(fixed, projected) <= softBudget) break
            projected[index] = compactTurn(turns[index])
        }

        // 保留当前轮和最近轮；预算不足时整轮移出最早历史，不制造摘要。
        while (projected.size > 1 && cost(fixed, projected) > softBudget) projected.removeAt(0)
        return AgentContextSnapshot(fixed + projected.flatten())
    }

    private fun compactTurn(turn: List<AgentContextItem>): List<AgentContextItem> = buildList {
        turn.filterIsInstance<UserInputContext>().firstOrNull()?.let(::add)
        turn.filterIsInstance<AssistantOutputContext>()
            .lastOrNull { it.toolCalls.isEmpty() && !it.text.isNullOrBlank() }
            ?.let(::add)
    }

    private fun capToolResults(turn: List<AgentContextItem>): List<AgentContextItem> = turn.map { item ->
        if (item is ToolResultContext && item.content.length > maxToolResultChars) {
            item.copy(content = item.content.take(maxToolResultChars) + "\n[工具结果仅在模型上下文中截断；完整原文已保存在历史记录]")
        } else item
    }

    private fun cost(fixed: List<AgentContextItem>, turns: List<List<AgentContextItem>>): Int =
        (fixed + turns.flatten()).sumOf { item ->
            when (item) {
                is SystemPromptContext -> item.text.length
                is UserInputContext -> item.text.length
                is AssistantOutputContext -> (item.text?.length ?: 0) + item.toolCalls.sumOf { it.arguments.length + it.name.length }
                is ToolResultContext -> item.content.length
                is ImageContext -> 4_000
                is AssistantPlaybackContext -> item.fullResponse.length + item.spokenPrefix.length
                is ToolAvailabilityContext -> 1_000 // 给工具定义及参数 schema 留一个保守的估算额度。
            }
        }
}
