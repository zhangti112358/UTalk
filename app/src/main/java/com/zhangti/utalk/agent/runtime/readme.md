# Agent 运行时

负责驱动一次完整的“模型推理 → 工具调用 → 继续推理”循环，并向 UI 输出结构化运行事件。

## 文件

- `AgentLoop.kt`：Agent 的核心循环，持续消费模型流、执行工具并把工具结果写回上下文，直到得到最终回答或达到调用上限。
- `AgentConversation.kt`：定义文字 UI 和语音会话共同依赖的发送、取消与播报打断记录接口。
- `AgentModelCaller.kt`：每次模型 API 调用的统一入口，先通过上下文组装器生成请求，再交给 `LlmClient`。
- `AgentEvents.kt`：定义流式文本、工具开始/结束、完成、失败和取消等运行事件。
- `AgentCancellation.kt`：保存当前模型流并传播取消信号，使 UI 可以立即中断生成。
- `ToolCallAccumulator.kt`：按照工具调用的稳定索引合并流式返回的名称、ID 和参数分片。
- `TextAgentSession.kt`：文字与语音共用的 Agent 会话门面，创建上下文、工具环境和 DeepSeek 客户端，并允许语音层写入播报打断事实。
