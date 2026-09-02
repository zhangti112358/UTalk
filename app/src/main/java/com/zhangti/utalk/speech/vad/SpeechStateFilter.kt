package com.zhangti.utalk.speech.vad

/**
 * 连续语音状态过滤：对模型逐帧预测结果做迟滞（hysteresis），
 * 避免一句话中间的短暂停顿被误判为结束。
 *
 * 逻辑（参考 gkonovalov/android-vad 的 isContinuousSpeech）：
 * - 语音帧累计超过 [minSpeechFrames] 后进入语音段；
 * - 语音段中连续非语音帧超过 [minSilenceFrames] 才结束语音段。
 *
 * 两个阈值都为 0 时退化为逐帧直出。
 */
internal class SpeechStateFilter(
    /** 触发语音段所需的最少语音帧数 */
    var minSpeechFrames: Int,
    /** 结束语音段所需的最少连续静音帧数 */
    var minSilenceFrames: Int,
) {
    private var speechFramesCount = 0
    private var silenceFramesCount = 0

    /** 输入本帧预测结果，返回过滤后的语音段状态。 */
    fun update(isSpeech: Boolean): Boolean {
        if (isSpeech) {
            if (speechFramesCount <= minSpeechFrames) speechFramesCount++
            if (speechFramesCount > minSpeechFrames) {
                silenceFramesCount = 0
                return true
            }
        } else {
            if (silenceFramesCount <= minSilenceFrames) silenceFramesCount++
            if (silenceFramesCount > minSilenceFrames) {
                speechFramesCount = 0
                return false
            } else if (speechFramesCount > minSpeechFrames) {
                return true
            }
        }
        return false
    }
}
