package com.zhangti.utalk.agent.tool.local

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CurrentTimeToolTest {
    @Test
    fun `returns full local date weekday and time`() = runBlocking {
        val tool = CurrentTimeTool {
            ZonedDateTime.of(2026, 9, 23, 14, 5, 6, 0, ZoneId.of("Asia/Shanghai"))
        }

        val result = tool.call(emptyMap())

        assertFalse(result.isError == true)
        assertEquals(
            "手机当前时间：2026年9月23日 星期三 14:05:06",
            (result.content.single() as TextContent).text,
        )
    }
}
