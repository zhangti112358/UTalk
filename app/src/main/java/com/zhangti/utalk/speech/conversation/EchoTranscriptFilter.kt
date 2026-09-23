package com.zhangti.utalk.speech.conversation

/** 播报期间的 ASR 结果若只是当前回复的一段，就不当作用户插话。 */
internal object EchoTranscriptFilter {
    fun isLikelyEcho(transcript: String, response: String): Boolean {
        val recognized = normalize(transcript)
        val spoken = normalize(response)
        if (recognized.isEmpty() || spoken.isEmpty()) return false
        if (spoken.contains(recognized)) return true
        if (recognized.length < 5) return false

        // 识别可能漏掉/听错少数字；只和同长度的回复片段比，避免短公共词误判。
        val maxErrors = (recognized.length / 5).coerceIn(1, 3)
        return distanceToSubstring(recognized, spoken) <= maxErrors
    }

    private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    private fun distanceToSubstring(a: String, b: String): Int {
        // 空的匹配起点可以落在回复的任何位置。
        var previous = IntArray(b.length + 1)
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(
                    previous[j + 1] + 1,
                    current[j] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1,
                )
            }
            previous = current
        }
        return previous.min()
    }
}
