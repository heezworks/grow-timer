package app.sn62kr.grow.core.model

/** 활동(취미 하나). 색은 팔레트 번호, 아이콘은 정해진 목록의 키(design.md 5절). */
data class Activity(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val iconKey: String,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Long,
)

enum class Source(val key: String) {
    TIMER("timer"), MANUAL("manual");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

/**
 * 한 번 보낸 시간. [localDate]는 시작한 날의 현지 날짜(YYYY-MM-DD)를 박아 둔 것이다 —
 * 시간대를 옮겨도 과거 칸이 움직이지 않는다(design.md 2절, T6).
 */
data class Session(
    val id: String,
    val activityId: String,
    val startUtc: Long,
    val durationSec: Int,
    val localDate: String,
    val source: Source,
    val createdAt: Long,
    val updatedAt: Long,
)
