package app.sn62kr.grow.data

import androidx.room.withTransaction
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.model.Source
import app.sn62kr.grow.core.time.Days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** 기록의 입구 하나. 화면은 DAO를 직접 보지 않는다. */
class Records(private val db: GrowDb, private val clock: () -> Long = System::currentTimeMillis) {
    private val activities = db.activities()
    private val sessions = db.sessions()

    val allActivities: Flow<List<Activity>> = activities.observeAll().map { l -> l.map { it.toModel() } }

    fun day(date: LocalDate): Flow<List<Session>> = sessions.observeDay(Days.key(date)).map { l -> l.map { it.toModel() } }

    suspend fun hasAnyActivity() = activities.count() > 0

    /** 날짜 → 그날 합(초). 격자와 누적이 이것 하나로 계산된다(design.md 5절: 계산하고 저장하지 않는다). */
    fun daily(activityId: String? = null): Flow<Map<LocalDate, Int>> =
        sessions.observeDailySums(activityId).map { l -> l.associate { Days.parse(it.localDate) to it.sec } }

    val allChrono: Flow<List<Session>> = sessions.observeAllChrono().map { l -> l.map { it.toModel() } }

    /** 모든 기록의 합(초). 식물과 정물이 이것에서 계산된다. */
    val totalSec: Flow<Long> = sessions.observeTotal()

    /** 그해 활동별 합(초), 많은 순. */
    fun activityTotals(year: Int): Flow<List<Pair<String, Long>>> =
        sessions.observeActivitySums("$year-01-01", "$year-12-31").map { l -> l.map { it.activityId to it.sec } }

    suspend fun addActivity(name: String, colorIndex: Int, iconKey: String): Activity {
        val a = Activity(UUID.randomUUID().toString(), name.trim(), colorIndex, iconKey, activities.maxOrder() + 1, false, clock())
        activities.insert(a.toEntity())
        return a
    }

    suspend fun addActivities(list: List<Triple<String, Int, String>>) = db.withTransaction {
        list.forEach { (n, c, i) -> addActivity(n, c, i) }
    }

    suspend fun updateActivity(a: Activity) = activities.update(a.toEntity())

    suspend fun setArchived(a: Activity, archived: Boolean) = activities.update(a.copy(archived = archived).toEntity())

    /** 순서 바꾸기: 받은 순서대로 sortOrder를 다시 매긴다. */
    suspend fun reorder(ordered: List<Activity>) =
        activities.updateAll(ordered.mapIndexed { i, a -> a.copy(sortOrder = i).toEntity() })

    suspend fun recordedSec(activityId: String) = sessions.activitySum(activityId)

    /** 활동과 그 기록을 진짜로 지운다(design.md 2절: 기본은 보관). */
    suspend fun deleteActivityWithRecords(id: String) = db.withTransaction {
        sessions.deleteForActivity(id)
        activities.delete(id)
    }

    suspend fun daySum(date: String, exceptId: String = "") = sessions.daySum(date, exceptId)

    suspend fun saveTimerSession(activityId: String, startUtc: Long, durationSec: Int, localDate: String): Session {
        val now = clock()
        val s = Session(UUID.randomUUID().toString(), activityId, startUtc, durationSec, localDate, Source.TIMER, now, now)
        sessions.insert(s.toEntity())
        return s
    }


    sealed interface AddResult {
        data class Added(val session: Session) : AddResult
        data object OverDay : AddResult
        data object Future : AddResult
    }

    suspend fun addManual(activityId: String, date: LocalDate, durationSec: Int, zone: ZoneId, today: LocalDate): AddResult {
        if (date.isAfter(today)) return AddResult.Future
        val key = Days.key(date)
        if (!Days.fitsInDay(daySum(key), durationSec)) return AddResult.OverDay
        val now = clock()
        val s = Session(UUID.randomUUID().toString(), activityId, Days.manualStart(date, zone), durationSec, key, Source.MANUAL, now, now)
        sessions.insert(s.toEntity())
        return AddResult.Added(s)
    }

    /** 고치기. 하루 합 한도를 넘으면 false. */
    suspend fun updateSession(s: Session): Boolean {
        if (!Days.fitsInDay(daySum(s.localDate, exceptId = s.id), s.durationSec)) return false
        sessions.update(s.copy(updatedAt = clock()).toEntity())
        return true
    }

    suspend fun deleteSession(id: String) = sessions.delete(id)

    suspend fun snapshot(): Pair<List<Activity>, List<Session>> = activities.all().map { it.toModel() } to sessions.all().map { it.toModel() }

    data class MergeResult(val activitiesAdded: Int, val sessionsAdded: Int, val skipped: Int)

    /** 백업 합치기: 같은 id는 한 번만(design.md 1절 Q17). 한 트랜잭션 — 중간에 깨지면 아무것도 안 들어간다. */
    suspend fun merge(acts: List<Activity>, list: List<Session>): MergeResult = db.withTransaction {
        var a = 0; var s = 0; var skip = 0
        for (x in acts) if (activities.insert(x.toEntity()) == -1L) skip++ else a++
        for (x in list) if (sessions.insert(x.toEntity()) == -1L) skip++ else s++
        MergeResult(a, s, skip)
    }

    /** 모든 기록 지우기(설정, 두 번 묻는다). */
    suspend fun deleteAll() = db.withTransaction { sessions.deleteAll(); activities.deleteAll() }

    /** 지우기 되돌리기. 같은 id로 다시 넣는다. */
    suspend fun restoreSession(s: Session) { sessions.insert(s.toEntity()) }
}
