package com.zhangti.utalk.agent.tool.web

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekWebToolsTest {
    @Test fun `search forwards official arguments and returns results`() = runBlocking {
        val service = FakeService()
        val result = DeepSeekWebSearchTool(service).call(mapOf(
            "query" to "DeepSeek 新闻 after:2026-10-01",
            "count" to 5,
            "ori_question" to "DeepSeek 最近有什么新闻",
            "need_content" to false,
        ))

        assertFalse(result.isError == true)
        assertEquals("DeepSeek 新闻 after:2026-10-01", service.searchBody?.getString("query"))
        assertEquals(5, service.searchBody?.getInt("count"))
        assertFalse(service.searchBody!!.getBoolean("need_content"))
        assertTrue(result.content.toString().contains("example.com"))
    }

    @Test fun `fetch validates url then returns markdown`() = runBlocking {
        val service = FakeService()
        val tool = DeepSeekWebFetchTool(service)

        val invalid = tool.call(mapOf("url" to "file:///etc/passwd"))
        assertTrue(invalid.isError == true)
        assertEquals(null, service.fetchBody)

        val valid = tool.call(mapOf("url" to "https://example.com/article"))
        assertFalse(valid.isError == true)
        assertEquals("https://example.com/article", service.fetchBody?.getString("url"))
        assertTrue(valid.content.toString().contains("# Article"))
    }

    @Test fun `search rejects invalid count before network`() = runBlocking {
        val service = FakeService()
        val result = DeepSeekWebSearchTool(service).call(mapOf("query" to "test", "count" to 51))
        assertTrue(result.isError == true)
        assertEquals(null, service.searchBody)
    }

    private class FakeService : DeepSeekWebService {
        var searchBody: JSONObject? = null
        var fetchBody: JSONObject? = null

        override fun search(body: JSONObject): JSONObject {
            searchBody = body
            return JSONObject("""{"results":[{"title":"Result","url":"https://example.com"}]}""")
        }

        override fun fetch(body: JSONObject): JSONObject {
            fetchBody = body
            return JSONObject().put("title", "Article").put("content", "# Article\n\nBody")
        }
    }
}
