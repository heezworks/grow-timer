package app.sn62kr.grow.net

import android.content.Context
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.sn62kr.grow.GrowApp
import app.sn62kr.grow.core.draw.Pose
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.data.Records
import app.sn62kr.grow.data.Reviews
import app.sn62kr.grow.data.SettingsStore
import app.sn62kr.grow.timer.Clock
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.concurrent.TimeUnit

/** 친구 한 명이 보여 주는 것: 정해진 값만(서버가 한 번 더 걸렀다). 이것으로 그 사람의 테라스와 격자를 다시 그린다. */
data class FriendView(
    val code: String,
    val daily: Map<LocalDate, Int>,
    val totalSec: Long,
    val gardenSeed: Long,
    val pose: Pose,
    val acts: List<Triple<String, Int, Long>>, // 아이콘 키, 색 번호, 초
    val reviews: List<FriendReview> = emptyList(),
)

/** 친구의 후기 하나: 아이콘 키, 별, 줄 번호, 책 ID. */
data class FriendReview(val icon: String, val stars: Int, val line: Int, val catalog: String?)

data class FriendList(val me: String, val friends: List<FriendView>, val waiting: List<String>)

/** 친구 기능의 앱 쪽(design.md 6절). 켜기 전에는 아무것도 보내지 않는다. */
class Friends(private val api: FriendApi, private val records: Records, private val settings: SettingsStore, private val clock: Clock, private val reviews: Reviews? = null) {
    val available get() = api.enabled

    sealed interface Out<out T> {
        data class Ok<T>(val v: T) : Out<T>
        data object Offline : Out<Nothing>
        data object Paused : Out<Nothing>
        data object NotFound : Out<Nothing>
        data object Self : Out<Nothing>
        data object Full : Out<Nothing>
        data object Failed : Out<Nothing>
    }

    private fun <T> map(r: FriendApi.Res, ok: (JsonObject) -> T): Out<T> = when (r) {
        is FriendApi.Res.Ok -> Out.Ok(ok(r.body))
        FriendApi.Res.Offline -> Out.Offline
        FriendApi.Res.Paused -> Out.Paused
        FriendApi.Res.Limit -> Out.Failed
        is FriendApi.Res.Fail -> when (r.status) { 404 -> Out.NotFound; 400 -> if (r.code == "self") Out.Self else Out.Failed; 409 -> Out.Full; else -> Out.Failed }
    }

    suspend fun key(): String? = settings.friendKey.first()

    /** 켜기: 기기 키를 만들고 등록해 친구 코드를 받는다. 오늘 스냅숏도 바로 올린다. */
    suspend fun enable(): Out<String> {
        val k = key() ?: ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        val out = map(api.call("POST", "/v1/devices", null, buildJsonObject { put("key", k) })) { it["code"]!!.jsonPrimitive.content }
        if (out is Out.Ok) { settings.setFriend(k, out.v); uploadIfDue() }
        return out
    }

    /** 끄기: 서버에서 이 기기와 친구 관계를 지운다. 인터넷이 없어도 이 폰에서는 끈다. */
    suspend fun disable() {
        key()?.let { api.call("DELETE", "/v1/devices/me", it) }
        settings.setFriend(null, null)
    }

    suspend fun list(): Out<FriendList> {
        val k = key() ?: return Out.Failed
        return map(api.call("GET", "/v1/friends", k)) { b ->
            FriendList(
                b["me"]!!.jsonPrimitive.content,
                b["friends"]!!.jsonArray.mapNotNull { f -> parseFriend(f.jsonObject) },
                b["waiting"]!!.jsonArray.map { it.jsonPrimitive.content },
            )
        }
    }

    suspend fun add(code: String): Out<Boolean> =
        map(api.call("POST", "/v1/friends", key(), buildJsonObject { put("code", code.trim().uppercase()) })) { it["status"]?.jsonPrimitive?.content == "friends" }

    suspend fun remove(code: String) = map(api.call("DELETE", "/v1/friends/$code", key())) { }
    suspend fun block(code: String) = map(api.call("POST", "/v1/blocks", key(), buildJsonObject { put("code", code) })) { }
    suspend fun report(code: String, reason: String) = map(api.call("POST", "/v1/reports", key(), buildJsonObject { put("code", code); put("reason", reason) })) { }

    /** 하루 한 번 스냅숏(D59). 서버도 하루 1회만 받는다. */
    suspend fun uploadIfDue(): Boolean {
        val k = key() ?: return false
        val today = clock.today().toString()
        if (settings.lastSnapshotDay.first() == today) return true
        val r = api.call("PUT", "/v1/snapshot", k, snapshot())
        if (r is FriendApi.Res.Ok || r is FriendApi.Res.Limit) { settings.setLastSnapshotDay(today); return true }
        return false
    }

    /**
     * 스냅숏: 최근 371일 날짜별 분, 쌓인 초, 정원 씨앗, 마지막 활동의 자세, 활동별 아이콘·색·초.
     * **활동 이름은 넣지 않는다**(자유 글, D59). 후기는 고르기식이 생기면 넣는다.
     */
    suspend fun snapshot(): JsonObject {
        val today = clock.today()
        val daily = records.daily().first()
        val acts = records.allActivities.first()
        val chrono = records.allChrono.first()
        val perAct = chrono.groupBy { it.activityId }.mapValues { (_, l) -> l.sumOf { it.durationSec.toLong() } }
        val lastAct = chrono.lastOrNull()?.activityId?.let { id -> acts.firstOrNull { it.id == id } }
        return buildJsonObject {
            put("v", 1)
            put("days", buildJsonArray { for (i in 370 downTo 0) add(JsonPrimitive(((daily[today.minusDays(i.toLong())] ?: 0) / 60).coerceIn(0, 1440))) })
            put("lastDay", today.toString())
            put("totalSec", records.totalSec.first().coerceAtMost(1_000_000_000))
            put("gardenSeed", settings.gardenSeed.first().toString())
            put("pose", Pose.of(lastAct?.iconKey).name.lowercase())
            put("acts", buildJsonArray {
                acts.filter { !it.archived }.sortedByDescending { perAct[it.id] ?: 0 }.take(12).forEach { a ->
                    add(buildJsonObject { put("icon", iconFor(a)); put("color", a.colorIndex.coerceIn(0, 11)); put("sec", (perAct[a.id] ?: 0).coerceAtMost(1_000_000_000)) })
                }
            })
            // 고르기식 후기: 별·줄 번호·책 ID만. 친구에게 보이기로 고른 것, 새것부터 20개(D59)
            put("reviews", buildJsonArray {
                reviews?.allOnce()?.filter { it.shared }?.take(20)?.forEach { r ->
                    val a = acts.firstOrNull { it.id == r.activityId }
                    add(buildJsonObject {
                        put("kind", r.kind); put("icon", a?.let(::iconFor) ?: "custom"); put("stars", r.stars); put("line", r.line)
                        r.catalogId?.let { put("catalog", it) }
                    })
                }
            })
        }
    }

    private fun iconFor(a: Activity) = if (a.iconKey in ICONS) a.iconKey else "custom"

    private fun parseFriend(f: JsonObject): FriendView? {
        val code = f["code"]?.jsonPrimitive?.content ?: return null
        val s = f["snapshot"] as? JsonObject ?: return FriendView(code, emptyMap(), 0, 0, Pose.REST, emptyList())
        return runCatching {
            val last = LocalDate.parse(s["lastDay"]!!.jsonPrimitive.content)
            val days = s["days"]!!.jsonArray.map { it.jsonPrimitive.int }
            val daily = days.withIndex().filter { it.value > 0 }.associate { (i, m) -> last.minusDays((days.size - 1 - i).toLong()) to m * 60 }
            FriendView(
                code, daily, s["totalSec"]!!.jsonPrimitive.long, s["gardenSeed"]!!.jsonPrimitive.content.toLong(),
                runCatching { Pose.valueOf(s["pose"]!!.jsonPrimitive.content.uppercase()) }.getOrDefault(Pose.REST),
                s["acts"]?.jsonArray?.map { a -> a.jsonObject.let { Triple(it["icon"]!!.jsonPrimitive.content, it["color"]!!.jsonPrimitive.int, it["sec"]!!.jsonPrimitive.long) } } ?: emptyList(),
                s["reviews"]?.jsonArray?.mapNotNull { r -> r.jsonObject.let { o ->
                    runCatching { FriendReview(o["icon"]!!.jsonPrimitive.content, o["stars"]!!.jsonPrimitive.int, o["line"]!!.jsonPrimitive.int, o["catalog"]?.jsonPrimitive?.content) }.getOrNull()
                } } ?: emptyList(),
            )
        }.getOrNull()
    }

    /** 익명 주간 신호(D63): 지난주(ISO 주)에 시간을 보낸 날 수. 기기 키·코드를 보내지 않는다. 주 1회. */
    suspend fun signalIfDue() {
        if (!settings.signal.first() || !api.enabled) return
        val today = clock.today()
        settings.ensureInstallWeek(isoWeek(today))
        val thisWeek = isoWeek(today)
        if (settings.lastSignalWeek.first() == thisWeek) return
        val lastWeekStart = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY)
        val daily = records.daily().first()
        val active = (0L..6L).count { (daily[lastWeekStart.plusDays(it)] ?: 0) > 0 }
        val r = api.call("POST", "/v1/signal", null, buildJsonObject { put("installWeek", settings.installWeek.first() ?: thisWeek); put("daysActive", active) })
        if (r is FriendApi.Res.Ok) settings.setLastSignalWeek(thisWeek)
    }

    companion object {
        val ICONS = setOf("reading", "instrument", "drawing", "knitting", "writing", "language", "study", "exercise", "cooking", "photo", "gardening", "coding", "custom", "default")
        fun isoWeek(d: LocalDate) = "%d-W%02d".format(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

        /** 하루 두 번쯤 깨어 스냅숏·신호를 확인한다(네트워크가 있을 때만). 보내는 것은 각각 하루·주 1회. */
        fun schedule(ctx: Context) {
            if (!WorkManager.isInitialized()) return // 시험(Robolectric)에서는 App Startup이 돌지 않는다. 실기기는 Application보다 먼저 초기화된다
            val req = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val f = (applicationContext as GrowApp).graph.friends
        f.uploadIfDue()
        f.signalIfDue()
        return Result.success()
    }
}
