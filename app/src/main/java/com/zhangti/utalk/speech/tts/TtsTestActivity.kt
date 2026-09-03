package com.zhangti.utalk.speech.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 豆包 TTS 手动测试页：输入文本 → 流式合成并播放。
 * 播放中再次点击「播放」即打断并合成新文本。
 */
class TtsTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TtsTestScreen()
                }
            }
        }
    }
}

/**
 * 一次「文本 → 豆包 TTS → 播放」播放器封装。
 */
private class TtsPlayer(
    private val onStatus: (String) -> Unit,
    private val onPlayingChanged: (Boolean) -> Unit,
) {
    private val tts = DoubaoTTS() // API Key 走 AppConfig（secrets.properties）
    private var session: DoubaoTTS.Session? = null
    private var audioTrack: AudioTrack? = null

    /** 已写入的帧数（仅 WS 线程读写），用于结束时判断缓冲是否播完。 */
    private var writtenFrames = 0L

    /** 开始合成并播放；已有会话时先打断（取消旧会话与播放）。 */
    fun speak(text: String) {
        if (text.isBlank()) return
        stop()

        val track = createAudioTrack()
        audioTrack = track
        writtenFrames = 0
        track.play()

        session = tts.startStream(object : DoubaoTTS.Listener {
            // 回调在 OkHttp WS 线程
            override fun onAudio(pcm: ByteArray) {
                runCatching { track.write(pcm, 0, pcm.size) }.onSuccess {
                    writtenFrames += pcm.size / 2 // 16-bit 单声道：2 字节 = 1 帧
                }
            }

            override fun onCompleted() {
                drainAndRelease(track)
                onPlayingChanged(false)
                onStatus("合成完成")
            }

            override fun onError(throwable: Throwable) {
                releaseTrack()
                onPlayingChanged(false)
                onStatus("出错：${throwable.message ?: "未知错误"}")
            }
        })
        onPlayingChanged(true)
        onStatus("合成中…")
        session?.sendText(text)
        session?.finish()
    }

    /**
     * 等 AudioTrack 内部缓冲播完再停止释放。
     * 直接 stop+release 会把缓冲里没播完的音频（通常是最后一个字）截断。
     */
    private fun drainAndRelease(track: AudioTrack) {
        try {
            val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline &&
                track.playbackHeadPosition < writtenFrames
            ) {
                Thread.sleep(20)
            }
        } catch (_: InterruptedException) {
            // 忽略，直接进入释放
        } finally {
            synchronized(this) {
                if (audioTrack === track) {
                    runCatching { track.stop() }
                    runCatching { track.release() }
                    audioTrack = null
                }
            }
        }
    }

    /** 打断：取消当前会话并停止播放。 */
    fun stop() {
        session?.cancel()
        session = null
        releaseTrack()
        onPlayingChanged(false)
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
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
            .setBufferSizeInBytes(maxOf(minBuffer, TtsConstants.SAMPLE_RATE * 2)) // 约 1 秒缓冲
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun releaseTrack() {
        synchronized(this) {
            audioTrack?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
            audioTrack = null
        }
    }

    companion object {
        /** 排空最长等待时间（音频是实时流式写入的，完成时剩余缓冲通常 < 1s）。 */
        private const val DRAIN_TIMEOUT_MS = 3000L
    }
}

@Composable
private fun TtsTestScreen() {
    var input by remember { mutableStateOf("绿蚁新醅酒，红泥小火炉。晚来天欲雪，能饮一杯无？") }
    var status by remember { mutableStateOf("未开始") }
    var playing by remember { mutableStateOf(false) }
    val player = remember {
        TtsPlayer(
            onStatus = { status = it },
            onPlayingChanged = { playing = it },
        )
    }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("豆包 TTS 测试", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Text("状态：$status", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("要合成的文本") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { player.speak(input) } // 播放中再点 = 打断并重播
            ) {
                Text(if (playing) "打断并重播" else "播放")
            }
            Button(onClick = {
                status = "已停止"
                player.stop()
            }) {
                Text("停止")
            }
        }
    }
}
