package com.zhangti.utalk.agent.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangti.utalk.agent.context.AssistantOutputContext
import com.zhangti.utalk.agent.context.ToolResultContext
import com.zhangti.utalk.agent.context.UserInputContext
import com.zhangti.utalk.agent.tool.local.DeviceLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteHistoryStoreTest {
    @Test fun eventsSurviveReopenAndAreSearchableWithLocation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "agent_history_instrumented_test.db"
        context.deleteDatabase(name)
        try {
            SqliteHistoryStore(context, name).use { store ->
                store.append("session-a", 1, 1_000L, UserInputContext("昨天去虹桥机场"))
                store.append("session-a", 1, 1_001L, ToolResultContext("call", "map", "路线约 20 公里", false))
                store.append("session-a", 1, 1_002L, AssistantOutputContext("建议提前出发"))
                store.updateTurnLocation("session-a", 1, DeviceLocation(31.2, 121.3, 15f, "gps", 999L))
            }
            SqliteHistoryStore(context, name).use { reopened ->
                val byWords = reopened.search("虹桥", null, null)
                assertEquals(1, byWords.size)
                assertTrue(byWords.single().conversation.contains("路线约 20 公里"))
                assertEquals(31.2, byWords.single().latitude!!, 0.0001)
                assertTrue(reopened.search("虹桥", 2_000L, null).isEmpty())
                assertFalse(reopened.search("20 公里", null, null).isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }
}
