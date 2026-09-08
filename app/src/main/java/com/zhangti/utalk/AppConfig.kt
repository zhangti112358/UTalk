package com.zhangti.utalk

import com.zhangti.utalk.agent.tool.mcp.McpServerConfig

/**
 * 所有本地密钥的名字集中在这里，与 secrets.properties 里的 key 一一对应。
 * 新增 key：secrets.properties 加一行 + 这里加一个常量 + AppConfig 加一个字段。
 */
object KeyNames {
    /** DeepSeek（LLM）API Key */
    const val DEEPSEEK = "deepseek"

    /** 豆包 Doubao（语音 ASR/TTS）API Key */
    const val DOUBAO = "doubao"

    /** 高德 MCP Key */
    const val AMAP = "amap"

    /** 飞友（VariFlight）MCP Key */
    const val VARIFLIGHT = "variflight"

    /** 彩云天气 MCP Key */
    const val CAIYUN_WEATHER = "caiyun_weather"

    /** DIDA（RollingGo）MCP Key */
    const val DIDA = "dida"

    /** 滴滴出行 MCP Key */
    const val DIDI = "didi"
}

/**
 * 应用配置数据类，统一入口。
 *
 * - 密钥：从本地 secrets.properties（已 gitignore）读取；
 * - 非敏感配置（链接、模型名等）：直接硬编码在下方。
 *
 * 用法：AppConfig.instance.deepseekApiKey / .deepseekBaseUrl
 *
 * 未来做「用户自己输入」时：load() 里先查用户配置（DataStore）、查不到再回落本地值，
 * 优先级：用户输入 > 本地构建值。
 */
data class AppConfig(
    val deepseekApiKey: String,
    val doubaoApiKey: String,
    val amapMcpKey: String,
    val variflightMcpKey: String,
    val caiyunWeatherMcpKey: String,
    val didaMcpKey: String,
    val didiMcpKey: String,
) {
    // ── 非敏感配置，硬编码 ──
    val deepseekBaseUrl: String = "https://api.deepseek.com/v1/"
    val deepseekModel: String = "deepseek-v4-flash"
    val doubaoAsrUrl: String = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
    val doubaoTtsUrl: String = "wss://openspeech.bytedance.com/api/v3/tts/bidirection"

    /** 远程 MCP 服务列表（含各服务的 QPS 限制，调用侧令牌桶遵守）。 */
    val remoteMcpServers: List<McpServerConfig> = listOf(
        McpServerConfig(
            name = "amap-maps",
            url = "https://mcp.amap.com/mcp?key=$amapMcpKey",
            qps = 3,
            // 高德要求同时接受 application/json 与 text/event-stream，否则返回错误
            headers = mapOf("Accept" to "application/json, text/event-stream"),
        ),
        // 飞友（VariFlight）航空 MCP Server。
        // 注意：URL 末尾 mcp 后不能带斜杠，带斜杠时飞友会 307 重定向导致建连失败。
        McpServerConfig(
            name = "VariFlight-Aviation",
            url = "https://ai.variflight.com/servers/aviation/mcp?api_key=$variflightMcpKey",
            qps = 3,
            headers = mapOf("Accept" to "application/json, text/event-stream"),
        ),
        // DIDA 酒店 MCP Server（RollingGo）：Authorization Bearer 头鉴权。
        McpServerConfig(
            name = "DIDA-Hotel",
            url = "https://mcp.rollinggo.cn/mcp",
            qps = 3,
            headers = mapOf(
                "Authorization" to "Bearer $didaMcpKey",
                "Accept" to "application/json, text/event-stream",
            ),
        ),
        // DIDA 机票 MCP Server（RollingGo）暂时停用（保留配置备查）。
        // McpServerConfig(
        //     name = "DIDA-Flight",
        //     url = "https://mcp.rollinggo.cn/mcp/flight",
        //     qps = 3,
        //     headers = mapOf(
        //         "Authorization" to "Bearer $didaMcpKey",
        //         "Accept" to "application/json, text/event-stream",
        //     ),
        // ),
        // 滴滴出行 MCP Server：网约车 + 地图（文档见 third/didi/mcp.md）。
        // 用 sandbox 调试端点（Mock 数据，不产生真实订单）；
        // 生产端点：https://mcp.didichuxing.com/mcp-servers?key=<KEY>（会产生真实订单）。
        McpServerConfig(
            name = "DiDi-Ride",
            url = "https://mcp.didichuxing.com/mcp-servers-sandbox?key=$didiMcpKey",
            qps = 1, // 官方未公布 QPS 上限，先保守限制
            headers = mapOf("Accept" to "application/json, text/event-stream"),
        ),
        // 彩云天气 MCP Server：X-Caiyun-API-Key 头鉴权。
        McpServerConfig(
            name = "Caiyun-Weather",
            url = "https://mcp-weather.caiyunapp.com/mcp",
            qps = 1, // 当前个人注册 QPS 为 1
            headers = mapOf(
                "X-Caiyun-API-Key" to caiyunWeatherMcpKey,
                "Accept" to "application/json, text/event-stream",
            ),
        ),
    )

    companion object {
        /** 全局实例（直接获取用） */
        val instance: AppConfig by lazy { load() }

        /** 从本地注入的密钥构建配置 */
        fun load(): AppConfig = AppConfig(
            deepseekApiKey = LocalConfig[KeyNames.DEEPSEEK],
            doubaoApiKey = LocalConfig[KeyNames.DOUBAO],
            amapMcpKey = LocalConfig[KeyNames.AMAP],
            variflightMcpKey = LocalConfig[KeyNames.VARIFLIGHT],
            caiyunWeatherMcpKey = LocalConfig[KeyNames.CAIYUN_WEATHER],
            didaMcpKey = LocalConfig[KeyNames.DIDA],
            didiMcpKey = LocalConfig[KeyNames.DIDI],
        )
    }
}
