# Agent 工具

统一工具抽象：本地函数封装与远程 MCP 服务的工具共用同一接口。

## 文件

- `AgentTool.kt`：定义本地工具和远程 MCP 工具共同实现的定义与调用接口。
- `ToolRegistry.kt`：提供按名称注册、列举和调用工具的基础注册表，未知工具会返回错误结果。
- `EchoTool.kt`：最小本地示例工具，用于验证工具抽象、注册表和测试链路。
- `catalog/`：保存全部工具及检索元数据，详情见 [`catalog/readme.md`](catalog/readme.md)。
- `discovery/`：按需搜索并激活工具，详情见 [`discovery/readme.md`](discovery/readme.md)。
- `execution/`：参数、策略、调用和结果转换，详情见 [`execution/readme.md`](execution/readme.md)。
- `model/`：MCP 与模型工具格式适配，详情见 [`model/readme.md`](model/readme.md)。
- `mcp/`：远程 MCP 服务接入，详情见 [`mcp/readme.md`](mcp/readme.md)。
- `local/`：本地设备工具，提供按需读取手机当前位置和当前时间，详情见 [`local/readme.md`](local/readme.md)。

## 参考

https://github.com/modelcontextprotocol/kotlin-sdk
（已克隆到 `third/kotlin-sdk`；依赖 `io.modelcontextprotocol:kotlin-sdk-core:0.14.0`。
注意：0.15.0 用 Kotlin 2.4 编译，与本项目 Kotlin 2.2 不兼容，先锁 0.14.0）

# 对外接口

### `AgentTool`（统一工具接口）

```kotlin
val definition: Tool                                // MCP 协议类型（name/description/inputSchema）
suspend fun call(arguments: Map<String, Any?>): CallToolResult
```

- 本地工具：`definition` 手写，`call` 直接调自己的函数，返回 `CallToolResult.success(text)`
- 远程工具：`definition` 来自 `client.listTools()`，`call` 委托 `client.callTool(name, arguments)`
- 工具自身失败应返回 `CallToolResult.error(...)` 而不是抛异常（LLM 能看到并自我纠正）

### `ToolRegistry`（注册表）

```kotlin
fun register(tool: AgentTool): AgentTool        // 重名抛异常
fun registerAll(toolList: List<AgentTool>)      // 批量注册（如远程 listTools 结果）
fun definitions(): List<Tool>                   // 全部定义（供 LLM / MCP server）
suspend fun call(name: String, arguments: Map<String, Any?>): CallToolResult
                                                // 未知工具返回 isError=true
```

### 示例

见 `EchoTool.kt`（原样回显文本）。
