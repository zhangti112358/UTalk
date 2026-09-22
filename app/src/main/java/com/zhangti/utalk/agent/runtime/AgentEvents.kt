package com.zhangti.utalk.agent.runtime

sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class ToolStarted(val name: String) : AgentEvent
    data class ToolFinished(val name: String, val isError: Boolean) : AgentEvent
    data class Completed(val text: String) : AgentEvent
    data class Failed(val message: String) : AgentEvent
    data object Cancelled : AgentEvent
}

fun interface AgentEventListener {
    fun onEvent(event: AgentEvent)
}

