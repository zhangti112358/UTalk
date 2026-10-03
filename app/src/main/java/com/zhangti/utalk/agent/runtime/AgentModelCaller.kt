package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.context.AgentContextSnapshot
import com.zhangti.utalk.agent.context.ContextAssembler
import com.zhangti.utalk.agent.context.AssembledContext
import com.zhangti.utalk.agent.llm.LlmClient
import com.zhangti.utalk.agent.llm.LlmStream
import com.zhangti.utalk.agent.llm.LlmUsage

/** 每一次模型 API 调用的唯一入口。 */
class AgentModelCaller(
    private val llm: LlmClient,
    private val assembler: ContextAssembler,
    private val onUsage: ((LlmUsage) -> Unit)? = null,
    private val onRequest: ((AssembledContext) -> Unit)? = null,
) {
    fun stream(context: AgentContextSnapshot): LlmStream {
        val assembled = assembler.assembleWithReport(context)
        onRequest?.invoke(assembled)
        val delegate = llm.stream(assembled.request)
        return object : LlmStream {
            private var recorded = false
            override fun next() = delegate.next()?.also { chunk ->
                chunk.usage?.let { usage ->
                    if (!recorded) {
                        recorded = true
                        assembler.observeUsage(assembled.projection.budget?.after, usage.promptTokens)
                        // 调试统计失败不影响 Agent 的正常输出。
                        runCatching { onUsage?.invoke(usage) }
                    }
                }
            }
            override fun close() = delegate.close()
        }
    }
}
