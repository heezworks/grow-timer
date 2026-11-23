package app.sn62kr.grow.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.model.Source
import kotlinx.coroutines.flow.Flow

// design.md 5절. id는 UUID 글자 — 백업 합치기가 INSERT … IGNORE 한 줄로 된다(S4).
// 출시 전까지는 스키마 1을 그대로 고친다. 첫 출시 뒤부터 버전을 올리고 이전 규칙을 쓴다.

@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorIndex: Int,
    val iconKey: String,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Long,
) {
    fun toModel() = Activity(id, name, colorIndex, iconKey, sortOrder, archived, createdAt)
}

fun Activity.toEntity() = ActivityEntity(id, name, colorIndex, iconKey, sortOrder, archived, createdAt)

@Entity(tableName = "session", indices = [Index("localDate"), Index("activityId")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    val startUtc: Long,
    val durationSec: Int,
    val localDate: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toModel() = Session(id, activityId, startUtc, durationSec, localDate, Source.of(source), createdAt, updatedAt)
}

fun Session.toEntity() = SessionEntity(id, activityId, startUtc, durationSec, localDate, source.key, createdAt, updatedAt)

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY archived, sortOrder, createdAt")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT COUNT(*) FROM activity")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM activity")
    suspend fun maxOrder(): Int

    @Query("SELECT * FROM activity WHERE id = :id")
    suspend fun get(id: String): ActivityEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(a: ActivityEntity)

    @Update
    suspend fun update(a: ActivityEntity)

    @Update
    suspend fun updateAll(a: List<ActivityEntity>)

    @Query("DELETE FROM activity WHERE id = :id")
    suspend fun delete(id: String)
}

/** 날짜별 합(초). */
data class DaySum(val localDate: String, val sec: Int)

/** 활동별 합(초). */
data class ActivitySum(val activityId: String, val sec: Long)

@Dao
interface SessionDao {
    /** 모든 날의 합. [activityId]가 null이면 전체. 3년 치라도 1,100줄 남짓이다. */
    @Query("SELECT localDate, SUM(durationSec) AS sec FROM session WHERE (:activityId IS NULL OR activityId = :activityId) GROUP BY localDate")
    fun observeDailySums(activityId: String?): Flow<List<DaySum>>

    @Query("SELECT COALESCE(SUM(durationSec), 0) FROM session")
    fun observeTotal(): Flow<Long>

    /** 기간 안의 활동별 합. 날짜는 YYYY-MM-DD 글자라 글자 비교가 곧 날짜 비교다. */
    @Query("SELECT activityId, SUM(durationSec) AS sec FROM session WHERE localDate BETWEEN :from AND :to GROUP BY activityId ORDER BY sec DESC")
    fun observeActivitySums(from: String, to: String): Flow<List<ActivitySum>>

    @Query("SELECT * FROM session WHERE localDate = :date ORDER BY startUtc DESC")
    fun observeDay(date: String): Flow<List<SessionEntity>>

    @Query("SELECT COALESCE(SUM(durationSec), 0) FROM session WHERE localDate = :date AND id != :exceptId")
    suspend fun daySum(date: String, exceptId: String = ""): Int

    @Query("SELECT COALESCE(SUM(durationSec), 0) FROM session WHERE activityId = :activityId")
    suspend fun activitySum(activityId: String): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(s: SessionEntity)

    @Update
    suspend fun update(s: SessionEntity)

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM session WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: String)
}

@Database(entities = [ActivityEntity::class, SessionEntity::class], version = 1, exportSchema = true)
abstract class GrowDb : RoomDatabase() {
    abstract fun activities(): ActivityDao
    abstract fun sessions(): SessionDao
}
