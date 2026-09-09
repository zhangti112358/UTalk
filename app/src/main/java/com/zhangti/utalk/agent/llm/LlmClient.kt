package com.zhangti.utalk.agent.llm

/**
 * LLM 客户端抽象：发起一次流式调用。
 * 上层只依赖本接口与 [com.zhangti.utalk.agent.llm.LlmTypes] 里的类型，不感知具体模型/厂商。
 */
interface LlmClient {

    /** 发起流式调用；返回前不阻塞（建连在后台线程）。 */
    fun stream(request: LlmRequest): LlmStream
}

/**
 * 一次流式会话。
 *
 * - [next] 阻塞直到下一块内容；返回 null 表示流结束；
 * - [close] 中断本次调用（关闭底层 HTTP 流，立即停止计费与回调）。
 */
interface LlmStream : AutoCloseable {

    /** 取下一块；流结束返回 null。 */
    fun next(): LlmChunk?

    /** 中断本次调用，幂等。 */
    override fun close()
}
