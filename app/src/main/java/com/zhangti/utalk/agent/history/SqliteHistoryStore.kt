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
import com.zhangti.utalk.agent.context.AssembledContext
import com.zhangti.utalk.agent.debug.DebugRepository
import com.zhangti.utalk.agent.debug.DebugSession
import com.zhangti.utalk.agent.debug.DebugTurn
import com.zhangti.utalk.agent.debug.DebugEvent
import com.zhangti.utalk.agent.debug.DebugToolCall
import com.zhangti.utalk.agent.debug.DebugRequest
import com.zhangti.utalk.agent.debug.DebugRequestSummary
import com.zhangti.utalk.agent.debug.RequestSnapshotCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 仅本机的追加式完整记录；模型上下文裁剪不会修改本库。 */
class SqliteHistoryStore(context: Context, databaseName: String = DATABASE_NAME) : SQLiteOpenHelper(
    context.applicationContext, databaseName, null, 2,
), HistoryStore, DebugRepository {
    private val revision = MutableStateFlow(0L)
    override val changes = revision.asStateFlow()
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
        createRequestsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createRequestsTable(db)
    }

    private fun createRequestsTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE model_requests (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL, turn_no INTEGER NOT NULL, occurred_at INTEGER NOT NULL,
            message_count INTEGER NOT NULL, tool_count INTEGER NOT NULL,
            removed_items INTEGER NOT NULL, truncated_results INTEGER NOT NULL,
            payload TEXT NOT NULL, projection TEXT NOT NULL
        )""")
        db.execSQL("CREATE INDEX requests_session ON model_requests(session_id, id)")
    }

    @Synchronized
    fun recordRequest(sessionId: String, turn: Int, assembled: AssembledContext, model: String, thinkingMode: String) {
        val report = assembled.projection
        val values = ContentValues().apply {
            put("session_id", sessionId); put("turn_no", turn); put("occurred_at", System.currentTimeMillis())
            put("message_count", assembled.request.messages.size); put("tool_count", assembled.request.tools.size)
            put("removed_items", report.removedByType.values.sum()); put("truncated_results", report.truncatedToolCallIds.size)
            put("payload", RequestSnapshotCodec.encode(assembled.request, model, thinkingMode))
            put("projection", RequestSnapshotCodec.encode(report))
        }
        writableDatabase.insertOrThrow("model_requests", null, values)
        revision.value++
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
        revision.value++
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
        revision.value++
    }

    @Synchronized
    override fun sessions(limit: Int): List<DebugSession> = buildList {
        readableDatabase.rawQuery("""SELECT session_id, MIN(occurred_at), MAX(occurred_at),
            COUNT(DISTINCT CASE WHEN turn_no > 0 THEN turn_no END),
            (SELECT substr(content,1,100) FROM events u WHERE u.session_id=e.session_id AND u.kind='user' ORDER BY u.id LIMIT 1)
            FROM events e GROUP BY session_id ORDER BY MAX(occurred_at) DESC LIMIT ${limit.coerceAtLeast(1)}""", null).use { cursor ->
            while (cursor.moveToNext()) add(DebugSession(cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getInt(3), cursor.getString(4).orEmpty()))
        }
    }

    @Synchronized
    override fun turns(sessionId: String, limit: Int): List<DebugTurn> = buildList {
        readableDatabase.rawQuery("""SELECT turn_no, MIN(occurred_at), COUNT(*),
            (SELECT substr(content,1,100) FROM events u WHERE u.session_id=e.session_id AND u.turn_no=e.turn_no AND u.kind='user' ORDER BY u.id LIMIT 1)
            FROM events e WHERE session_id=? GROUP BY turn_no ORDER BY turn_no DESC LIMIT ${limit.coerceAtLeast(1)}""", arrayOf(sessionId)).use { cursor ->
            while (cursor.moveToNext()) add(DebugTurn(cursor.getInt(0), cursor.getLong(1), cursor.getInt(2), cursor.getString(3).orEmpty()))
        }
    }

    @Synchronized
    override fun events(sessionId: String, turn: Int): List<DebugEvent> = buildList {
        readableDatabase.rawQuery("""SELECT id,kind,occurred_at,content,tool_calls,tool_call_id,tool_name,is_error,
            image_path,captured_at,latitude,longitude,accuracy_meters,location_at
            FROM events WHERE session_id=? AND turn_no=? ORDER BY id""", arrayOf(sessionId, turn.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val calls = mutableListOf<DebugToolCall>()
                cursor.getString(4)?.let { raw ->
                    val array = JSONArray(raw)
                    for (index in 0 until array.length()) array.getJSONObject(index).let {
                        calls += DebugToolCall(it.getString("id"), it.getString("name"), it.getString("arguments"))
                    }
                }
                add(DebugEvent(cursor.getLong(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3), calls,
                    cursor.getString(5), cursor.getString(6), cursor.getInt(7) != 0, cursor.getString(8), cursor.getString(9),
                    cursor.getDoubleOrNull(10), cursor.getDoubleOrNull(11), cursor.getFloatOrNull(12),
                    if (cursor.isNull(13)) null else cursor.getLong(13)))
            }
        }
    }

    @Synchronized
    override fun requests(sessionId: String, limit: Int): List<DebugRequestSummary> = buildList {
        readableDatabase.rawQuery("""SELECT id,turn_no,occurred_at,message_count,tool_count,removed_items,truncated_results
            FROM model_requests WHERE session_id=? ORDER BY id DESC LIMIT ${limit.coerceAtLeast(1)}""", arrayOf(sessionId)).use { cursor ->
            while (cursor.moveToNext()) add(cursor.requestSummary())
        }
    }

    @Synchronized
    override fun request(id: Long): DebugRequest? = readableDatabase.rawQuery("""SELECT id,turn_no,occurred_at,
        message_count,tool_count,removed_items,truncated_results,payload,projection
        FROM model_requests WHERE id=?""", arrayOf(id.toString())).use { cursor ->
        if (!cursor.moveToFirst()) null else DebugRequest(cursor.requestSummary(), cursor.getString(7), cursor.getString(8))
    }

    private fun android.database.Cursor.requestSummary() = DebugRequestSummary(
        getLong(0), getInt(1), getLong(2), getInt(3), getInt(4), getInt(5), getInt(6),
    )

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
