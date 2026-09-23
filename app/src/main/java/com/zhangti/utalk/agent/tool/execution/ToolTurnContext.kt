package com.zhangti.utalk.agent.tool.execution

/** 只在当前用户输入引发的 Agent 循环内有效，避免沿用上一轮的下单授权。 */
class ToolTurnContext(val userText: String) {
    private var rideOrderReserved = false

    @Synchronized
    fun reserveRideOrder(): Boolean {
        if (rideOrderReserved) return false
        rideOrderReserved = true
        return true
    }
}
