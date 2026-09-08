package com.zhangti.utalk.agent.tool.mcp

import android.util.Log
import com.zhangti.utalk.agent.tool.AgentTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import java.io.Closeable
import kotlinx.coroutines.runBlocking

/**
 * 远程 MCP 服务接入：按 [McpServerConfig] 建连（Streamable HTTP），
 * listTools 后把每个远程工具包装成 [AgentTool]。
 * 同一 provider 的所有工具共享一个 [RateLimiter]（即共享该服务的 QPS 额度）。
 */
class McpToolProvider(private val config: McpServerConfig) : Closeable {

    private val httpClient: HttpClient = HttpClient(OkHttp) {
        // JSON 序列化由 MCP SDK 自行处理，这里只做传输
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 60_000
        }
    }

    private val mcpClient: Client =
        Client(clientInfo = Implementation(name = "utalk", version = "1.0.0"))

    private val transport = StreamableHttpClientTransport(
        client = httpClient,
        url = config.url,
        requestBuilder = {
            config.headers.forEach { (name, value) -> headers.append(name, value) }
        },
    )

    /** 本服务的 QPS 令牌桶，所有远程工具共享。 */
    private val limiter = RateLimiter(config.qps)

    @Volatile
    private var connected = false

    /** 建连（只连一次）并拉取远程工具列表，包装为 [AgentTool]。 */
    suspend fun loadTools(): List<AgentTool> {
        if (!connected) {
            Log.i(TAG, "连接远程 MCP：${config.name} -> ${config.url}")
            mcpClient.connect(transport)
            connected = true
        }
        val tools = mcpClient.listTools().tools ?: emptyList()
        Log.i(TAG, "远程 MCP ${config.name} 提供 ${tools.size} 个工具")
        return tools.map { RemoteMcpTool(it, mcpClient, limiter) }
    }

    override fun close() {
        runBlocking { runCatching { mcpClient.close() } }
        httpClient.close()
    }

    companion object {
        private const val TAG = "McpToolProvider"
    }
}
