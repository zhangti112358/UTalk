package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.llm.LlmStream

class AgentCancellation {
    @Volatile private var cancelled = false
    @Volatile private var stream: LlmStream? = null

    val isCancelled: Boolean get() = cancelled

    @Synchronized
    fun attach(stream: LlmStream) {
        this.stream = stream
        if (cancelled) stream.close()
    }

    @Synchronized
    fun detach(stream: LlmStream) {
        if (this.stream === stream) this.stream = null
    }

    fun cancel() {
        cancelled = true
        val active = synchronized(this) { stream.also { stream = null } } ?: return
        // 关闭网络流可能触发 socket I/O；UI 点击取消时不能在主线程执行。
        Thread({ runCatching { active.close() } }, "AgentStreamCancel").start()
    }
}
