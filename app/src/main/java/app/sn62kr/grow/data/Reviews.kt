package app.sn62kr.grow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** 후기 하나. 자유 글이 없다(D59). */
data class Review(
    val id: String, val activityId: String, val stars: Int, val line: Int,
    val catalogId: String?, val title: String?, val shared: Boolean, val createdAt: Long,
) {
    val kind get() = if (catalogId != null) "catalog" else "preset"
}

class Reviews(private val db: GrowDb, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.reviews()

    val all: Flow<List<Review>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    suspend fun add(activityId: String, stars: Int, line: Int, catalogId: String?, title: String?, shared: Boolean): Review {
        val r = Review(UUID.randomUUID().toString(), activityId, stars.coerceIn(1, 5), line.coerceIn(0, PresetLines.COUNT - 1), catalogId, title, shared, clock())
        dao.insert(r.toEntity())
        return r
    }

    suspend fun delete(id: String) = dao.delete(id)
    suspend fun allOnce() = dao.all().map { it.toModel() }
    suspend fun merge(list: List<Review>): Int = list.count { dao.insert(it.toEntity()) != -1L }
    suspend fun deleteAll() = dao.deleteAll()

    private fun ReviewEntity.toModel() = Review(id, activityId, stars, line, catalogId, title, shared, createdAt)
    private fun Review.toEntity() = ReviewEntity(id, activityId, kind, stars, line, catalogId, title, shared, createdAt)
}

/** 정해진 한 줄의 번호. 문구는 strings.xml의 review_line_N(언어마다). 번호는 바꾸지 않는다 — 친구 폰이 번호로 읽는다. */
object PresetLines {
    const val COUNT = 12 // ReviewSheet의 LINES와 같아야 한다
}
