package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.context.AgentContextSnapshot
import com.zhangti.utalk.agent.context.ContextAssembler
import com.zhangti.utalk.agent.llm.LlmClient
import com.zhangti.utalk.agent.llm.LlmStream

/** 每一次模型 API 调用的唯一入口。 */
class AgentModelCaller(
    private val llm: LlmClient,
    private val assembler: ContextAssembler,
) {
    fun stream(context: AgentContextSnapshot): LlmStream =
        llm.stream(assembler.assemble(context))
}

