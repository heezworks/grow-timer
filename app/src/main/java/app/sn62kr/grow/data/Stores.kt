package app.sn62kr.grow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.sn62kr.grow.core.time.RunningTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 파일이 둘이다. settings는 자동 백업에 들어가고 timer는 빠진다(backup_rules.xml).
val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer")
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
private data class TimerJson(
    val activityId: String, val startWall: Long, val startElapsed: Long, val boot: Int, val startLocalDate: String,
    val pausedTotalMs: Long = 0, val pausedAtWall: Long? = null, val pausedAtElapsed: Long? = null, val targetSec: Int? = null,
) {
    fun toModel() = RunningTimer(activityId, startWall, startElapsed, boot, startLocalDate, pausedTotalMs, pausedAtWall, pausedAtElapsed, targetSec)

    companion object {
        fun of(t: RunningTimer) = TimerJson(t.activityId, t.startWall, t.startElapsed, t.boot, t.startLocalDate,
            t.pausedTotalMs, t.pausedAtWall, t.pausedAtElapsed, t.targetSec)
    }
}

/** 도는 타이머 하나(design.md 2절: 동시에 하나만). */
class TimerStore(private val ds: DataStore<Preferences>) {
    private val key = stringPreferencesKey("running")
    private val json = Json { ignoreUnknownKeys = true }

    val running: Flow<RunningTimer?> = ds.data.map { p -> p[key]?.let { decode(it) } }

    suspend fun get(): RunningTimer? = running.first()

    suspend fun set(t: RunningTimer?) {
        ds.edit { p -> if (t == null) p.remove(key) else p[key] = json.encodeToString(TimerJson.serializer(), TimerJson.of(t)) }
    }

    private fun decode(s: String) = runCatching { json.decodeFromString(TimerJson.serializer(), s).toModel() }.getOrNull()
}

class SettingsStore(private val ds: DataStore<Preferences>) {
    private val onboarded = booleanPreferencesKey("onboarded")
    private val askedNotify = booleanPreferencesKey("askedNotify")

    private val gardenSeedKey = longPreferencesKey("gardenSeed")
    private val weekStartKey = intPreferencesKey("weekStart") // 0 = 기기 로케일, 1~7 = 월~일
    private val notifyKey = booleanPreferencesKey("notify")
    private val lastBackupKey = longPreferencesKey("lastBackupAt")

    val weekStart: Flow<Int> = ds.data.map { it[weekStartKey] ?: 0 }
    /** 알림 켬/끔(조용한 알림과 정한 시간 알림 둘 다). 기본 켬. 시스템 권한과 따로다. */
    val notify: Flow<Boolean> = ds.data.map { it[notifyKey] ?: true }
    val lastBackupAt: Flow<Long> = ds.data.map { it[lastBackupKey] ?: 0L }

    suspend fun setWeekStart(v: Int) = ds.edit { it[weekStartKey] = v }
    suspend fun setNotify(v: Boolean) = ds.edit { it[notifyKey] = v }
    suspend fun setLastBackup(at: Long) = ds.edit { it[lastBackupKey] = at }

    /** 백업 파일에 넣을 설정. */
    suspend fun export(): BackupSettings = ds.data.first().let { p ->
        BackupSettings(p[weekStartKey] ?: 0, p[notifyKey] ?: true, p[gardenSeedKey] ?: 0L)
    }

    /** 백업에서 되살린다. 정원 씨앗은 이 폰에 기록이 없을 때만 바꾼다 — 있으면 지금 화분 모양이 갑자기 바뀐다. */
    suspend fun import(s: BackupSettings, takeGardenSeed: Boolean) = ds.edit { p ->
        p[weekStartKey] = s.weekStart; p[notifyKey] = s.notify
        if (takeGardenSeed && s.gardenSeed != 0L) p[gardenSeedKey] = s.gardenSeed
    }

    private val widgetTipKey = booleanPreferencesKey("widgetTipClosed")
    val widgetTipClosed: Flow<Boolean> = ds.data.map { it[widgetTipKey] ?: false }
    suspend fun closeWidgetTip() = ds.edit { it[widgetTipKey] = true }

    val isOnboarded: Flow<Boolean> = ds.data.map { it[onboarded] ?: false }

    /**
     * 정원 씨앗: 식물 종 순서·모양과 정물 순서를 정한다. 처음 읽을 때 한 번 만든다. 자동 백업에 들어가고(settings 파일),
     * JSON 백업에도 넣는다(M5) — 폰을 바꿔도 같은 정원.
     */
    val gardenSeed: Flow<Long> = ds.data.map { it[gardenSeedKey] ?: 0L }

    suspend fun ensureGardenSeed(): Long {
        var out = 0L
        ds.edit { p -> out = p[gardenSeedKey] ?: java.security.SecureRandom().nextLong().also { p[gardenSeedKey] = it } }
        return out
    }
    val hasAskedNotify: Flow<Boolean> = ds.data.map { it[askedNotify] ?: false }

    suspend fun setOnboarded() = ds.edit { it[onboarded] = true }
    suspend fun setAskedNotify() = ds.edit { it[askedNotify] = true }
}
