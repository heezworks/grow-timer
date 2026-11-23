package app.sn62kr.grow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    val isOnboarded: Flow<Boolean> = ds.data.map { it[onboarded] ?: false }
    val hasAskedNotify: Flow<Boolean> = ds.data.map { it[askedNotify] ?: false }

    suspend fun setOnboarded() = ds.edit { it[onboarded] = true }
    suspend fun setAskedNotify() = ds.edit { it[askedNotify] = true }
}
