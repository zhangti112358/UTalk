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

/** 以当前用户指令授权一次叫车，无需额外确认；其他写操作仍保持关闭。 */
class ExplicitRideOrderPolicy : ToolPolicy {
    override fun evaluate(
        tool: CatalogTool,
        arguments: Map<String, Any?>,
        turn: ToolTurnContext,
    ): ToolPolicyDecision = when (tool.metadata.risk) {
        ToolRisk.READ_ONLY -> ToolPolicyDecision.Allow
        ToolRisk.BLOCKED -> ToolPolicyDecision.Deny("${tool.metadata.id} 当前不支持执行")
        ToolRisk.EXPLICIT_RIDE_ORDER -> when {
            !RideOrderIntent.isExplicitOrderRequest(turn.userText) -> ToolPolicyDecision.Deny(
                "本轮用户没有明确要求叫车；只能查询或预估，不得创建订单"
            )
            arguments["product_category"]?.toString().isNullOrBlank() ||
                arguments["estimate_trace_id"]?.toString().isNullOrBlank() -> ToolPolicyDecision.Deny(
                    "创建订单前必须先预估，并提供预估返回的车型标识和 estimate_trace_id"
                )
            !turn.reserveRideOrder() -> ToolPolicyDecision.Deny(
                "本轮已尝试创建订单；请先查询订单状态，不能重复发单"
            )
            else -> ToolPolicyDecision.Allow
        }
    }
}
