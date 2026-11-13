package com.triple.fcare.ai

import com.triple.fcare.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini API 호출 (개선사항 2, 사용자 결정 11-12: API 키로 직접 호출).
 *
 * - API 키·모델 이름은 local.properties의 `gemini.apiKey`, `gemini.model`에서 BuildConfig로 들어온다.
 *   local.properties는 .gitignore에 있으므로 GitHub에 올라가지 않는다. (키를 코드에 직접 쓰지 말 것)
 * - 앱을 분해하면 키가 보일 수 있다는 한계가 있다 → 위험관리계획서에 기록, 키에 사용량 제한을 걸어 둘 것.
 * - 실패(인터넷 없음, 키 오류, 한도 초과, 응답 형식 이상)하면 null을 돌려주고, 호출한 쪽은 규칙 문장을 쓴다.
 * - 추가 라이브러리 없이 Android 기본 HttpURLConnection과 org.json을 사용한다.
 */
class GeminiClient(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    val model: String = BuildConfig.GEMINI_MODEL,
) {
    val available: Boolean get() = apiKey.isNotBlank()

    suspend fun generate(system: String, prompt: String): String? = withContext(Dispatchers.IO) {
        if (!available) return@withContext null
        runCatching {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)   // 키는 주소(URL)가 아니라 헤더로 보낸다
            }
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                ))
                .put("generationConfig", JSONObject().put("temperature", 0.4).put("maxOutputTokens", 1024))
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) return@runCatching null
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim()
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    companion object {
        private const val TIMEOUT_MS = 15_000
    }
}
