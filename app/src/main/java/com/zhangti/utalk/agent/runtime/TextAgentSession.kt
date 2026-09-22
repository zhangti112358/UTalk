package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.bootstrap.ToolLoadReport
import com.zhangti.utalk.agent.bootstrap.TravelToolEnvironment
import com.zhangti.utalk.agent.context.ContextAssembler
import com.zhangti.utalk.agent.context.InMemoryAgentContextStore
import com.zhangti.utalk.agent.context.SystemPromptContext
import com.zhangti.utalk.agent.llm.DeepSeekLlm
import com.zhangti.utalk.agent.tool.execution.CatalogToolInvoker
import java.io.Closeable

class TextAgentSession private constructor(
    private val llm: DeepSeekLlm,
    private val tools: TravelToolEnvironment,
    private val loop: AgentLoop,
) : Closeable {
    @Volatile private var currentRun: AgentCancellation? = null

    fun send(text: String, listener: AgentEventListener): AgentCancellation {
        check(currentRun == null) { "Agent 正在运行" }
        val cancellation = AgentCancellation()
        currentRun = cancellation
        Thread({
            try {
                loop.runTurn(text, listener, cancellation)
            } finally {
                currentRun = null
            }
        }, "TextAgentLoop").start()
        return cancellation
    }

    fun cancel() = currentRun?.cancel()

    override fun close() {
        cancel()
        tools.close()
        llm.close()
    }

    companion object {
        suspend fun create(
            onProgress: (String) -> Unit = {},
        ): Pair<TextAgentSession, ToolLoadReport> {
            val context = InMemoryAgentContextStore(
                listOf(SystemPromptContext(SYSTEM_PROMPT))
            )
            val (environment, report) = TravelToolEnvironment.load(context, onProgress = onProgress)
            val llm = DeepSeekLlm()
            val assembler = ContextAssembler(environment.catalog)
            val loop = AgentLoop(
                context = context,
                model = AgentModelCaller(llm, assembler),
                tools = CatalogToolInvoker(environment.catalog),
            )
            return TextAgentSession(llm, environment, loop) to report
        }

        private val SYSTEM_PROMPT = """
            你是 UTalk 的出行助手。回答应准确、简洁，并优先使用工具核实实时信息。
            高德地图的常用地点搜索、地址解析、距离和路线规划工具可以直接使用。
            航班、机票、酒店、天气、打车以及地图扩展能力没有直接显示时，必须先调用 search_tools 搜索并加载，不能猜测工具名。
            search_tools 返回的工具会在下一轮自动可用；收到结果后应直接调用合适的工具继续任务。
            不要伪造工具结果。创建或取消真实订单属于有副作用操作，未经明确确认不得执行。
            当信息不足以调用工具时，先向用户询问必要信息。
        """.trimIndent()
    }
}

