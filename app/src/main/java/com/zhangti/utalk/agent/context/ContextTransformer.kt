package com.zhangti.utalk.agent.context

/**
 * 上下文变换扩展点。后续的消息摘要、工具结果压缩、过期工具卸载都实现为 Transformer，
 * 不需要修改 Agent 循环或模型客户端。
 */
fun interface ContextTransformer {
    fun transform(context: AgentContextSnapshot): AgentContextSnapshot
}

class ContextPipeline(
    private val transformers: List<ContextTransformer> = emptyList(),
) {
    fun apply(context: AgentContextSnapshot): AgentContextSnapshot =
        transformers.fold(context) { current, transformer -> transformer.transform(current) }
}

