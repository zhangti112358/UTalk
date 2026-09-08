package com.zhangti.utalk.agent.tool.mcp

/**
 * 远程 MCP 服务配置。
 *
 * @param name 服务名（用于日志与工具冲突提示）
 * @param url MCP 接入点（Streamable HTTP）
 * @param qps 每秒最大请求数（服务商限制，调用侧用令牌桶遵守）
 * @param headers 附加请求头（鉴权等）
 */
data class McpServerConfig(
    val name: String,
    val url: String,
    val qps: Int = 1,
    val headers: Map<String, String> = emptyMap(),
) {
    init {
        require(qps > 0) { "qps 必须大于 0" }
        require(url.startsWith("http")) { "MCP url 必须以 http(s) 开头" }
    }
}
