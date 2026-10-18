package com.zhangti.utalk.agent.tool.web

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** DeepSeek Search / Fetch 原始 JSON API 客户端。 */
interface DeepSeekWebService {
    fun search(body: JSONObject): JSONObject
    fun fetch(body: JSONObject): JSONObject
}

class DeepSeekWebApi(
    private val apiKey: String,
    baseUrl: String,
    private val client: OkHttpClient = sharedClient,
) : DeepSeekWebService {
    private val toolsBaseUrl = baseUrl.trimEnd('/') + "/tools/"

    override fun search(body: JSONObject): JSONObject = post("search", body)

    override fun fetch(body: JSONObject): JSONObject = post("fetch", body)

    private fun post(endpoint: String, body: JSONObject): JSONObject {
        require(apiKey.isNotBlank()) { "请先在设置中填写 DeepSeek API Key" }
        val request = Request.Builder()
            .url(toolsBaseUrl + endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                }.getOrNull().takeUnless { it.isNullOrBlank() }
                throw DeepSeekWebException(response.code, message ?: raw.take(1_000).ifBlank { "请求失败" })
            }
            return runCatching { JSONObject(raw) }
                .getOrElse { throw DeepSeekWebException(response.code, "DeepSeek 返回了无效 JSON") }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val sharedClient by lazy { OkHttpClient() }
    }
}

class DeepSeekWebException(val statusCode: Int, message: String) : Exception("HTTP $statusCode：$message")
