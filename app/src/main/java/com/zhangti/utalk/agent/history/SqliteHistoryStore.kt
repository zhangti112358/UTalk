package com.zhangti.utalk.agent.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.zhangti.utalk.agent.context.AgentContextItem
import com.zhangti.utalk.agent.context.AssistantOutputContext
import com.zhangti.utalk.agent.context.AssistantPlaybackContext
import com.zhangti.utalk.agent.context.ImageContext
import com.zhangti.utalk.agent.context.SystemPromptContext
import com.zhangti.utalk.agent.context.ToolAvailabilityContext
import com.zhangti.utalk.agent.context.ToolResultContext
import com.zhangti.utalk.agent.context.UserInputContext
import com.zhangti.utalk.agent.tool.local.DeviceLocation
import org.json.JSONArray
import org.json.JSONObject

/** 仅本机的追加式完整记录；模型上下文裁剪不会修改本库。 */
class SqliteHistoryStore(context: Context, databaseName: String = DATABASE_NAME) : SQLiteOpenHelper(
    context.applicationContext, databaseName, null, 1,
), HistoryStore {
    init { setWriteAheadLoggingEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL,
            turn_no INTEGER NOT NULL,
            occurred_at INTEGER NOT NULL,
            kind TEXT NOT NULL,
            content TEXT,
            tool_calls TEXT,
            tool_call_id TEXT,
            tool_name TEXT,
            is_error INTEGER,
            image_path TEXT,
            captured_at TEXT,
            search_text TEXT,
            latitude REAL,
            longitude REAL,
            accuracy_meters REAL,
            location_at INTEGER
        )""")
        db.execSQL("CREATE INDEX events_turn ON events(session_id, turn_no, id)")
        db.execSQL("CREATE INDEX events_time ON events(occurred_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("历史数据库需要显式迁移：$oldVersion → $newVersion")
    }

    @Synchronized
    override fun append(sessionId: String, turn: Int, occurredAtMillis: Long, item: AgentContextItem) {
        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("turn_no", turn)
            put("occurred_at", occurredAtMillis)
            when (item) {
                is SystemPromptContext -> { put("kind", "system"); put("content", item.text) }
                is UserInputContext -> {
                    put("kind", "user"); put("content", item.text); put("search_text", item.text)
                }
                is AssistantOutputContext -> {
                    put("kind", "assistant"); put("content", item.text)
                    put("tool_calls", JSONArray().apply {
                        item.toolCalls.forEach { call -> put(JSONObject().apply {
                            put("id", call.id); put("name", call.name); put("arguments", call.arguments)
                        }) }
                    }.toString())
                    if (item.toolCalls.isEmpty()) put("search_text", item.text)
                }
                is ToolResultContext -> {
                    put("kind", "tool_result"); put("content", item.content)
                    put("tool_call_id", item.toolCallId); put("tool_name", item.toolName)
                    put("is_error", if (item.isError) 1 else 0)
                }
                is ToolAvailabilityContext -> {
                    put("kind", "tool_availability"); put("content", item.toolId)
                    put("tool_name", item.source.name)
                }
                is ImageContext -> {
                    put("kind", "image"); put("image_path", item.path)
                    put("captured_at", item.capturedAt)
                }
                is AssistantPlaybackContext -> {
                    put("kind", "playback_interruption")
                    put("content", JSONObject().apply {
                        put("spokenPrefix", item.spokenPrefix)
                        put("fullResponse", item.fullResponse)
                        put("estimated", item.estimated)
                    }.toString())
                }
            }
        }
        check(writableDatabase.insertOrThrow("events", null, values) > 0)
    }

    @Synchronized
    override fun updateTurnLocation(sessionId: String, turn: Int, location: DeviceLocation) {
        val values = ContentValues().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy_meters", location.accuracyMeters)
            put("location_at", location.timestampMillis)
        }
        writableDatabase.update("events", values, "session_id=? AND turn_no=?", arrayOf(sessionId, turn.toString()))
    }

    @Synchronized
    override fun search(query: String, afterMillis: Long?, beforeMillis: Long?, limit: Int): List<HistoryMatch> {
        require(query.isNotBlank() || afterMillis != null || beforeMillis != null)
        val searchable = "COALESCE(search_text, CASE WHEN kind='tool_result' THEN content END)"
        val clauses = mutableListOf("$searchable IS NOT NULL", "turn_no > 0",
            "(tool_name IS NULL OR tool_name <> 'search_conversation_history')")
        val args = mutableListOf<String>()
        if (query.isNotBlank()) {
            clauses += "$searchable LIKE ? ESCAPE '\\'"
            args += "%${query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        }
        if (afterMillis != null) { clauses += "occurred_at >= ?"; args += afterMillis.toString() }
        if (beforeMillis != null) { clauses += "occurred_at <= ?"; args += beforeMillis.toString() }
        val result = mutableListOf<HistoryMatch>()
        val seen = mutableSetOf<Pair<String, Int>>()
        readableDatabase.rawQuery(
            "SELECT session_id, turn_no, occurred_at, latitude, longitude, accuracy_meters FROM events " +
                "WHERE ${clauses.joinToString(" AND ")} ORDER BY occurred_at DESC, id DESC LIMIT 100",
            args.toTypedArray(),
        ).use { cursor ->
            while (cursor.moveToNext() && result.size < limit.coerceIn(1, 10)) {
                val session = cursor.getString(0)
                val turn = cursor.getInt(1)
                if (!seen.add(session to turn)) continue
                val conversation = loadTurnConversation(session, turn)
                result += HistoryMatch(
                    session, turn, cursor.getLong(2),
                    cursor.getDoubleOrNull(3), cursor.getDoubleOrNull(4), cursor.getFloatOrNull(5),
                    conversation,
                )
            }
        }
        return result
    }

    private fun loadTurnConversation(sessionId: String, turn: Int): String = buildString {
        readableDatabase.rawQuery(
            "SELECT kind, COALESCE(search_text, CASE WHEN kind='tool_result' THEN content END), tool_name " +
                "FROM events WHERE session_id=? AND turn_no=? " +
                "AND (search_text IS NOT NULL OR (kind='tool_result' AND tool_name <> 'search_conversation_history')) ORDER BY id",
            arrayOf(sessionId, turn.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (isNotEmpty()) appendLine()
                append(when (cursor.getString(0)) {
                    "user" -> "用户："
                    "tool_result" -> "工具 ${cursor.getString(2)}："
                    else -> "助手："
                })
                append(cursor.getString(1).take(1_500))
                if (length > 5_000) { append("\n[本轮摘录过长；其余原文仍保存在本机历史]"); break }
            }
        }
    }

    private fun android.database.Cursor.getDoubleOrNull(index: Int): Double? =
        if (isNull(index)) null else getDouble(index)
    private fun android.database.Cursor.getFloatOrNull(index: Int): Float? =
        if (isNull(index)) null else getFloat(index)

    companion object {
        const val DATABASE_NAME = "agent_history.db"
        @Volatile private var instance: SqliteHistoryStore? = null
        fun get(context: Context): SqliteHistoryStore = instance ?: synchronized(this) {
            instance ?: SqliteHistoryStore(context.applicationContext).also { instance = it }
        }
    }
}
