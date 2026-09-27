package com.zhangti.utalk.agent.tool.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonPrimitive

class TakePhotoToolTest {
    @Test
    fun `successful capture returns original reference`() = runBlocking {
        val result = TakePhotoTool { CapturedPhoto("/private/photo.jpg", "now", "wide") }.call(emptyMap())
        assertEquals(false, result.isError)
        assertEquals("/private/photo.jpg", result.structuredContent!!["photo_path"]!!.jsonPrimitive.content)
    }

    @Test
    fun `camera errors are visible to agent`() = runBlocking {
        val result = TakePhotoTool { throw IllegalStateException("相机权限被拒绝") }.call(emptyMap())
        assertTrue(result.isError == true)
    }
}
