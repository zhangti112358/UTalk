package com.zhangti.utalk.agent.tool.mcp

import com.zhangti.utalk.AppConfig
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * 工具列表导出（手动运行）：把每个远程 MCP 服务的工具列表分别保存到 doc/mcp-tools/，
 * 用于后续分析每个工具说明的 token 数。
 *
 * 运行：./gradlew :app:testDebugUnitTest --tests "*McpToolListDumpTest"
 */
class McpToolListDumpTest {

    @Test
    fun `dump tool list of each remote mcp server to doc`() = runBlocking {
        // user.dir = app/ 模块目录，上一层即仓库根
        val outDir = File(System.getProperty("user.dir")).parentFile.resolve("doc/mcp-tools")
        outDir.mkdirs()

        val json = Json { prettyPrint = true }

        AppConfig.instance.remoteMcpServers.forEach { server ->
            val provider = McpToolProvider(server)
            try {
                val tools = provider.loadTools()

                val md = buildString {
                    appendLine("# ${server.name} 工具列表")
                    appendLine()
                    appendLine("MCP 地址: ${maskUrl(server.url)}")
                    appendLine()
                    appendLine("工具数量: ${tools.size}")
                    appendLine()
                    tools.forEach { tool ->
                        val def = tool.definition
                        appendLine("## ${def.name}")
                        appendLine()
                        appendLine("描述: ${def.description ?: "（无）"}")
                        appendLine()
                        appendLine("输入 Schema:")
                        appendLine("```json")
                        appendLine(json.encodeToString(def.inputSchema))
                        appendLine("```")
                        appendLine()
                    }
                }

                val file = File(outDir, "${server.name}.md")
                file.writeText(md)
                println("已保存 ${file.path}（${tools.size} 个工具）")
            } catch (e: Exception) {
                println("!! ${server.name} 失败：${e.message}")
                File(outDir, "${server.name}.failed.txt")
                    .writeText(e.stackTraceToString())
            } finally {
                provider.close()
            }
        }
    }

    /** 打码 URL 中的 key 类查询参数，避免把密钥写进文档。 */
    private fun maskUrl(url: String): String =
        url.replace(Regex("([?&](?:key|api_key|api-key|apikey)=)[^&]*")) {
            it.groupValues[1] + "***"
        }
}
