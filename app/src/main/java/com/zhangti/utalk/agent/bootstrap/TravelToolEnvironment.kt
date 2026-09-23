package com.zhangti.utalk.agent.bootstrap

import com.zhangti.utalk.AppConfig
import com.zhangti.utalk.agent.context.AgentContextStore
import com.zhangti.utalk.agent.context.ToolAvailabilityContext
import com.zhangti.utalk.agent.context.ToolAvailabilitySource
import com.zhangti.utalk.agent.tool.catalog.CatalogTool
import com.zhangti.utalk.agent.tool.catalog.ToolCatalog
import com.zhangti.utalk.agent.tool.catalog.ToolDomain
import com.zhangti.utalk.agent.tool.catalog.ToolMetadata
import com.zhangti.utalk.agent.tool.catalog.ToolRisk
import com.zhangti.utalk.agent.tool.discovery.SearchToolsTool
import com.zhangti.utalk.agent.tool.catalog.TravelToolExposurePolicy
import com.zhangti.utalk.agent.tool.mcp.McpServerConfig
import com.zhangti.utalk.agent.tool.mcp.McpToolProvider
import com.zhangti.utalk.agent.tool.model.NamespacedAgentTool
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class ToolLoadReport(
    val loadedTools: Int,
    val errors: List<String>,
)

/** 负责远程出行工具的加载、分类、命名空间和生命周期。 */
class TravelToolEnvironment private constructor(
    val catalog: ToolCatalog,
    private val providers: List<McpToolProvider>,
) : Closeable {
    override fun close() = providers.forEach { runCatching { it.close() } }

    companion object {
        suspend fun load(
            context: AgentContextStore,
            configs: List<McpServerConfig> = AppConfig.instance.remoteMcpServers,
            onProgress: (String) -> Unit = {},
        ): Pair<TravelToolEnvironment, ToolLoadReport> = coroutineScope {
            val catalog = ToolCatalog()
            val specs = configs.mapNotNull(::specFor)
            val loaded = specs.map { spec ->
                async(Dispatchers.IO) {
                    onProgress("正在连接 ${spec.providerName}…")
                    val provider = McpToolProvider(spec.config)
                    val result = runCatching { provider.loadTools() }
                    Triple(spec, provider, result)
                }
            }.awaitAll()

            val providers = mutableListOf<McpToolProvider>()
            val errors = mutableListOf<String>()
            loaded.forEach { (spec, provider, result) ->
                providers += provider
                result.onSuccess { tools ->
                    val exposedTools = tools.filter { remote ->
                        TravelToolExposurePolicy.isVisible(spec.config.name, remote.definition.name)
                    }
                    exposedTools.forEach { remote ->
                        val tool = NamespacedAgentTool(spec.namespace, remote)
                        val originalName = remote.definition.name
                        val core = spec.domain == ToolDomain.MAP && originalName in CORE_MAP_TOOLS
                        catalog.register(
                            CatalogTool(
                                metadata = ToolMetadata(
                                    id = tool.definition.name,
                                    provider = spec.providerName,
                                    domain = spec.domain,
                                    summary = remote.definition.description.orEmpty(),
                                    risk = riskFor(spec.domain, originalName),
                                    keywords = keywordsFor(spec.domain, originalName),
                                    core = core,
                                ),
                                tool = tool,
                            )
                        )
                    }
                    onProgress("${spec.providerName}：已加载 ${exposedTools.size} 个工具")
                }.onFailure {
                    errors += "${spec.providerName}: ${it.message ?: "连接失败"}"
                    onProgress("${spec.providerName}：加载失败")
                }
            }

            val searchTool = SearchToolsTool(catalog, context)
            catalog.register(
                CatalogTool(
                    metadata = ToolMetadata(
                        id = SearchToolsTool.ID,
                        provider = "local",
                        domain = ToolDomain.SYSTEM,
                        summary = searchTool.definition.description.orEmpty(),
                        core = true,
                    ),
                    tool = searchTool,
                )
            )
            catalog.coreToolIds().forEach {
                context.append(ToolAvailabilityContext(it, ToolAvailabilitySource.CORE))
            }
            val environment = TravelToolEnvironment(catalog, providers)
            environment to ToolLoadReport(catalog.all().size, errors)
        }

        private fun specFor(config: McpServerConfig): SourceSpec? = when (config.name) {
            "amap-maps" -> SourceSpec(config, "amap", "高德地图", ToolDomain.MAP)
            "VariFlight-Aviation" -> SourceSpec(config, "flight", "飞友航班", ToolDomain.FLIGHT)
            "DIDA-Hotel" -> SourceSpec(config, "hotel", "DIDA 酒店", ToolDomain.HOTEL)
            "DiDi-Ride" -> SourceSpec(config, "ride", "滴滴出行", ToolDomain.RIDE)
            "Caiyun-Weather" -> SourceSpec(config, "weather", "彩云天气", ToolDomain.WEATHER)
            else -> null
        }

        private fun riskFor(domain: ToolDomain, name: String): ToolRisk = when {
            domain == ToolDomain.RIDE && name == "taxi_create_order" -> ToolRisk.EXPLICIT_RIDE_ORDER
            domain == ToolDomain.RIDE && name == "taxi_cancel_order" -> ToolRisk.BLOCKED
            else -> ToolRisk.READ_ONLY
        }

        private fun keywordsFor(domain: ToolDomain, name: String): Set<String> =
            domain.aliases + name.replace('_', ' ').split(' ')

        private val CORE_MAP_TOOLS = setOf(
            "maps_geo",
            "maps_regeocode",
            "maps_text_search",
            "maps_around_search",
            "maps_search_detail",
            "maps_distance",
            "maps_direction_driving",
            "maps_direction_walking",
            "maps_direction_bicycling",
            "maps_direction_transit_integrated",
        )
    }
}

private data class SourceSpec(
    val config: McpServerConfig,
    val namespace: String,
    val providerName: String,
    val domain: ToolDomain,
)
