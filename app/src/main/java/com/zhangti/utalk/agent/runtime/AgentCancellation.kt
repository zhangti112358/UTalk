package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.llm.LlmStream

class AgentCancellation {
    @Volatile private var cancelled = false
    @Volatile private var stream: LlmStream? = null

    val isCancelled: Boolean get() = cancelled

    fun attach(stream: LlmStream) {
        this.stream = stream
        if (cancelled) stream.close()
    }

    fun detach(stream: LlmStream) {
        if (this.stream === stream) this.stream = null
    }

    fun cancel() {
        cancelled = true
        stream?.close()
    }
}

