package app.sn62kr.grow.data

import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.model.Source
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 백업 파일(JSON 한 개, design.md 5절). S4에서 3,000건 어림 약 710KB.
 * format 글자는 앱 이름이 정해지면 바꾼다 — 그 전 파일도 읽을 수 있게 옛 이름을 [Backup.FORMATS]에 남긴다.
 * 익명 키·친구는 M4b에서 넣는다(필드는 지금 없다. 모르는 필드는 읽을 때 무시한다).
 */
@Serializable
data class BackupSettings(
    val weekStart: Int = 0, val notify: Boolean = true, val gardenSeed: Long = 0L,
    /** 친구 기능의 기기 키·코드(켰을 때만). 폰을 바꿔도 친구가 이어진다. 옛 폰의 친구 기능은 같은 키라 함께 움직인다. */
    val friendKey: String? = null, val friendCode: String? = null,
)

@Serializable
data class BackupActivity(
    val id: String, val name: String, val colorIndex: Int, val iconKey: String,
    val sortOrder: Int, val archived: Boolean, val createdAt: Long,
)

@Serializable
data class BackupSession(
    val id: String, val activityId: String, val startUtc: Long, val durationSec: Int,
    val localDate: String, val source: String, val createdAt: Long, val updatedAt: Long,
)

@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val activities: List<BackupActivity>,
    val sessions: List<BackupSession>,
    val settings: BackupSettings = BackupSettings(),
)

object Backup {
    const val FORMAT = "grow-timer-backup"
    val FORMATS = setOf(FORMAT)
    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(acts: List<Activity>, sessions: List<Session>, settings: BackupSettings, now: Long): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            FORMAT, VERSION, now,
            acts.map { BackupActivity(it.id, it.name, it.colorIndex, it.iconKey, it.sortOrder, it.archived, it.createdAt) },
            sessions.map { BackupSession(it.id, it.activityId, it.startUtc, it.durationSec, it.localDate, it.source.key, it.createdAt, it.updatedAt) },
            settings,
        ),
    )

    sealed interface Read {
        data class Ok(val activities: List<Activity>, val sessions: List<Session>, val settings: BackupSettings) : Read
        /** "「이름」 백업 파일이 아니에요" */
        data object NotOurs : Read
        /** "더 새 버전에서 만든 백업이에요" */
        data object Newer : Read
        /** 읽을 수 없는 파일(잘렸거나 깨졌다) */
        data object Broken : Read
    }

    fun decode(text: String): Read {
        val f = try { json.decodeFromString(BackupFile.serializer(), text) } catch (e: Exception) {
            // format이 다른 JSON이면 "우리 파일이 아님", 아예 못 읽으면 "깨짐"
            return runCatching { json.parseToJsonElement(text) }.fold({ Read.NotOurs }, { Read.Broken })
        }
        if (f.format !in FORMATS) return Read.NotOurs
        if (f.version > VERSION) return Read.Newer
        val valid = f.sessions.filter { it.durationSec in 1..24 * 3600 && it.localDate.length == 10 }
        return Read.Ok(
            f.activities.map { Activity(it.id, it.name, it.colorIndex, it.iconKey, it.sortOrder, it.archived, it.createdAt) },
            valid.map { Session(it.id, it.activityId, it.startUtc, it.durationSec, it.localDate, Source.of(it.source), it.createdAt, it.updatedAt) },
            f.settings,
        )
    }
}
