package com.zhangti.utalk.speech.tts

import android.util.Log
import com.zhangti.utalk.AppConfig
import com.zhangti.utalk.speech.tts.TtsConstants.DEFAULT_HEADER_SIZE
import com.zhangti.utalk.speech.tts.TtsConstants.DEFAULT_SPEAKER
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_CANCEL_SESSION
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_CONNECTION_FAILED
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_CONNECTION_FINISHED
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_CONNECTION_STARTED
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_FINISH_CONNECTION
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_FINISH_SESSION
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_SESSION_FAILED
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_SESSION_FINISHED
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_SESSION_STARTED
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_START_CONNECTION
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_START_SESSION
import com.zhangti.utalk.speech.tts.TtsConstants.EVENT_TASK_REQUEST
import com.zhangti.utalk.speech.tts.TtsConstants.FLAG_WITH_EVENT
import com.zhangti.utalk.speech.tts.TtsConstants.GZIP
import com.zhangti.utalk.speech.tts.TtsConstants.JSON
import com.zhangti.utalk.speech.tts.TtsConstants.MSG_AUDIO_ONLY_SERVER
import com.zhangti.utalk.speech.tts.TtsConstants.MSG_ERROR
import com.zhangti.utalk.speech.tts.TtsConstants.MSG_FULL_CLIENT_REQUEST
import com.zhangti.utalk.speech.tts.TtsConstants.MSG_FULL_SERVER_RESPONSE
import com.zhangti.utalk.speech.tts.TtsConstants.NO_COMPRESSION
import com.zhangti.utalk.speech.tts.TtsConstants.PROTOCOL_VERSION
import com.zhangti.utalk.speech.tts.TtsConstants.RESOURCE_ID
import com.zhangti.utalk.speech.tts.TtsConstants.SAMPLE_RATE
import com.zhangti.utalk.speech.tts.TtsConstants.TTS_URL
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/**
 * 豆包双向流式语音合成（TTS）封装：输入文本，流式返回 PCM 音频。
 *
 * - 接口：双向流式 `wss://openspeech.bytedance.com/api/v3/tts/bidirection`
 * - 鉴权：新版控制台，仅使用 `X-Api-Key`（默认取 [AppConfig.instance.doubaoApiKey]）
 * - 资源：`seed-tts-2.0`；音色：默认 `zh_female_vv_uranus_bigtts`
 * - 音频：pcm / 16000Hz / 单声道 / 16bit
 *
 * 协议交互：StartConnection → ConnectionStarted → StartSession → SessionStarted →
 * 发送 TaskRequest 文本 → FinishSession → 期间持续收音频帧 → SessionFinished → FinishConnection。
 *
 * 打断：调用 [Session.cancel]（发送 CancelSession）后立即 [startStream] 开新会话即可。
 *
 * 使用方式：
 * ```kotlin
 * val session = DoubaoTTS().startStream(object : DoubaoTTS.Listener {
 *     override fun onAudio(pcm: ByteArray) { /* 写入 AudioTrack */ }
 *     override fun onCompleted() {}
 *     override fun onError(throwable: Throwable) {}
 * })
 * session.sendText("你好")
 * session.finish()   // 文本发送完毕
 * ```
 */
class DoubaoTTS(
    private val apiKey: String = AppConfig.instance.doubaoApiKey,
    private val speaker: String = DEFAULT_SPEAKER,
    private val sampleRate: Int = SAMPLE_RATE,
) {

    /** 合成结果回调（回调线程为 OkHttp WS 线程，写音频/更新 UI 请自行切线程）。 */
    interface Listener {
        /** 收到一段合成音频（PCM 16kHz/16bit/mono，Raw 未压缩）。 */
        fun onAudio(pcm: ByteArray)

        /** 合成正常结束（收到 SessionFinished）。 */
        fun onCompleted() {}

        /** 合成出错。 */
        fun onError(throwable: Throwable) {}
    }

    /** 一次流式会话句柄。 */
    inner class Session(
        private val webSocket: WebSocket,
        private val client: OkHttpClient,
        internal val sessionId: String,
    ) {
        @Volatile
        internal var finished = false

        /** 会话是否已被主动取消，取消后不再推进状态机或回调上层。 */
        @Volatile
        internal var cancelled = false

        /**
         * 会话是否已就绪（收到 SessionStarted）。
         * 就绪前发出的 TaskRequest 会被服务端丢弃，故暂存文本待就绪后按序补发。
         */
        @Volatile
        internal var ready = false

        private val pending = ArrayList<String>()

        /** 就绪前调用了 finish()：记录意图，待 markReady 后补发 FinishSession。 */
        private var finishRequested = false

        /** 由收到 SessionStarted 时调用：按序补发暂存文本与 FinishSession。 */
        internal fun markReady() {
            val buffered: List<String>
            val needFinish: Boolean
            synchronized(pending) {
                ready = true
                buffered = ArrayList(pending)
                pending.clear()
                needFinish = finishRequested
            }
            if (cancelled) return
            for (t in buffered) {
                webSocket.send(buildTaskRequest(sessionId, t).toByteString())
            }
            if (needFinish) {
                webSocket.send(buildFinishSession(sessionId).toByteString())
            }
        }

        /** 发送一段待合成文本（可多次调用，模拟流式输入）。 */
        fun sendText(text: String) {
            if (finished || text.isEmpty()) return
            synchronized(pending) {
                if (!ready) {
                    pending.add(text)
                    return
                }
            }
            webSocket.send(buildTaskRequest(sessionId, text).toByteString())
        }

        /** 文本发送完毕，发送 FinishSession 触发服务端收尾。 */
        fun finish() {
            if (finished) return
            finished = true
            synchronized(pending) {
                // 尚未就绪：仅记录意图，等 markReady 时按序补发，避免抢在 SessionStarted 前被丢弃。
                if (!ready) {
                    finishRequested = true
                    return
                }
            }
            webSocket.send(buildFinishSession(sessionId).toByteString())
        }

        /** 打断/取消当前会话：发送 CancelSession 后强制断开。 */
        fun cancel() {
            finished = true
            cancelled = true
            runCatching { webSocket.send(buildCancelSession(sessionId).toByteString()) }
            runCatching { webSocket.close(1000, "client cancel") }
            runCatching { webSocket.cancel() }
            // client 为进程级共享实例，只关本次 WebSocket，不能 shutdown 线程池。
        }

        internal fun release() {
            // 共享 client，无需 shutdown。
        }
    }

    /** 建立 WebSocket 并发送 StartConnection，返回可持续投喂文本的 [Session]。 */
    fun startStream(listener: Listener): Session {
        val client = sharedClient

        val request = Request.Builder()
            .url(TTS_URL)
            .header("X-Api-Key", apiKey)
            .header("X-Api-Resource-Id", RESOURCE_ID)
            .header("X-Api-Connect-Id", UUID.randomUUID().toString())
            .build()

        val sessionId = UUID.randomUUID().toString()
        lateinit var session: Session
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (session.cancelled) {
                    runCatching { webSocket.cancel() }
                    return
                }
                Log.i(TAG, "ws connected, X-Tt-Logid=${response.header("X-Tt-Logid")}")
                webSocket.send(buildStartConnection().toByteString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (session.cancelled) return
                val r = parseResponse(bytes.toByteArray())
                when (r.messageType) {
                    MSG_ERROR.toInt() -> {
                        val msg = r.payload?.toString(Charsets.UTF_8)
                        Log.e(TAG, "server error code=${r.errorCode} payload=$msg")
                        listener.onError(RuntimeException("TTS error ${r.errorCode}: $msg"))
                    }
                    MSG_AUDIO_ONLY_SERVER.toInt() -> {
                        // 音频帧：Raw PCM 数据
                        r.payload?.takeIf { it.isNotEmpty() }?.let { listener.onAudio(it) }
                    }
                    MSG_FULL_SERVER_RESPONSE.toInt() -> handleServerEvent(webSocket, session, r, listener)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (session.cancelled) return
                Log.e(TAG, "ws failure", t)
                listener.onError(t)
                session.release()
            }
        })

        session = Session(ws, client, sessionId)
        return session
    }

    /** 处理服务端事件（状态机推进）。 */
    private fun handleServerEvent(webSocket: WebSocket, session: Session, r: TtsResponse, listener: Listener) {
        when (r.event) {
            EVENT_CONNECTION_STARTED -> {
                webSocket.send(buildStartSession(session.sessionId).toByteString())
            }
            EVENT_CONNECTION_FAILED -> {
                listener.onError(RuntimeException("ConnectionFailed: ${r.payload?.toString(Charsets.UTF_8)}"))
            }
            EVENT_SESSION_STARTED -> {
                session.markReady()
            }
            EVENT_SESSION_FINISHED -> {
                listener.onCompleted()
                webSocket.send(buildFinishConnection().toByteString())
            }
            EVENT_SESSION_FAILED -> {
                listener.onError(RuntimeException("SessionFailed: ${r.payload?.toString(Charsets.UTF_8)}"))
            }
            EVENT_CONNECTION_FINISHED -> {
                webSocket.close(1000, "done")
            }
        }
    }

    // ── 协议编码 ──

    private fun sessionPayload(): ByteArray {
        val body = JSONObject().apply {
            put("user", JSONObject().apply { put("uid", "utalk") })
            put("namespace", "BidirectionalTTS")
            put("event", EVENT_START_SESSION)
            put("req_params", JSONObject().apply {
                put("speaker", speaker)
                put("audio_params", JSONObject().apply {
                    put("format", "pcm")
                    put("sample_rate", sampleRate)
                    put("channel", 1)
                    put("bits", 16)
                })
            })
        }
        return body.toString().toByteArray(Charsets.UTF_8)
    }

    private fun taskPayload(text: String): ByteArray {
        val body = JSONObject().apply {
            put("event", EVENT_TASK_REQUEST)
            put("req_params", JSONObject().apply { put("text", text) })
        }
        return body.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildStartConnection(): ByteArray =
        buildEventFrame(EVENT_START_CONNECTION, sessionId = null, "{}".toByteArray(Charsets.UTF_8))

    private fun buildFinishConnection(): ByteArray =
        buildEventFrame(EVENT_FINISH_CONNECTION, sessionId = null, "{}".toByteArray(Charsets.UTF_8))

    private fun buildStartSession(sessionId: String): ByteArray =
        buildEventFrame(EVENT_START_SESSION, sessionId, sessionPayload())

    private fun buildFinishSession(sessionId: String): ByteArray =
        buildEventFrame(EVENT_FINISH_SESSION, sessionId, "{}".toByteArray(Charsets.UTF_8))

    private fun buildCancelSession(sessionId: String): ByteArray =
        buildEventFrame(EVENT_CANCEL_SESSION, sessionId, "{}".toByteArray(Charsets.UTF_8))

    private fun buildTaskRequest(sessionId: String, text: String): ByteArray =
        buildEventFrame(EVENT_TASK_REQUEST, sessionId, taskPayload(text))

    /**
     * 组装带事件号的客户端请求帧：
     * header(4B) + event(int32 BE) + [sessionId_size(uint32 BE) + sessionId] + payload_size(uint32 BE) + payload
     * 连接级事件（StartConnection/FinishConnection）不携带 sessionId。
     */
    private fun buildEventFrame(event: Int, sessionId: String?, payload: ByteArray): ByteArray {
        val header = makeHeader(MSG_FULL_CLIENT_REQUEST, FLAG_WITH_EVENT, JSON, NO_COMPRESSION)
        val out = ByteArrayOutputStream()
        out.write(header)
        out.write(intToBytes(event))
        val isConnectionEvent = event == EVENT_START_CONNECTION || event == EVENT_FINISH_CONNECTION
        if (!isConnectionEvent && sessionId != null) {
            val idBytes = sessionId.toByteArray(Charsets.UTF_8)
            out.write(intToBytes(idBytes.size))
            out.write(idBytes)
        }
        out.write(intToBytes(payload.size))
        out.write(payload)
        return out.toByteArray()
    }

    // ── 协议解码 ──

    private class TtsResponse {
        var messageType = 0
        var event = 0
        var errorCode = 0
        var payload: ByteArray? = null
    }

    private fun parseResponse(res: ByteArray): TtsResponse {
        val result = TtsResponse()
        if (res.size < 4) return result

        val headerSize = (res[0].toInt() and 0x0F) * 4
        val messageType = (res[1].toInt() shr 4) and 0x0F
        val flags = res[1].toInt() and 0x0F
        val compression = res[2].toInt() and 0x0F
        result.messageType = messageType

        var offset = headerSize

        if (messageType == MSG_ERROR.toInt()) {
            // 错误帧：error_code(4B) + payload_size(4B) + payload
            result.errorCode = bytesToInt(res, offset); offset += 4
            val size = bytesToInt(res, offset); offset += 4
            result.payload = res.copyOfRange(offset, minOf(offset + size, res.size))
            return result
        }

        // 数据/响应帧携带事件号（flag 含 WithEvent）
        val withEvent = (flags and FLAG_WITH_EVENT.toInt()) != 0
        if (withEvent) {
            result.event = bytesToInt(res, offset); offset += 4
            when (result.event) {
                // 连接级事件携带 connectId，其余携带 sessionId
                EVENT_CONNECTION_STARTED, EVENT_CONNECTION_FAILED, EVENT_CONNECTION_FINISHED -> {
                    if (offset + 4 <= res.size) {
                        val cidSize = bytesToInt(res, offset); offset += 4
                        offset += cidSize
                    }
                }
                else -> {
                    if (offset + 4 <= res.size) {
                        val sidSize = bytesToInt(res, offset); offset += 4
                        offset += sidSize
                    }
                }
            }
        }

        if (offset + 4 > res.size) return result
        val size = bytesToInt(res, offset); offset += 4
        var payload = res.copyOfRange(offset, minOf(offset + size, res.size))
        if (compression == GZIP.toInt() && payload.isNotEmpty()) {
            payload = runCatching { gunzip(payload) }.getOrElse { payload }
        }
        result.payload = payload
        return result
    }

    // ── 底层工具 ──

    private fun makeHeader(msgType: Byte, flags: Byte, serial: Byte, compress: Byte): ByteArray {
        return byteArrayOf(
            ((PROTOCOL_VERSION.toInt() shl 4) or DEFAULT_HEADER_SIZE.toInt()).toByte(),
            ((msgType.toInt() shl 4) or flags.toInt()).toByte(),
            ((serial.toInt() shl 4) or compress.toInt()).toByte(),
            0x00,
        )
    }

    private fun intToBytes(v: Int): ByteArray = byteArrayOf(
        (v shr 24 and 0xFF).toByte(),
        (v shr 16 and 0xFF).toByte(),
        (v shr 8 and 0xFF).toByte(),
        (v and 0xFF).toByte(),
    )

    private fun bytesToInt(src: ByteArray, offset: Int): Int =
        (src[offset].toInt() and 0xFF shl 24) or
            (src[offset + 1].toInt() and 0xFF shl 16) or
            (src[offset + 2].toInt() and 0xFF shl 8) or
            (src[offset + 3].toInt() and 0xFF)

    private fun gunzip(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(src.inputStream()).use { it.copyTo(out) }
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "DoubaoTTS"

        /** 进程级共享 OkHttpClient：复用 DNS/TLS/连接池。 */
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
