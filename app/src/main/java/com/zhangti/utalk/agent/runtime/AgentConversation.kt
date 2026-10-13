package com.zhangti.utalk.agent.runtime

/** 文字 UI 与语音编排共同依赖的会话接口。 */
interface AgentConversation : AutoCloseable {
    val isRunning: Boolean
    fun send(text: String, listener: AgentEventListener): AgentCancellation
    fun cancel()
    fun recordPlaybackInterruption(spokenPrefix: String, fullResponse: String)
    /** 语音协议诊断仅进入本机 Debug 历史，不进入模型上下文。 */
    fun recordVoiceDiagnostic(content: String) {}
}
