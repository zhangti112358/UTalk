package com.zhangti.utalk.agent.tool.catalog

/** 地点和路线统一由高德提供；滴滴仅暴露非跳转链接的叫车接口。 */
object TravelToolExposurePolicy {
    fun isVisible(providerName: String, toolName: String): Boolean = when (providerName) {
        "DiDi-Ride" -> toolName.startsWith("taxi_") && toolName != "taxi_generate_ride_app_link"
        "amap-maps" -> toolName != "maps_schema_take_taxi"
        else -> true
    }
}
