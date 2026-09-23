package com.zhangti.utalk.speech.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.zhangti.utalk.speech.tts.DoubaoTTS
import com.zhangti.utalk.speech.tts.TtsConstants
import kotlin.math.roundToInt

data class PlaybackProgress(
    val spokenPrefix: String,
    val submittedText: String,
    val playedFrames: Long,
    val writtenFrames: Long,
    val estimated: Boolean = true,
)

interface StreamingSpeechPlayer : AutoCloseable {
    val isAudible: Boolean
    fun start()
    fun appendText(delta: String)
    fun finish()
    fun interrupt(): PlaybackProgress

    interface Listener {
        fun onPlaybackStarted() {}
        fun onPlaybackCompleted() {}
        fun onPlaybackError(throwable: Throwable) {}
    }
}

fun interface StreamingSpeechPlayerFactory {
    fun create(listener: StreamingSpeechPlayer.Listener): StreamingSpeechPlayer
}

object DefaultStreamingSpeechPlayerFactory : StreamingSpeechPlayerFactory {
    override fun create(listener: StreamingSpeechPlayer.Listener): StreamingSpeechPlayer =
        StreamingTtsPlayer(listener)
}

/** DeepSeek 文本增量 → 豆包 TTS → AudioTrack 的独立流式播放器。 */
class StreamingTtsPlayer(
    private val listener: StreamingSpeechPlayer.Listener,
) : StreamingSpeechPlayer {

    private val lock = Any()
    private val tts = DoubaoTTS()
    private val pendingText = StringBuilder()
    private val submittedText = StringBuilder()
    private var session: DoubaoTTS.Session? = null
    private var track: AudioTrack? = null
    private var writtenFrames = 0L
    private var receivedAudio = false
    private var finished = false
    private var interrupted = false

    override val isAudible: Boolean
        get() = synchronized(lock) { receivedAudio && track?.playState == AudioTrack.PLAYSTATE_PLAYING }

    override fun start() {
        synchronized(lock) {
            check(session == null) { "TTS 播放器已经启动" }
            val audioTrack = createAudioTrack().also {
                track = it
                it.play()
            }
            session = tts.startStream(object : DoubaoTTS.Listener {
                override fun onAudio(pcm: ByteArray) {
                    var notifyStarted = false
                    synchronized(lock) {
                        if (interrupted || track !== audioTrack) return
                        val written = audioTrack.write(pcm, 0, pcm.size)
                        if (written > 0) {
                            writtenFrames += written / 2
                            if (!receivedAudio) {
                                receivedAudio = true
                                notifyStarted = true
                            }
                        }
                    }
                    // 外部回调不能发生在播放器锁内，避免与 VAD 打断线程形成反向锁顺序。
                    if (notifyStarted) listener.onPlaybackStarted()
                }

                override fun onCompleted() {
                    Thread({ drainAndComplete(audioTrack) }, "TtsAudioDrain").start()
                }

                override fun onError(throwable: Throwable) {
                    synchronized(lock) {
                        if (interrupted) return
                        releaseTrackLocked()
                    }
                    listener.onPlaybackError(throwable)
                }
            })
        }
    }

    /** 接受任意大小的模型文本分片，按句子或长度批量发送，避免逐 token 请求 TTS。 */
    override fun appendText(delta: String) {
        if (delta.isEmpty()) return
        synchronized(lock) {
            if (finished || interrupted) return
            pendingText.append(delta)
            if (pendingText.any { it in SENTENCE_ENDINGS } || pendingText.length >= MAX_CHUNK_CHARS) {
                flushLocked()
            }
        }
    }

    override fun finish() {
        synchronized(lock) {
            if (finished || interrupted) return
            finished = true
            flushLocked()
            session?.finish()
        }
    }

    /** 停止合成和播放，返回根据 PCM 播放头估算的已播报文字前缀。 */
    override fun interrupt(): PlaybackProgress = synchronized(lock) {
        val progress = progressLocked()
        interrupted = true
        session?.cancel()
        session = null
        releaseTrackLocked()
        progress
    }

    private fun flushLocked() {
        if (pendingText.isEmpty()) return
        val text = pendingText.toString()
        pendingText.clear()
        submittedText.append(text)
        session?.sendText(text)
    }

    private fun progressLocked(): PlaybackProgress {
        val written = writtenFrames.coerceAtLeast(0)
        val played = playbackFramesLocked().coerceIn(0, written)
        val ratio = if (written == 0L) 0.0 else played.toDouble() / written
        val count = (submittedText.length * ratio).roundToInt().coerceIn(0, submittedText.length)
        return PlaybackProgress(
            spokenPrefix = submittedText.substring(0, count),
            submittedText = submittedText.toString(),
            playedFrames = played,
            writtenFrames = written,
        )
    }

    private fun playbackFramesLocked(): Long =
        track?.playbackHeadPosition?.toLong()?.and(0xffffffffL) ?: 0L

    private fun drainAndComplete(expectedTrack: AudioTrack) {
        try {
            val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
            while (!interrupted && System.currentTimeMillis() < deadline) {
                val done = synchronized(lock) {
                    track !== expectedTrack || playbackFramesLocked() >= writtenFrames
                }
                if (done) break
                Thread.sleep(20)
            }
        } catch (_: InterruptedException) {
            // 直接释放。
        } finally {
            val notify = synchronized(lock) {
                if (interrupted || track !== expectedTrack) false
                else {
                    releaseTrackLocked()
                    true
                }
            }
            if (notify) listener.onPlaybackCompleted()
        }
    }

    private fun releaseTrackLocked() {
        track?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
    }

    private fun createAudioTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            TtsConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // 助手回复按媒体/助手路由外放；只有麦克风走 VOICE_COMMUNICATION 的 AEC 采集路径。
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(TtsConstants.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, TtsConstants.SAMPLE_RATE * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    override fun close() {
        synchronized(lock) {
            interrupted = true
            session?.cancel()
            session = null
            releaseTrackLocked()
        }
    }

    companion object {
        private val SENTENCE_ENDINGS = setOf('。', '！', '？', '；', '.', '!', '?', ';', '\n')
        private const val MAX_CHUNK_CHARS = 48
        private const val DRAIN_TIMEOUT_MS = 5000L
    }
}
