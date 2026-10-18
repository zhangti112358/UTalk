package com.zhangti.utalk.agent.tool.web

import com.zhangti.utalk.agent.tool.AgentTool
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.net.URI
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

/** 把单个网页抓取为适合模型阅读的 Markdown。 */
class DeepSeekWebFetchTool(private val api: DeepSeekWebService) : AgentTool {
    override val definition = Tool(
        name = ID,
        description = "抓取一个网页并返回页面标题和Markdown正文。用于阅读web_search找到的网页；只接受完整的HTTP或HTTPS URL。",
        inputSchema = ToolSchema(
            properties = JsonObject(mapOf("url" to stringField("要抓取的完整HTTP或HTTPS网页URL"))),
            required = listOf("url"),
        ),
    )

    override suspend fun call(arguments: Map<String, Any?>) = toolResult {
        val url = arguments["url"]?.toString()?.trim().orEmpty()
        require(url.length in 1..8192) { "url 必须为1到8192个字符" }
        val uri = runCatching { URI(url) }.getOrNull()
        require(uri != null && uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
        ) { "url 必须是包含主机且不含用户名密码的HTTP或HTTPS地址" }
        api.fetch(JSONObject().put("url", url)).toString(2)
    }

    companion object {
        const val ID = "web_fetch"
    }
}
