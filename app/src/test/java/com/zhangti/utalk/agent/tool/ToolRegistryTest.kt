package com.zhangti.utalk.agent.tool

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ToolRegistry 单元测试（用 EchoTool 验证统一工具抽象的注册与调用）。
 */
class ToolRegistryTest {

    @Test
    fun `register and call echo tool`() = runBlocking {
        val registry = ToolRegistry()
        registry.register(EchoTool())

        val result = registry.call("echo", mapOf("text" to "你好"))

        assertTrue(result.isError != true)
        val content = result.content.first() as TextContent
        assertEquals("你好", content.text)
    }

    @Test
    fun `duplicate name throws`() {
        val registry = ToolRegistry()
        registry.register(EchoTool())

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(EchoTool())
        }
    }

    @Test
    fun `unknown tool returns error result`() = runBlocking {
        val registry = ToolRegistry()

        val result = registry.call("不存在的工具", emptyMap())

        assertEquals(true, result.isError)
    }

    @Test
    fun `definitions lists registered tools`() {
        val registry = ToolRegistry()
        registry.register(EchoTool())

        assertEquals(listOf("echo"), registry.definitions().map { it.name })
        assertEquals(setOf("echo"), registry.names)
    }
}
