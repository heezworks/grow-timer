package app.sn62kr.grow.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 친구 서버 클라이언트(design.md 6절). 부품 없이 HttpURLConnection으로(새 부품을 늘리지 않는다).
 * 보내는 것: 기기 키(서버는 해시만 둔다), 친구 코드, 정해진 값만 담은 스냅숏. 활동 이름 같은 자유 글은 보내지 않는다(D59).
 */
class FriendApi(private val base: String) {
    sealed interface Res {
        data class Ok(val body: JsonObject) : Res
        /** 인터넷이 없다. */
        data object Offline : Res
        /** 서버가 쉬는 중(예산·스위치). "친구 화면은 잠시 쉬는 중이에요" */
        data object Paused : Res
        /** 한도(하루 1회 등). */
        data object Limit : Res
        data class Fail(val status: Int, val code: String?) : Res
    }

    val enabled get() = base.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun call(method: String, path: String, key: String?, body: JsonElement? = null): Res = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext Res.Fail(0, "disabled")
        val c = try { URL(base + path).openConnection() as HttpURLConnection } catch (e: IOException) { return@withContext Res.Offline }
        try {
            c.requestMethod = method
            c.connectTimeout = 8_000; c.readTimeout = 10_000
            c.setRequestProperty("content-type", "application/json")
            key?.let { c.setRequestProperty("authorization", "Bearer $it") }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
            val status = c.responseCode
            val text = (if (status < 400) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() } ?: "{}"
            val obj = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrElse { JsonObject(emptyMap()) }
            when {
                status in 200..299 -> Res.Ok(obj)
                status == 503 -> Res.Paused
                status == 429 -> Res.Limit
                else -> Res.Fail(status, obj["error"]?.toString()?.trim('"'))
            }
        } catch (e: IOException) {
            Res.Offline
        } finally { c.disconnect() }
    }
}
