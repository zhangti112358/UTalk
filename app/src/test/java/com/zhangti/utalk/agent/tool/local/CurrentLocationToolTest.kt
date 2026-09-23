package com.zhangti.utalk.agent.tool.local

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLocationToolTest {
    @Test
    fun `returns longitude first and accuracy for map tools`() = runBlocking {
        val tool = CurrentLocationTool(CurrentLocationSource {
            DeviceLocation(39.916527, 116.397128, 12.5f, "gps", 1_700_000_000_000)
        })

        val result = tool.call(emptyMap())

        assertFalse(result.isError == true)
        val text = (result.content.single() as TextContent).text
        assertTrue(text.contains("116.397128,39.916527"))
        assertTrue(text.contains("12 米"))
        assertTrue(text.contains("gps"))
    }

    @Test
    fun `permission denial is returned as a tool error`() = runBlocking {
        val tool = CurrentLocationTool(CurrentLocationSource {
            throw IllegalStateException("未授予手机定位权限")
        })

        val result = tool.call(emptyMap())

        assertTrue(result.isError == true)
        assertTrue((result.content.single() as TextContent).text.contains("定位权限"))
    }
}
