# LLM 模型调用

纯模型流式调用（OpenAI 协议，DeepSeek 模型），与上层解耦：上下文、system prompt、工具注册均由上层管理，本层只负责发送与流式返回。

## 参考

- 官方库：https://github.com/openai-java/openai-java（已克隆到 `third/openai-java`）
- 依赖：`com.openai:openai-java:4.58.0`（含 okhttp 客户端与 Jackson）

# 对外接口

### `LlmClient`（接口）

```kotlin
fun stream(request: LlmRequest): LlmStream   // 发起流式调用，不阻塞（建连在后台线程）
```

实现：`DeepSeekLlm(apiKey/baseUrl/model 默认走 AppConfig)`，可 `close()` 释放底层客户端。

### `LlmStream`（一次流式会话）

```kotlin
fun next(): LlmChunk?   // 阻塞取下一块；null = 流结束
fun close()            // 中断本次调用（关闭底层 HTTP 流，幂等）
```

### `LlmTypes`（上层可见的类型）

```kotlin
LlmRequest(messages: List<LlmMessage>, tools: List<LlmTool> = [], temperature: Float? = null, maxTokens: Int? = null)
LlmMessage(role: SYSTEM/USER/ASSISTANT/TOOL, content: String?, toolCalls: List<LlmToolCall>, toolCallId: String?)
LlmTool(name, description, schema: Map<String, Any?>)          // schema 为 JSON Schema 的 Map
LlmChunk(text: String?, toolCalls: List<LlmToolCallDelta>, finishReason: String?)
LlmToolCallDelta(index, id?, name?, arguments?)                // 流式分片，由上层按 index 累积
```

## 用法

```kotlin
val llm = DeepSeekLlm()
val stream = llm.stream(
    LlmRequest(
        messages = listOf(
            LlmMessage(LlmRole.SYSTEM, "你是助手"),
            LlmMessage(LlmRole.USER, "你好"),
        ),
        tools = listOf(LlmTool("echo", "回显", mapOf("type" to "object", "properties" to emptyMap<String, Any>()))),
    )
)
while (true) {
    val chunk = stream.next() ?: break
    chunk.text?.let { print(it) }
    // 中断：stream.close()（可从任意线程调用）
}
```

注意：中断是「关闭底层 HTTP 流」的硬中断，立即停止模型生成与计费；工具调用分片需上层按 index/id 累积成完整参数。
