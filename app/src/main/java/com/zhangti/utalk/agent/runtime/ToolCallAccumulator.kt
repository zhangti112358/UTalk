package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.llm.LlmToolCall
import com.zhangti.utalk.agent.llm.LlmToolCallDelta

internal class ToolCallAccumulator {
    private data class Pending(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder(),
    )

    private val pending = sortedMapOf<Int, Pending>()

    fun add(deltas: List<LlmToolCallDelta>) {
        deltas.forEach { delta ->
            val call = pending.getOrPut(delta.index) { Pending() }
            delta.id?.let { call.id = it }
            delta.name?.let { call.name = it }
            delta.arguments?.let { call.arguments.append(it) }
        }
    }

    fun build(): List<LlmToolCall> = pending.values.mapNotNull { call ->
        if (call.id.isBlank() || call.name.isBlank()) null
        else LlmToolCall(call.id, call.name, call.arguments.toString())
    }
}

