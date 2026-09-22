package com.zhangti.utalk.agent.tool.execution

import com.zhangti.utalk.agent.tool.catalog.CatalogTool
import com.zhangti.utalk.agent.tool.catalog.ToolRisk

sealed interface ToolPolicyDecision {
    data object Allow : ToolPolicyDecision
    data class Deny(val reason: String) : ToolPolicyDecision
}

fun interface ToolPolicy {
    fun evaluate(tool: CatalogTool): ToolPolicyDecision
}

/** 首版只自动执行只读工具；真实订单操作等待后续确认 UI。 */
class ReadOnlyToolPolicy : ToolPolicy {
    override fun evaluate(tool: CatalogTool): ToolPolicyDecision =
        if (tool.metadata.risk == ToolRisk.READ_ONLY) ToolPolicyDecision.Allow
        else ToolPolicyDecision.Deny("${tool.metadata.id} 会改变订单状态，需要用户确认；当前文字版尚未开启确认流程")
}

