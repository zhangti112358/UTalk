package com.zhangti.utalk.agent.runtime

import com.zhangti.utalk.agent.bootstrap.ToolLoadReport
import com.zhangti.utalk.agent.bootstrap.TravelToolEnvironment
import com.zhangti.utalk.agent.context.ContextAssembler
import com.zhangti.utalk.agent.context.AssistantPlaybackContext
import com.zhangti.utalk.agent.context.InMemoryAgentContextStore
import com.zhangti.utalk.agent.context.SystemPromptContext
import com.zhangti.utalk.agent.llm.DeepSeekLlm
import com.zhangti.utalk.agent.tool.execution.CatalogToolInvoker
import java.io.Closeable

class TextAgentSession private constructor(
    private val llm: DeepSeekLlm,
    private val tools: TravelToolEnvironment,
    private val context: InMemoryAgentContextStore,
    private val loop: AgentLoop,
) : AgentConversation, Closeable {
    @Volatile private var currentRun: AgentCancellation? = null

    @Synchronized
    override fun send(text: String, listener: AgentEventListener): AgentCancellation {
        check(currentRun == null) { "Agent 正在运行" }
        val cancellation = AgentCancellation()
        currentRun = cancellation
        Thread({
            try {
                loop.runTurn(text, AgentEventListener { event ->
                    // 终态事件回调可能立即发起下一轮，先释放当前运行标记。
                    if (event is AgentEvent.Completed || event is AgentEvent.Failed || event is AgentEvent.Cancelled) {
                        clearRun(cancellation)
                    }
                    listener.onEvent(event)
                }, cancellation)
            } finally {
                clearRun(cancellation)
            }
        }, "TextAgentLoop").start()
        return cancellation
    }

    @Synchronized
    private fun clearRun(run: AgentCancellation) {
        if (currentRun === run) currentRun = null
    }

    override fun cancel() {
        currentRun?.cancel()
    }

    override val isRunning: Boolean get() = currentRun != null

    override fun recordPlaybackInterruption(spokenPrefix: String, fullResponse: String) {
        context.append(
            AssistantPlaybackContext(
                spokenPrefix = spokenPrefix,
                fullResponse = fullResponse,
            )
        )
    }

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
            return TextAgentSession(llm, environment, context, loop) to report
        }

        private val SYSTEM_PROMPT = """
            你是 UTalk 的语音出行助手。回答应准确、非常简短，并优先使用工具核实实时信息。
            不要使用 Markdown、表格、标题、项目符号或特殊排版，只输出适合直接朗读的自然语言。
            最重要的结论必须放在最前面的 1 到 3 句话里。除非用户明确要求详情，否则先给结论和必要行动建议，把次要细节留给后续追问。
            地点搜索、地址解析、距离和路线规划统一使用高德地图工具，不使用滴滴地图工具。高德地图的常用工具可以直接使用。
            航班、机票、酒店、天气、打车以及地图扩展能力没有直接显示时，必须先调用 search_tools 搜索并加载，不能猜测工具名。
            search_tools 返回的工具会在下一轮自动可用；收到结果后应直接调用合适的工具继续任务。
            滴滴打车只走接口下单，不生成打车链接。用户本轮明确要求“直接打车”“叫一辆快车”“确认下单”等，即已授权本轮下单；不要再问一次确认。
            下单前先用高德地图工具核实起终点坐标，再调用 ride__taxi_estimate 获取可用车型、价格和 traceId，选出用户指定的车型，随后调用 ride__taxi_create_order；不要猜测车型标识或预估 ID，也不要重复发单。
            用户只是询价、比较车型或讨论路线时不能下单。若缺少起点、终点或无法确定车型，先询问必要信息。取消订单当前不可执行。
            不要伪造工具结果。
            当信息不足以调用工具时，先向用户询问必要信息。
        """.trimIndent()
    }
}
