# 远程 MCP 调用

把远程 MCP 服务的工具接入统一 [AgentTool](../AgentTool.kt) 抽象，并遵守服务商 QPS 限制。

## 对外接口

### `McpServerConfig`（服务配置）

```kotlin
McpServerConfig(
    name: String,              // 服务名（日志/冲突提示）
    url: String,               // MCP 接入点（Streamable HTTP）
    qps: Int = 1,              // 服务商 QPS 上限（调用侧令牌桶遵守）
    headers: Map<String, String> = emptyMap(),  // 鉴权请求头
)
```

服务列表配置在 `AppConfig.remoteMcpServers`（key 来自 secrets.properties）。

### `McpToolProvider`（服务接入）

```kotlin
McpToolProvider(config: McpServerConfig)

suspend fun loadTools(): List<AgentTool>  // 建连（只连一次）+ listTools + 包装
fun close()                               // 释放连接
```

返回的每个 `AgentTool`（[RemoteMcpTool](RemoteMcpTool.kt)）：
- `definition` = 服务端下发的工具定义，可直接注册进 `ToolRegistry`
- `call` = 先 `RateLimiter.acquire()`（共享该服务的 QPS 额度）再 `client.callTool`

### `RateLimiter`（令牌桶，QPS 限流）

```kotlin
RateLimiter(qps: Int)
suspend fun acquire()  // 令牌不足时挂起等待
```

容量 = qps（允许瞬时突发），每秒补充 qps 个令牌。

## 设计要点（QPS）

- 每个 server 一个 provider + 一个共享令牌桶，**所有工具共享该服务的 QPS 额度**
- 令牌不足时挂起等待而不是丢弃请求（LLM 调用链上丢请求会导致工具调用失败）
- 工具名保持服务端原名；不同 server 同名工具注册进 ToolRegistry 时会按重名规则处理
