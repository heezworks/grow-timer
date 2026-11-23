package app.sn62kr.grow.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 책 고르기: 앱이 Open Library에 직접 묻는다(우리 서버를 거치지 않는다, design.md 6절). 검색어는 Open Library로만 간다.
 * 고른 책은 작품 ID(OL…W)만 친구에게 간다. 친구 폰은 그 ID로 제목을 다시 묻는다.
 */
object OpenLibrary {
    data class Book(val id: String, val title: String, val author: String?, val year: Int?)

    private val json = Json { ignoreUnknownKeys = true }
    private val titles = HashMap<String, String>()

    private fun get(url: String): JsonObject? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000; c.readTimeout = 10_000
        c.setRequestProperty("user-agent", "grow-timer-android (open source)") // 이름·연락처는 넣지 않는다
        try { if (c.responseCode != 200) null else json.parseToJsonElement(c.inputStream.use { it.readBytes().decodeToString() }) as JsonObject } finally { c.disconnect() }
    }.getOrNull()

    suspend fun search(q: String): List<Book>? = withContext(Dispatchers.IO) {
        val body = get("https://openlibrary.org/search.json?limit=12&fields=key,title,author_name,first_publish_year&q=" + URLEncoder.encode(q.trim(), "UTF-8")) ?: return@withContext null
        body["docs"]?.jsonArray?.mapNotNull { d ->
            val o = d.jsonObject
            val key = o["key"]?.jsonPrimitive?.content ?: return@mapNotNull null // "/works/OL45883W"
            val id = key.substringAfterLast('/').takeIf { Regex("^OL\\d{1,10}W$").matches(it) } ?: return@mapNotNull null
            Book(id, o["title"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                o["author_name"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content, o["first_publish_year"]?.jsonPrimitive?.content?.toIntOrNull())
                .also { titles[id] = it.title }
        }
    }

    /** 친구가 고른 책의 제목. 한 번 받으면 기억한다. */
    suspend fun title(id: String): String? = titles[id] ?: withContext(Dispatchers.IO) {
        if (!Regex("^OL\\d{1,10}[WM]$").matches(id)) return@withContext null
        val path = if (id.endsWith("W")) "works" else "books"
        get("https://openlibrary.org/$path/$id.json")?.get("title")?.jsonPrimitive?.content?.also { titles[id] = it }
    }
}
