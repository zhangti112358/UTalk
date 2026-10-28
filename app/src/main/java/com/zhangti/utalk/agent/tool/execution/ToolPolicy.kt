package com.zhangti.utalk.agent.tool.execution

import com.zhangti.utalk.agent.tool.catalog.CatalogTool
import com.zhangti.utalk.agent.tool.catalog.ToolRisk

sealed interface ToolPolicyDecision {
    data object Allow : ToolPolicyDecision
    data class Deny(val reason: String) : ToolPolicyDecision
}

fun interface ToolPolicy {
    fun evaluate(
        tool: CatalogTool,
        arguments: Map<String, Any?>,
        turn: ToolTurnContext,
    ): ToolPolicyDecision
}

/** 语义安全规则由系统提示词交给模型判断；这里只阻止同一轮重复创建订单。 */
class DefaultToolPolicy : ToolPolicy {
    override fun evaluate(
        tool: CatalogTool,
        arguments: Map<String, Any?>,
        turn: ToolTurnContext,
    ): ToolPolicyDecision = when (tool.metadata.risk) {
        ToolRisk.READ_ONLY -> ToolPolicyDecision.Allow
        ToolRisk.AGENT_DECIDES -> ToolPolicyDecision.Allow
        ToolRisk.BLOCKED -> ToolPolicyDecision.Deny("${tool.metadata.id} 当前不支持执行")
        ToolRisk.EXPLICIT_RIDE_ORDER ->
            if (!turn.reserveRideOrder()) ToolPolicyDecision.Deny(
                "本轮已尝试创建订单；请先查询订单状态，不能重复发单"
            )
            else ToolPolicyDecision.Allow
    }
}
