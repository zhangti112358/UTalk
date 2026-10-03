package com.zhangti.utalk.agent.context

/** 有状态的会话投影视图；卸载边界不回退，完整历史保持不变。 */
class ModelContextPolicy(
    softBudget: Int = 64_000,
    recentFullTurns: Int = 3,
    private val estimator: ContextTokenEstimator = ConservativeTokenEstimator(),
    val config: ContextBudgetConfig = ContextBudgetConfig(
        softLimit = softBudget.toLong(), toolEvictionTarget = minOf(48_000, softBudget).toLong(),
        recentFullTurns = recentFullTurns,
    ),
    private val protections: List<ContextProtection> = listOf(RideContextProtection()),
) : ContextTransformer {
    private val evictedToolTurns = mutableSetOf<Int>()
    private val evictedTextTurns = mutableSetOf<Int>()
    private var firstUser: UserInputContext? = null
    var lastDecision: List<String> = emptyList()
        private set

    @Synchronized
    override fun transform(context: AgentContextSnapshot): AgentContextSnapshot {
        val items = context.items
        val starts = items.indices.filter { items[it] is UserInputContext }
        if (starts.isEmpty()) return context
        val first = items[starts.first()] as UserInputContext
        if (firstUser !== first) {
            evictedToolTurns.clear(); evictedTextTurns.clear(); firstUser = first
        }
        // 固定项只出现一次，不能再留在轮次内部重复计数。
        val fixed = items.filter { it is SystemPromptContext || it is ToolAvailabilityContext }
            .distinctBy { if (it is ToolAvailabilityContext) it.toolId else it }
        val turns = starts.mapIndexed { index, start ->
            items.subList(start, starts.getOrElse(index + 1) { items.size })
                .filterNot { it is SystemPromptContext || it is ToolAvailabilityContext }
        }
        val protected = mutableSetOf(turns.lastIndex)
        protected += (turns.lastIndex - config.recentFullTurns).coerceAtLeast(0)..turns.lastIndex
        protections.forEach { protected += it.protectedTurns(turns) }
        // 最近的原图继续支持追问；旧原图随历史工具链卸载。
        turns.indexOfLast { turn -> turn.any { it is ImageContext } }.takeIf { it >= 0 }?.let { protected += it }
        val reasons = mutableListOf<String>()
        val projected = turns.mapIndexed { index, turn ->
            when {
                index in evictedTextTurns -> emptyList()
                index in evictedToolTurns -> dialogueOnly(turn)
                else -> turn
            }
        }.toMutableList()
        fun estimate() = estimator.estimate(fixed + projected.flatten())

        if (estimate().total > config.softLimit) {
            for (index in projected.indices) {
                if (estimate().total <= config.toolEvictionTarget) break
                if (index in protected || index in evictedTextTurns || index in evictedToolTurns) continue
                if (projected[index].none { it is ToolResultContext || it is ImageContext || it is AssistantPlaybackContext ||
                        it is AssistantOutputContext && it.toolCalls.isNotEmpty() }) continue
                evictedToolTurns += index
                projected[index] = dialogueOnly(turns[index])
                reasons += "第 ${index + 1} 轮：移出旧工具链，保留用户输入与模型回复"
            }
        }
        if (estimate().dialogue > config.dialogueLimit) {
            for (index in projected.indices) {
                if (estimate().dialogue <= config.dialogueTarget) break
                if (index in protected || index in evictedTextTurns) continue
                evictedTextTurns += index
                projected[index] = emptyList()
                reasons += "第 ${index + 1} 轮：旧对话文本超过阈值，整轮移出模型视图"
            }
        }
        val finalEstimate = estimate()
        if (finalEstimate.fixed > config.softLimit) reasons += "固定提示词/工具定义已超过软阈值；没有通过删除最近对话抵账"
        if (finalEstimate.total > config.softLimit) reasons += "受保护的最近对话或未完成任务使输入暂时超过软阈值"
        if (finalEstimate.dialogue > config.dialogueTarget && evictedTextTurns.isNotEmpty())
            reasons += "最近对话/未完成任务受保护，文本未强行降至目标"
        lastDecision = reasons
        return AgentContextSnapshot(fixed + projected.flatten())
    }

    private fun dialogueOnly(turn: List<AgentContextItem>): List<AgentContextItem> = turn.mapNotNull {
        when (it) {
            is UserInputContext -> it
            is AssistantOutputContext -> it.text?.takeIf(String::isNotBlank)?.let { text -> AssistantOutputContext(text) }
            else -> null
        }
    }
}
