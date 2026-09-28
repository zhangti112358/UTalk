package com.zhangti.utalk

import com.zhangti.utalk.agent.tool.mcp.McpServerConfig
import com.zhangti.utalk.settings.DiDiEnvironment
import com.zhangti.utalk.settings.SettingsRepository

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
 * - 密钥：手机设置优先；未填写时从构建期 secrets.properties（已 gitignore）读取；
 * - 非敏感配置（链接、模型名等）：直接硬编码在下方。
 *
 * 用法：AppConfig.instance.deepseekApiKey / .deepseekBaseUrl
 *
 * 设置变更在重新打开 Agent / 语音或模型页面后生效，现有会话不会热切换密钥。
 */
data class AppConfig(
    val deepseekApiKey: String,
    val doubaoApiKey: String,
    val amapMcpKey: String,
    val variflightMcpKey: String,
    val caiyunWeatherMcpKey: String,
    val didaMcpKey: String,
    val didiMcpKey: String,
    val didiEnvironment: DiDiEnvironment = DiDiEnvironment.SANDBOX,
) {
    // ── 非敏感配置，硬编码 ──
    val deepseekBaseUrl: String = "https://api.deepseek.com/v1/"
    val deepseekModel: String = "deepseek-flash"
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
        // 滴滴出行 MCP Server：远端含网约车和地图工具，但 Agent 只暴露网约车工具（文档见 doc/didi/mcp.md）。
        // 默认 sandbox 调试端点（Mock 数据，不产生真实订单）；
        // 生产端点：https://mcp.didichuxing.com/mcp-servers?key=<KEY>（会产生真实订单）。
        McpServerConfig(
            name = "DiDi-Ride",
            url = "https://mcp.didichuxing.com/${if (didiEnvironment == DiDiEnvironment.PRODUCTION) "mcp-servers" else "mcp-servers-sandbox"}?key=$didiMcpKey",
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
        val instance: AppConfig get() = load()

        /** 从本地注入的密钥构建配置 */
        fun load(): AppConfig = AppConfig(
            deepseekApiKey = SettingsRepository.resolvedKey(KeyNames.DEEPSEEK),
            doubaoApiKey = SettingsRepository.resolvedKey(KeyNames.DOUBAO),
            amapMcpKey = SettingsRepository.resolvedKey(KeyNames.AMAP),
            variflightMcpKey = SettingsRepository.resolvedKey(KeyNames.VARIFLIGHT),
            caiyunWeatherMcpKey = SettingsRepository.resolvedKey(KeyNames.CAIYUN_WEATHER),
            didaMcpKey = SettingsRepository.resolvedKey(KeyNames.DIDA),
            didiMcpKey = SettingsRepository.resolvedKey(KeyNames.DIDI),
            didiEnvironment = SettingsRepository.didiEnvironment(),
        )
    }
}
