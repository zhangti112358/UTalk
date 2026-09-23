package com.zhangti.utalk.speech.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import androidx.core.content.ContextCompat
import com.zhangti.utalk.speech.asr.AsrConstants

/** Android 麦克风实现；通信输入源优先使用系统 AEC 抑制扬声器回声。 */
class MicrophonePcmSource(
    context: Context,
    private val frameSamples: Int = 512,
) : PcmAudioSource {
    private val appContext = context.applicationContext
    @Volatile private var running = false
    @Volatile private var aecActive = false
    private var recorder: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var worker: Thread? = null

    override val isRunning: Boolean get() = running
    override val isEchoCancellationActive: Boolean get() = running && aecActive

    override fun start(listener: PcmAudioSource.Listener) {
        check(!running) { "麦克风已经启动" }
        check(
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        ) { "未授予麦克风权限" }
        val frameBytes = frameSamples * 2
        val minBuffer = AudioRecord.getMinBufferSize(
            AsrConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            AsrConstants.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, frameBytes * 4),
        )
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }
        recorder = audioRecord
        echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                AcousticEchoCanceler.create(audioRecord.audioSessionId)?.also { effect ->
                    if (!effect.enabled) effect.enabled = true
                    Log.i(TAG, "AEC enabled=${effect.enabled}, session=${audioRecord.audioSessionId}")
                    if (!effect.enabled) Log.w(TAG, "系统 AEC 已创建但未能启用")
                }
            }.onFailure { Log.w(TAG, "AEC 初始化失败", it) }.getOrNull()
        } else null
        aecActive = runCatching { echoCanceler?.enabled == true }.getOrDefault(false)
        if (echoCanceler == null) Log.w(TAG, "设备未提供可用的系统 AEC")
        running = true
        worker = Thread({ capture(audioRecord, frameBytes, listener) }, "VoicePcmCapture").apply { start() }
    }

    private fun capture(record: AudioRecord, frameBytes: Int, listener: PcmAudioSource.Listener) {
        try {
            record.startRecording()
            val frame = ByteArray(frameBytes)
            var offset = 0
            while (running) {
                val read = record.read(frame, offset, frame.size - offset)
                if (read < 0) error("麦克风读取失败：$read")
                if (read == 0) continue
                offset += read
                if (offset == frame.size) {
                    listener.onFrame(frame.copyOf())
                    offset = 0
                }
            }
        } catch (t: Throwable) {
            if (running) listener.onError(t)
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            if (recorder === record) recorder = null
        }
    }

    override fun stop() {
        running = false
        aecActive = false
        // stop() 会唤醒正在阻塞的 read。
        recorder?.let { runCatching { it.stop() } }
        echoCanceler?.release()
        echoCanceler = null
    }

    override fun close() = stop()

    companion object {
        private const val TAG = "MicrophonePcmSource"
    }
}
