# 工具模型适配

隔离 MCP 工具定义、远程原始名称与模型侧 Function Calling 格式之间的差异。

## 文件

- `NamespacedAgentTool.kt`：为远程工具增加稳定命名空间，避免高德、滴滴等服务出现同名冲突，同时仍委托原工具执行。
- `ToolDefinitionAdapter.kt`：把 MCP `ToolSchema` 转换为 `LlmTool` 使用的 JSON Schema，并省略 DeepSeek 不接受的空字段。

