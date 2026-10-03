package com.zhangti.utalk.agent.context

import com.fasterxml.jackson.databind.ObjectMapper
import com.zhangti.utalk.agent.llm.LlmTool
import kotlin.math.ceil

/** 可替换的请求前估算；不是 tokenizer，也不承诺等于服务端计数。 */
fun interface ContextTokenEstimator {
    fun estimate(items: List<AgentContextItem>): TokenEstimate
}

data class TokenEstimate(
    val system: Long = 0, val toolDefinitions: Long = 0, val dialogue: Long = 0,
    val toolHistory: Long = 0, val images: Long = 0, val other: Long = 0, val overhead: Long = 0,
) {
    val total: Long get() = system + toolDefinitions + dialogue + toolHistory + images + other + overhead
    val fixed: Long get() = system + toolDefinitions
}

/** 会话内校准只上调、不下调：实际输入高于估算时加大余量；缺失统计时维持原值。 */
class CalibratedTokenEstimator(private val base: ContextTokenEstimator) : ContextTokenEstimator {
    @Volatile var factor: Double = 1.0
        private set
    override fun estimate(items: List<AgentContextItem>): TokenEstimate {
        val value = base.estimate(items)
        val scale = factor
        fun scaled(tokens: Long) = ceil(tokens * scale).toLong()
        return TokenEstimate(scaled(value.system), scaled(value.toolDefinitions), scaled(value.dialogue),
            scaled(value.toolHistory), scaled(value.images), scaled(value.other), scaled(value.overhead))
    }
    @Synchronized
    fun observe(estimated: Long, actual: Long) {
        if (estimated > 0 && actual > estimated) {
            factor = (factor * actual.toDouble() / estimated * 1.1).coerceAtMost(4.0)
        }
    }
}

/** 对实际说明/schema 计数，工具去重；文本保守加 15%，每图预留 1,536 tokens。 */
class ConservativeTokenEstimator(
    private val resolveTool: (String) -> LlmTool? = { null },
) : ContextTokenEstimator {
    private val mapper = ObjectMapper()
    override fun estimate(items: List<AgentContextItem>): TokenEstimate {
        var system = 0L; var definitions = 0L; var dialogue = 0L
        var tools = 0L; var images = 0L; var other = 0L; var messages = 0L
        val seen = mutableSetOf<String>()
        items.forEach { item ->
            when (item) {
                is SystemPromptContext -> { system += text(item.text); messages++ }
                is ToolAvailabilityContext -> if (seen.add(item.toolId)) resolveTool(item.toolId)?.let {
                    definitions += text(it.name) + text(it.description) + text(mapper.writeValueAsString(it.schema)) + 24
                }
                is UserInputContext -> { dialogue += text(item.text); messages++ }
                is AssistantOutputContext -> {
                    dialogue += text(item.text.orEmpty()); messages++
                    tools += item.toolCalls.sumOf { text(it.name) + text(it.id) + text(it.arguments) + 16 }
                }
                is ToolResultContext -> { tools += text(item.content) + text(item.toolCallId); messages++ }
                is ImageContext -> { images += 1_536; other += 96; messages++ }
                is AssistantPlaybackContext -> {
                    other += text(item.spokenPrefix) + text(item.fullResponse) + 64; messages++
                }
            }
        }
        return TokenEstimate(system, definitions, dialogue, tools, images, other, messages * 12 + 128)
    }

    private fun text(value: String): Long {
        var units = 0.0
        var index = 0
        while (index < value.length) {
            val cp = value.codePointAt(index)
            units += when {
                cp <= 127 -> 0.4
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN -> 0.8
                else -> 1.0
            }
            index += Character.charCount(cp)
        }
        return ceil(units * 1.15).toLong()
    }
}

data class ContextBudgetConfig(
    val softLimit: Long = 64_000, val toolEvictionTarget: Long = 48_000,
    val dialogueLimit: Long = 16_000, val dialogueTarget: Long = 8_000,
    val recentFullTurns: Int = 3, val modelLimit: Long = 1_000_000,
    val outputReserve: Int = 8_192, val hardSafetyMargin: Long = 32_000,
) {
    init {
        require(softLimit > 0 && toolEvictionTarget in 1..softLimit)
        require(dialogueLimit > 0 && dialogueTarget in 1..dialogueLimit && recentFullTurns >= 0)
        require(outputReserve > 0 && hardSafetyMargin >= 0 && modelLimit > outputReserve + hardSafetyMargin)
    }
    val hardInputLimit: Long get() = modelLimit - outputReserve - hardSafetyMargin
}
