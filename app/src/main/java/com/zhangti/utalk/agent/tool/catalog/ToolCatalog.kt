package com.zhangti.utalk.agent.tool.catalog

import com.zhangti.utalk.agent.tool.AgentTool

enum class ToolDomain(val aliases: Set<String>) {
    MAP(setOf("地图", "地点", "路线", "导航", "map", "route", "location")),
    FLIGHT(setOf("机票", "航班", "飞机", "flight", "airport")),
    HOTEL(setOf("酒店", "住宿", "hotel")),
    RIDE(setOf("打车", "出租车", "网约车", "滴滴", "taxi", "ride")),
    WEATHER(setOf("天气", "气温", "降雨", "预警", "weather")),
    SYSTEM(setOf("系统", "工具", "tool")),
}

enum class ToolRisk { READ_ONLY, EXPLICIT_RIDE_ORDER, BLOCKED }

data class ToolMetadata(
    val id: String,
    val provider: String,
    val domain: ToolDomain,
    val summary: String,
    val risk: ToolRisk = ToolRisk.READ_ONLY,
    val keywords: Set<String> = emptySet(),
    val core: Boolean = false,
)

data class CatalogTool(
    val metadata: ToolMetadata,
    val tool: AgentTool,
)

class ToolCatalog {
    private val entries = LinkedHashMap<String, CatalogTool>()

    @Synchronized
    fun register(entry: CatalogTool) {
        require(entry.metadata.id == entry.tool.definition.name) {
            "目录 id 必须与暴露给模型的工具名一致"
        }
        require(entry.metadata.id !in entries) { "工具重名：${entry.metadata.id}" }
        entries[entry.metadata.id] = entry
    }

    @Synchronized
    fun get(id: String): CatalogTool? = entries[id]

    @Synchronized
    fun coreToolIds(): List<String> = entries.values
        .filter { it.metadata.core }
        .map { it.metadata.id }

    @Synchronized
    fun all(): List<CatalogTool> = entries.values.toList()

    /** 小规模目录先使用可解释的关键词评分；未来可独立替换为向量检索。 */
    @Synchronized
    fun search(query: String, domain: ToolDomain?, limit: Int): List<CatalogTool> {
        val normalized = query.lowercase()
        return entries.values.asSequence()
            .filter { !it.metadata.core }
            .filter { domain == null || it.metadata.domain == domain }
            .map { entry ->
                val meta = entry.metadata
                val fields = buildList {
                    add(meta.id.lowercase())
                    add(meta.summary.lowercase())
                    addAll(meta.keywords.map(String::lowercase))
                    addAll(meta.domain.aliases)
                }
                val score = fields.sumOf { field ->
                    when {
                        normalized.isBlank() -> 1
                        normalized.contains(field) -> 4
                        field.contains(normalized) -> 3
                        normalized.split(Regex("\\s+|，|、")).any { it.length > 1 && field.contains(it) } -> 1
                        else -> 0
                    }
                } + (if (domain == meta.domain) 5 else 0) +
                    if (meta.domain == ToolDomain.RIDE &&
                        meta.id.startsWith("ride__taxi_") &&
                        RIDE_QUERY_TERMS.any(normalized::contains)
                    ) 12 else 0
                entry to score
            }
            .filter { (_, score) -> score > 0 || domain != null }
            .sortedByDescending { (_, score) -> score }
            .take(limit.coerceIn(1, 8))
            .map { it.first }
            .toList()
    }

    companion object {
        private val RIDE_QUERY_TERMS = listOf(
            "打车", "叫车", "下单", "快车", "专车", "滴滴", "出租车", "网约车", "taxi", "ride",
        )
    }
}
