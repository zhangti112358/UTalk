package com.zhangti.utalk.agent.llm

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.core.http.StreamResponse
import com.openai.models.FunctionDefinition
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageToolCall
import com.openai.models.chat.completions.ChatCompletionTool
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import java.util.concurrent.LinkedBlockingQueue

/**
 * DeepSeek LLM 客户端（OpenAI 兼容协议，基于 openai-java 官方风格库）。
 *
 * - 流式返回：后台线程拉取，[DeepSeekStream.next] 阻塞取块；
 * - 中断：[DeepSeekStream.close] 关闭底层 HTTP 流，立即停止；
 * - 只做纯粹模型调用：上下文、system prompt、工具清单全部由上层通过 [LlmRequest] 传入。
 */
class DeepSeekLlm(
    private val apiKey: String = com.zhangti.utalk.AppConfig.instance.deepseekApiKey,
    private val baseUrl: String = com.zhangti.utalk.AppConfig.instance.deepseekBaseUrl,
    private val model: String = com.zhangti.utalk.AppConfig.instance.deepseekModel,
) : LlmClient, AutoCloseable {

    private val client: OpenAIClient = OpenAIOkHttpClient.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .build()

    override fun stream(request: LlmRequest): LlmStream =
        DeepSeekStream(buildParams(request))

    override fun close() = client.close()

    /** 把上层请求映射为 OpenAI 协议参数。 */
    private fun buildParams(request: LlmRequest): ChatCompletionCreateParams =
        ChatCompletionCreateParams.builder().apply {
            model(model)
            request.messages.forEach { message ->
                when (message.role) {
                    LlmRole.SYSTEM -> addSystemMessage(message.content ?: "")
                    LlmRole.USER -> addUserMessage(message.content ?: "")
                    LlmRole.ASSISTANT -> {
                        if (message.toolCalls.isEmpty()) {
                            addAssistantMessage(message.content ?: "")
                        } else {
                            addMessage(assistantWithToolCalls(message))
                        }
                    }
                    LlmRole.TOOL -> addMessage(
                        ChatCompletionToolMessageParam.builder()
                            .content(message.content ?: "")
                            .toolCallId(message.toolCallId ?: "")
                            .build()
                    )
                }
            }
            if (request.tools.isNotEmpty()) {
                tools(request.tools.map { tool ->
                    ChatCompletionTool.ofFunction(
                        ChatCompletionFunctionTool.builder()
                            .function(
                                FunctionDefinition.builder()
                                    .name(tool.name)
                                    .description(tool.description)
                                    .putAdditionalProperty(
                                        "parameters",
                                        JsonValue.fromJsonNode(MAPPER.valueToTree(tool.schema)),
                                    )
                                    .build()
                            )
                            .build()
                    )
                })
            }
            request.temperature?.let { temperature(it.toDouble()) }
            request.maxTokens?.let { maxCompletionTokens(it.toLong()) }
        }.build()

    /** 携带工具调用历史的助手消息。 */
    private fun assistantWithToolCalls(message: LlmMessage): ChatCompletionAssistantMessageParam =
        ChatCompletionAssistantMessageParam.builder()
            .content(
                message.content?.let {
                    ChatCompletionAssistantMessageParam.Content.ofText(it)
                }
            )
            .toolCalls(
                message.toolCalls.map { call ->
                    ChatCompletionMessageToolCall.ofFunction(
                        ChatCompletionMessageFunctionToolCall.builder()
                            .id(call.id)
                            .function(
                                ChatCompletionMessageFunctionToolCall.Function.builder()
                                    .name(call.name)
                                    .arguments(call.arguments)
                                    .build()
                            )
                            .build()
                    )
                }
            )
            .build()

    /** 一次流式会话：后台线程拉流，next() 阻塞取块，close() 中断。 */
    private inner class DeepSeekStream(
        private val params: ChatCompletionCreateParams,
    ) : LlmStream {

        private val queue = LinkedBlockingQueue<Any>(QUEUE_CAPACITY)

        @Volatile
        private var closed = false

        @Volatile
        private var streamResponse: StreamResponse<ChatCompletionChunk>? = null

        private val worker = Thread({
            var sr: StreamResponse<ChatCompletionChunk>? = null
            try {
                sr = client.chat().completions().createStreaming(params)
                streamResponse = sr
                if (closed) {
                    sr.close()
                    return@Thread
                }
                sr.stream().use { chunks ->
                    chunks.forEach { chunk ->
                        if (!closed) queue.put(chunk.toLlmChunk())
                    }
                }
                queue.offer(END)
            } catch (t: Throwable) {
                if (!closed) queue.offer(t)
            } finally {
                if (!closed) sr?.close()
            }
        }, "DeepSeekLlmStream").apply {
            isDaemon = true
            start()
        }

        override fun next(): LlmChunk? {
            if (closed) return null
            return when (val item = queue.take()) {
                END -> null
                is LlmChunk -> item
                is Throwable -> throw RuntimeException("模型调用失败", item)
                else -> null
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            streamResponse?.close() // 中断底层 HTTP 流
            queue.clear()
            queue.offer(END) // 唤醒可能阻塞在 take() 的调用方
        }
    }

    companion object {
        private const val TAG = "DeepSeekLlm"
        private val MAPPER = ObjectMapper()
        private val END = Any()
        private const val QUEUE_CAPACITY = 64
    }
}

/** 服务端块 → 上层块。 */
private fun ChatCompletionChunk.toLlmChunk(): LlmChunk {
    val choice = choices().firstOrNull()
    val delta = choice?.delta()
    val text = delta?.content()?.orElse(null)
    val toolCalls = delta?.toolCalls()?.orElse(null)?.mapIndexed { index, call ->
        val fn = call.function().orElse(null)
        LlmToolCallDelta(
            index = index,
            id = call.id().orElse(null),
            name = fn?.name()?.orElse(null),
            arguments = fn?.arguments()?.orElse(null),
        )
    }.orEmpty()
    val finishReason = choice?.finishReason()?.orElse(null)?.toString()
    return LlmChunk(text = text, toolCalls = toolCalls, finishReason = finishReason)
}
