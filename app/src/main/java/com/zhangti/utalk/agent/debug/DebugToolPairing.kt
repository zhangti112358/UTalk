package com.zhangti.utalk.agent.debug

/** 在一次模型输出后的结果范围内配对，避免不同模型调用复用调用 ID 时串线。 */
internal fun pairToolResults(events: List<DebugEvent>): Map<Pair<Long, Int>, DebugEvent> = buildMap {
    events.forEachIndexed { index, event ->
        if (event.toolCalls.isEmpty()) return@forEachIndexed
        val end = (index + 1 until events.size).firstOrNull { events[it].kind == "assistant" } ?: events.size
        val candidates = events.subList(index + 1, end).filter { it.kind == "tool_result" }.toMutableList()
        event.toolCalls.forEachIndexed { callIndex, call ->
            val match = candidates.indexOfFirst { it.toolCallId == call.id && it.toolName == call.name }
            if (match >= 0) put(event.id to callIndex, candidates.removeAt(match))
        }
    }
}
