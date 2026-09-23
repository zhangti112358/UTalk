package com.zhangti.utalk.agent.tool.execution

/** 识别当前轮明确的叫车指令；普通询价和否定指令不能授权下单。 */
object RideOrderIntent {
    private val blockedPhrases = listOf(
        "不要", "别", "不用", "先不", "暂不", "取消", "不需要", "只是", "只想",
        "能不能", "可不可以", "怎么", "如何", "链接",
    )
    private val quotePhrases = listOf("多少钱", "价格", "预估", "估价", "先看看", "查询")
    private val directPhrases = listOf(
        "直接下单", "现在下单", "马上下单", "立即下单", "确认下单",
        "直接叫车", "现在叫车", "马上叫车", "立即叫车", "帮我叫车", "给我叫车", "我要叫车",
        "直接打车", "现在打车", "马上打车", "立即打车", "帮我打车", "给我打车", "我要打车",
    )
    private val carRequest = Regex("(?:打|叫)(?:一|个|辆|台|部){0,4}(?:特惠快车|快车|专车|豪华车|出租车|网约车|车)(?:去|到|来|吧|了|$)")

    fun isExplicitOrderRequest(text: String): Boolean {
        val normalized = text.lowercase().filterNot(Char::isWhitespace)
        if (blockedPhrases.any(normalized::contains)) return false
        if (normalized.endsWith('吗') || normalized.endsWith('？') || normalized.endsWith('?')) return false
        val orderStart = maxOf(
            directPhrases.maxOfOrNull { normalized.lastIndexOf(it) } ?: -1,
            carRequest.findAll(normalized).lastOrNull()?.range?.first ?: -1,
        )
        if (orderStart < 0) return false
        val quoteStart = quotePhrases.maxOfOrNull { normalized.lastIndexOf(it) } ?: -1
        return quoteStart < orderStart
    }
}
