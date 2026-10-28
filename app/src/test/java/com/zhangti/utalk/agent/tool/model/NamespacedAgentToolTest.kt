package com.zhangti.utalk.agent.tool.model

import com.zhangti.utalk.agent.tool.EchoTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamespacedAgentToolTest {
    @Test fun `namespace can append capability boundary without changing delegate name`() {
        val original = EchoTool()
        val wrapped = NamespacedAgentTool("rail", original, "只能查询，不能购票或下单。")

        assertEquals("rail__echo", wrapped.definition.name)
        assertTrue(wrapped.definition.description.orEmpty().contains("只能查询，不能购票或下单"))
        assertEquals("echo", original.definition.name)
    }
}
