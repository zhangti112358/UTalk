package com.zhangti.utalk.speech.vad

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.reflect.safeCast

/**
 * 基于 Silero VAD 模型的语音活动检测实现。
 *
 * 参考 gkonovalov/android-vad（MIT License）的 Silero 实现：
 * 加载 assets 中的 [MODEL_FILE]（Silero Team, MIT License），通过 ONNX Runtime 逐帧推理。
 * 模型输出当前帧的语音置信度，并维护 LSTM 隐藏状态 h / 细胞状态 c 作为跨帧上下文；
 * 置信度与模式阈值比较得到逐帧预测，再经 [SpeechStateFilter] 迟滞过滤，避免句中
 * 短暂停顿被误判为结束。
 *
 * 支持的采样率与帧长组合：
 *   8000Hz:  256 / 512 / 768 采样点
 *   16000Hz: 512 / 1024 / 1536 采样点
 *
 * @param context 用于从 assets 读取模型文件
 * @param sampleRate 输入音频采样率
 * @param frameSize 每帧采样点数
 * @param mode 检测模式（置信度阈值）
 * @param speechDurationMs 语音段最短时长（ms），0 表示不限制
 * @param silenceDurationMs 静音段最短时长（ms），0 表示不限制
 */
class SileroVad(
    context: Context,
    sampleRate: VadSampleRate = VadSampleRate.SAMPLE_RATE_16K,
    frameSize: VadFrameSize = VadFrameSize.FRAME_SIZE_512,
    mode: VadMode = VadMode.NORMAL,
    speechDurationMs: Int = 0,
    silenceDurationMs: Int = 0,
) : VoiceActivityDetector {

    private val env: OrtEnvironment
    private val session: OrtSession
    private var isInitiated = false

    /** LSTM 隐藏状态（2 层 × 64 维），跨帧传递上下文。 */
    private var h = FloatArray(2 * 64)

    /** LSTM 细胞状态（2 层 × 64 维）。 */
    private var c = FloatArray(2 * 64)

    /** 连续语音迟滞过滤，避免句中停顿被误判为结束。 */
    private val speechFilter = SpeechStateFilter(minSpeechFrames = 0, minSilenceFrames = 0)

    var sampleRate: VadSampleRate = sampleRate
        set(value) {
            require(SUPPORTED_PARAMETERS.containsKey(value)) {
                "VAD 不支持采样率 $value（仅支持 8kHz / 16kHz）！"
            }
            field = value
        }

    var frameSize: VadFrameSize = frameSize
        set(value) {
            require(SUPPORTED_PARAMETERS[sampleRate]?.contains(value) == true) {
                "VAD 不支持采样率 $sampleRate 与帧长 $value 的组合！"
            }
            field = value
        }

    var mode: VadMode = mode

    /** 语音段最短时长（ms），0 表示不限制。 */
    var speechDurationMs: Int = speechDurationMs
        set(value) {
            require(value in 0..MAX_DURATION_MS) {
                "speechDurationMs 应在 0..$MAX_DURATION_MS ms 之间！"
            }
            field = value
            speechFilter.minSpeechFrames =
                AudioUtils.getFramesCount(sampleRate.value, frameSize.value, value)
        }

    /** 静音段最短时长（ms），0 表示不限制。 */
    var silenceDurationMs: Int = silenceDurationMs
        set(value) {
            require(value in 0..MAX_DURATION_MS) {
                "silenceDurationMs 应在 0..$MAX_DURATION_MS ms 之间！"
            }
            field = value
            speechFilter.minSilenceFrames =
                AudioUtils.getFramesCount(sampleRate.value, frameSize.value, value)
        }

    override fun isSpeech(audio: FloatArray): Boolean =
        speechFilter.update(predict(audio))

    override fun isSpeech(audio: ShortArray): Boolean =
        speechFilter.update(predict(AudioUtils.toFloatArray(audio)))

    override fun isSpeech(audio: ByteArray): Boolean =
        speechFilter.update(predict(AudioUtils.toFloatArray(audio)))

    /**
     * 帧级预测：构造输入张量（音频 + 采样率 + h/c 状态）送入模型，
     * 返回置信度是否超过当前模式阈值。
     */
    private fun predict(audioData: FloatArray): Boolean {
        checkState()
        return createInputTensors(audioData).use { tensors ->
            session.run(tensors).use { result ->
                extractResult(result) > threshold()
            }
        }
    }

    /**
     * 提取推理结果：取出语音置信度，同时更新 h / c 状态供下一帧使用。
     */
    private fun extractResult(result: OrtSession.Result): Float {
        val confidence: Array<FloatArray>? = unpack(result, OutputTensors.OUTPUT)

        flattenArray(unpack(result, OutputTensors.CN))?.let { c = it }
        flattenArray(unpack(result, OutputTensors.HN))?.let { h = it }

        return confidence?.getOrNull(0)?.getOrNull(0) ?: 0f
    }

    private inline fun <reified T> unpack(output: OrtSession.Result, index: Int): Array<T>? {
        return try {
            Array<T>::class.safeCast(output.get(index).value)
        } catch (_: OrtException) {
            null
        }
    }

    private fun flattenArray(array: Array<Array<FloatArray>>?): FloatArray? =
        array?.flatten()?.flatMap { it.asIterable() }?.toFloatArray()

    /**
     * 构造模型输入张量：input（音频帧）、sr（采样率）、h / c（LSTM 状态）。
     */
    private fun createInputTensors(audioData: FloatArray): TensorMap<String, OnnxTensor> {
        return TensorMap<String, OnnxTensor>().apply {
            InputTensors.INPUT to OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(audioData),
                longArrayOf(1, frameSize.value.toLong())
            )
            InputTensors.SR to OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(longArrayOf(sampleRate.value.toLong())),
                longArrayOf(1)
            )
            InputTensors.H to OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(h),
                longArrayOf(2, 1, 64)
            )
            InputTensors.C to OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(c),
                longArrayOf(2, 1, 64)
            )
        }
    }

    /** 从 assets 读取模型字节。 */
    private fun loadModel(context: Context): ByteArray =
        context.assets.open(MODEL_FILE).use { it.readBytes() }

    /** 各模式对应的置信度阈值。 */
    private fun threshold(): Float = when (mode) {
        VadMode.NORMAL -> 0.5f
        VadMode.AGGRESSIVE -> 0.8f
        VadMode.VERY_AGGRESSIVE -> 0.95f
        VadMode.OFF -> 0f
    }

    override fun close() {
        checkState()
        isInitiated = false
        session.close()
        env.close()
    }

    private fun checkState() {
        require(isInitiated) { "VAD 会话已关闭，无法继续使用！" }
    }

    /** 输入张量名。 */
    private object InputTensors {
        const val INPUT = "input"
        const val SR = "sr"
        const val H = "h"
        const val C = "c"
    }

    /** 输出张量下标。 */
    private object OutputTensors {
        const val OUTPUT = 0
        const val HN = 1
        const val CN = 2
    }

    init {
        // 先做参数校验，再加载模型
        this.sampleRate = sampleRate
        this.frameSize = frameSize
        this.mode = mode
        this.silenceDurationMs = silenceDurationMs
        this.speechDurationMs = speechDurationMs

        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        this.env = OrtEnvironment.getEnvironment()
        this.session = env.createSession(loadModel(context), sessionOptions)
        sessionOptions.close()
        this.isInitiated = true
    }

    companion object {
        /** assets 中的模型文件名。 */
        const val MODEL_FILE = "silero_vad.onnx"

        private const val MAX_DURATION_MS = 300000

        /** 采样率与帧长的合法组合。 */
        private val SUPPORTED_PARAMETERS: Map<VadSampleRate, Set<VadFrameSize>> = mapOf(
            VadSampleRate.SAMPLE_RATE_8K to setOf(
                VadFrameSize.FRAME_SIZE_256,
                VadFrameSize.FRAME_SIZE_512,
                VadFrameSize.FRAME_SIZE_768
            ),
            VadSampleRate.SAMPLE_RATE_16K to setOf(
                VadFrameSize.FRAME_SIZE_512,
                VadFrameSize.FRAME_SIZE_1024,
                VadFrameSize.FRAME_SIZE_1536
            )
        )
    }
}
