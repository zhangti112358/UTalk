package com.zhangti.utalk.speech.asr

import android.util.Log
import com.zhangti.utalk.AppConfig
import com.zhangti.utalk.speech.asr.AsrConstants.ASR_URL
import com.zhangti.utalk.speech.asr.AsrConstants.CLIENT_AUDIO_ONLY_REQUEST
import com.zhangti.utalk.speech.asr.AsrConstants.CLIENT_FULL_REQUEST
import com.zhangti.utalk.speech.asr.AsrConstants.DEFAULT_HEADER_SIZE
import com.zhangti.utalk.speech.asr.AsrConstants.GZIP
import com.zhangti.utalk.speech.asr.AsrConstants.JSON
import com.zhangti.utalk.speech.asr.AsrConstants.NEG_WITH_SEQUENCE
import com.zhangti.utalk.speech.asr.AsrConstants.NO_SERIALIZATION
import com.zhangti.utalk.speech.asr.AsrConstants.POS_SEQUENCE
import com.zhangti.utalk.speech.asr.AsrConstants.PROTOCOL_VERSION
import com.zhangti.utalk.speech.asr.AsrConstants.RESOURCE_ID
import com.zhangti.utalk.speech.asr.AsrConstants.SAMPLE_RATE
import com.zhangti.utalk.speech.asr.AsrConstants.SERVER_ERROR_RESPONSE
import com.zhangti.utalk.speech.asr.AsrConstants.SERVER_FULL_RESPONSE
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/**
 * 豆包流式语音识别（ASR）封装。
 *
 * - 接口：双向流式 `wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async`
 * - 鉴权：新版控制台，仅使用 `X-Api-Key`（默认取 [AppConfig.instance.doubaoApiKey]）
 * - 资源：`volc.seedasr.sauc.duration`
 *
 * 使用方式（实时流）：
 * ```kotlin
 * val session = DoubaoAsr().startStream(object : DoubaoAsr.Listener {
 *     override fun onResult(text: String, isFinal: Boolean) { /* 显示，注意切主线程 */ }
 *     override fun onError(throwable: Throwable) {}
 *     override fun onCompleted() {}
 * })
 * // 边采集边送 16kHz/16bit/mono 裸 PCM（连接未就绪时自动暂存，就绪后按序补发）
 * session.sendPcm(pcmChunk)
 * // ...
 * session.finish()   // 发送最后一包并等待收尾
 * ```
 */
class DoubaoAsr(
    private val apiKey: String = AppConfig.instance.doubaoApiKey,
) {

    /** 识别结果回调（回调线程为 OkHttp WS 线程，UI 更新请自行切主线程）。 */
    interface Listener {
        /**
         * @param text 当前累计识别文本
         * @param isFinal 是否为最终结果（最后一包）
         */
        fun onResult(text: String, isFinal: Boolean)

        /** 识别出错。 */
        fun onError(throwable: Throwable) {}

        /** 识别正常结束（收到最后一包）。 */
        fun onCompleted() {}
    }

    /** 一次流式会话句柄。 */
    inner class Session(
        private val webSocket: WebSocket,
        private val client: OkHttpClient,
    ) {
        /** 音频包序号从 2 开始（1 已用于 full request）。 */
        private val seq = AtomicInteger(1)

        @Volatile
        private var finished = false

        /** 是否已被主动取消（取消后不再回调上层，避免关闭后仍报错）。 */
        @Volatile
        internal var cancelled = false

        /**
         * 是否已就绪（full request 已在 onOpen 发出）。
         * 建连成功前音频包不能抢在 full request（seq=1）之前发出，
         * 否则服务端报序号不匹配；就绪前暂存到 [pending]，onOpen 后按序补发。
         */
        @Volatile
        private var ready = false

        /** full request 发出前暂存的音频包。 */
        private val pending = ArrayList<ByteArray>()

        /** 未就绪时调用 finish：尾包数据暂存于此，待 markReady 时补发。 */
        @Volatile
        private var pendingFinish = false
        @Volatile
        private var pendingLast: ByteArray? = null

        /** 由 onOpen 调用：full request 已发出，补发建连期间暂存的音频包与尾包。 */
        internal fun markReady() {
            val buffered: List<ByteArray>
            val tail: ByteArray?
            val needTail: Boolean
            synchronized(pending) {
                ready = true
                buffered = ArrayList(pending)
                pending.clear()
                needTail = pendingFinish
                tail = pendingLast
                pendingFinish = false
                pendingLast = null
            }
            for (data in buffered) {
                if (cancelled) break
                val s = seq.incrementAndGet()
                webSocket.send(buildAudioRequest(s, data, isLast = false).toByteString())
            }
            if (needTail) {
                val s = seq.incrementAndGet()
                webSocket.send(buildAudioRequest(s, tail ?: ByteArray(0), isLast = true).toByteString())
            }
        }

        /** 发送一段裸 PCM（16kHz/16bit/mono），非最后一包。 */
        fun sendPcm(pcm: ByteArray, size: Int = pcm.size) {
            if (finished || size <= 0) return
            val data = if (size == pcm.size) pcm else pcm.copyOf(size)
            synchronized(pending) {
                if (!ready) {
                    pending.add(data)
                    return
                }
            }
            val s = seq.incrementAndGet()
            webSocket.send(buildAudioRequest(s, data, isLast = false).toByteString())
        }

        /** 发送最后一包（可为空音频），标记 is_last，触发服务端收尾。 */
        fun finish(lastPcm: ByteArray? = null) {
            if (finished) return
            finished = true
            // 尚未就绪时不能直接发送（音频会抢在 full request 之前），
            // 暂存尾包，由 markReady 在 full request 之后按序补发。
            synchronized(pending) {
                if (!ready) {
                    pendingLast = lastPcm ?: ByteArray(0)
                    pendingFinish = true
                    return
                }
            }
            val s = seq.incrementAndGet()
            val data = lastPcm ?: ByteArray(0)
            webSocket.send(buildAudioRequest(s, data, isLast = true).toByteString())
        }

        /** 主动中断会话并释放连接。 */
        fun cancel() {
            finished = true
            cancelled = true
            // 先发一个 is_last 空尾包，告知服务端会话正常结束，规避等待超时报错。仅已就绪时发。
            runCatching {
                if (ready) {
                    val s = seq.incrementAndGet()
                    webSocket.send(buildAudioRequest(s, ByteArray(0), isLast = true).toByteString())
                }
            }
            runCatching { webSocket.close(1000, "client cancel") }
            runCatching { webSocket.cancel() }
            // client 为进程级共享实例，只关本次 WebSocket，不能 shutdown 线程池。
        }

        internal fun release() {
            // 共享 client，无需 shutdown。
        }
    }

    /** 建立 WebSocket 并发送 full request，返回可持续投喂 PCM 的 [Session]。 */
    fun startStream(listener: Listener): Session {
        val client = sharedClient

        val request = Request.Builder()
            .url(ASR_URL)
            .header("X-Api-Key", apiKey)
            .header("X-Api-Resource-Id", RESOURCE_ID)
            .header("X-Api-Request-Id", UUID.randomUUID().toString())
            .build()

        lateinit var session: Session
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (session.cancelled) {
                    runCatching { webSocket.cancel() }
                    return
                }
                Log.i(TAG, "ws connected, X-Tt-Logid=${response.header("X-Tt-Logid")}")
                webSocket.send(buildFullRequest(1).toByteString())
                session.markReady()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (session.cancelled) return
                val r = parseResponse(bytes.toByteArray())
                if (r.code != 0) {
                    Log.e(TAG, "server error code=${r.code} payload=${r.rawPayload}")
                    listener.onError(RuntimeException("ASR error ${r.code}: ${r.rawPayload}"))
                    return
                }
                if (r.text != null) {
                    Log.i(TAG, "ASR 识别结果: ${r.text}")
                    listener.onResult(r.text!!, r.isLast)
                }
                if (r.isLast) {
                    listener.onCompleted()
                    webSocket.close(1000, "done")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (session.cancelled) return
                Log.e(TAG, "ws failure", t)
                listener.onError(t)
                session.release()
            }
        })

        session = Session(ws, client)
        return session
    }

    // ── 协议编码 ──

    private fun buildFullRequest(seq: Int): ByteArray {
        val payload = JSONObject().apply {
            put("user", JSONObject().apply { put("uid", "utalk") })
            put("audio", JSONObject().apply {
                put("format", "pcm")
                put("codec", "raw")
                put("rate", SAMPLE_RATE)
                put("bits", 16)
                put("channel", 1)
            })
            put("request", JSONObject().apply {
                put("model_name", "bigmodel")
                put("enable_itn", true)
                put("enable_punc", true)
                put("enable_ddc", true)
                put("show_utterances", true)
                put("enable_nonstream", false)
            })
        }
        val compressed = gzip(payload.toString().toByteArray(Charsets.UTF_8))
        val header = makeHeader(CLIENT_FULL_REQUEST, POS_SEQUENCE, JSON, GZIP)
        return assembleFrame(header, seq, compressed)
    }

    private fun buildAudioRequest(seq: Int, data: ByteArray, isLast: Boolean): ByteArray {
        val flags = if (isLast) NEG_WITH_SEQUENCE else POS_SEQUENCE
        val actualSeq = if (isLast) -seq else seq
        val compressed = gzip(data)
        val header = makeHeader(CLIENT_AUDIO_ONLY_REQUEST, flags, NO_SERIALIZATION, GZIP)
        return assembleFrame(header, actualSeq, compressed)
    }

    private fun assembleFrame(header: ByteArray, seq: Int, payload: ByteArray): ByteArray {
        val seqBytes = intToBytes(seq)
        val sizeBytes = intToBytes(payload.size)
        val frame = ByteArray(header.size + seqBytes.size + sizeBytes.size + payload.size)
        var p = 0
        header.copyInto(frame, p); p += header.size
        seqBytes.copyInto(frame, p); p += seqBytes.size
        sizeBytes.copyInto(frame, p); p += sizeBytes.size
        payload.copyInto(frame, p)
        return frame
    }

    // ── 协议解码 ──

    private class AsrResponse {
        var code = 0
        var isLast = false
        var text: String? = null
        var rawPayload: String? = null // 服务端返回的原始 JSON（调试用）
    }

    private fun parseResponse(res: ByteArray): AsrResponse {
        val result = AsrResponse()
        if (res.size < 4) return result

        val headerSize = (res[0].toInt() and 0x0F) * 4
        val messageType = (res[1].toInt() shr 4) and 0x0F
        val flags = res[1].toInt() and 0x0F
        val compression = res[2].toInt() and 0x0F

        var payload = res.copyOfRange(headerSize, res.size)

        if (flags and 0x01 != 0) payload = payload.copyOfRange(4, payload.size) // sequence
        if (flags and 0x02 != 0) result.isLast = true
        if (flags and 0x04 != 0) payload = payload.copyOfRange(4, payload.size) // event

        when (messageType) {
            SERVER_FULL_RESPONSE.toInt() -> {
                val size = bytesToInt(payload, 0)
                payload = payload.copyOfRange(4, 4 + size)
            }
            SERVER_ERROR_RESPONSE.toInt() -> {
                result.code = bytesToInt(payload, 0)
                val size = bytesToInt(payload, 4)
                payload = payload.copyOfRange(8, 8 + size)
            }
        }

        if (payload.isEmpty()) return result
        if (compression == GZIP.toInt()) {
            payload = runCatching { gunzip(payload) }.getOrElse { return result }
        }

        val payloadStr = String(payload, Charsets.UTF_8)
        result.rawPayload = payloadStr
        runCatching {
            val json = JSONObject(payloadStr)
            result.text = json.optJSONObject("result")?.optString("text")?.takeIf { it.isNotEmpty() }
        }
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

    private fun gzip(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(src) }
        return out.toByteArray()
    }

    private fun gunzip(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(src.inputStream()).use { it.copyTo(out) }
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "DoubaoAsr"

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
