package app.sn62kr.spike.timer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "시작 시각 저장" 타이머(D44). 포그라운드 서비스 없음.
 * 흐른 시간은 같은 부팅 안에서는 elapsedRealtime(시계를 바꿔도 안 흔들림)으로,
 * 재부팅을 넘으면 벽시계로 센다. 재부팅 뒤 첫 신호에서 elapsed 기준을 다시 잡는다.
 */
data class TimerState(
    val running: Boolean = false,
    val activity: String = "",
    val startWall: Long = 0, val startElapsed: Long = 0, val boot: Int = -1,
    val startLocalDate: String = "",       // 시작한 날(현지)을 박아 둔다(T6)
    val pausedTotal: Long = 0,
    val pausedAtWall: Long = -1, val pausedAtElapsed: Long = -1,
    val targetSec: Int = 0,
)

data class Reading(val activeMs: Long, val source: String, val clockJumpMs: Long, val needsCorrection: Boolean)

object TimerMath {
    const val MAX_MS = 8L * 3600 * 1000 // 한 세션 최대 8시간(design.md 2절)

    fun read(s: TimerState, nowWall: Long, nowElapsed: Long, nowBoot: Int): Reading {
        val sameBoot = s.boot == nowBoot && nowBoot >= 0
        val raw: Long; val source: String; val jump: Long
        if (sameBoot) {
            raw = nowElapsed - s.startElapsed
            source = "elapsed"
            jump = (nowWall - s.startWall) - raw
        } else {
            raw = nowWall - s.startWall
            source = "wall(재부팅 뒤)"
            jump = 0
        }
        val pausedNow = when {
            s.pausedAtElapsed < 0 -> 0L
            sameBoot -> nowElapsed - s.pausedAtElapsed
            else -> nowWall - s.pausedAtWall
        }
        val active = raw - s.pausedTotal - pausedNow
        return Reading(active, source, jump, active < 0 || active > MAX_MS)
    }
}

object Timer {
    private const val P = "s3"
    const val CH_RUN = "running"; const val CH_DONE = "done"

    fun boot(ctx: Context): Int = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun load(ctx: Context): TimerState {
        val p = ctx.getSharedPreferences(P, 0)
        return TimerState(
            p.getBoolean("running", false), p.getString("activity", "") ?: "",
            p.getLong("startWall", 0), p.getLong("startElapsed", 0), p.getInt("boot", -1),
            p.getString("startLocalDate", "") ?: "", p.getLong("pausedTotal", 0),
            p.getLong("pausedAtWall", -1), p.getLong("pausedAtElapsed", -1), p.getInt("targetSec", 0),
        )
    }

    private fun save(ctx: Context, s: TimerState) {
        ctx.getSharedPreferences(P, 0).edit()
            .putBoolean("running", s.running).putString("activity", s.activity)
            .putLong("startWall", s.startWall).putLong("startElapsed", s.startElapsed).putInt("boot", s.boot)
            .putString("startLocalDate", s.startLocalDate).putLong("pausedTotal", s.pausedTotal)
            .putLong("pausedAtWall", s.pausedAtWall).putLong("pausedAtElapsed", s.pausedAtElapsed)
            .putInt("targetSec", s.targetSec).commit()
    }

    fun log(ctx: Context, msg: String) {
        Log.i("S3", msg)
        val p = ctx.getSharedPreferences(P, 0)
        val old = p.getString("log", "") ?: ""
        p.edit().putString("log", (msg + "\n" + old).take(4000)).commit()
    }

    fun read(ctx: Context) = TimerMath.read(load(ctx), System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(ctx))

    fun start(ctx: Context, activity: String, targetSec: Int) {
        val old = load(ctx)
        if (old.running) { log(ctx, "START 거절: 이미 도는 타이머가 있다(${old.activity}). 끝내고 시작해야 한다"); return }
        val now = System.currentTimeMillis()
        val s = TimerState(true, activity, now, SystemClock.elapsedRealtime(), boot(ctx),
            LocalDate.now().toString(), 0, -1, -1, targetSec)
        save(ctx, s)
        scheduleTarget(ctx, s)
        showRunning(ctx, s)
        log(ctx, "START $activity target=${targetSec}s wall=$now elapsed=${s.startElapsed} boot=${s.boot} date=${s.startLocalDate}")
    }

    fun pause(ctx: Context) {
        val s = load(ctx); if (!s.running || s.pausedAtElapsed >= 0) return
        save(ctx, s.copy(pausedAtWall = System.currentTimeMillis(), pausedAtElapsed = SystemClock.elapsedRealtime()))
        cancelTarget(ctx); log(ctx, "PAUSE active=${read(ctx).activeMs}")
    }

    fun resume(ctx: Context) {
        val s = load(ctx); if (!s.running || s.pausedAtElapsed < 0) return
        val sameBoot = s.boot == boot(ctx)
        val paused = if (sameBoot) SystemClock.elapsedRealtime() - s.pausedAtElapsed else System.currentTimeMillis() - s.pausedAtWall
        val n = s.copy(pausedTotal = s.pausedTotal + paused, pausedAtWall = -1, pausedAtElapsed = -1)
        save(ctx, n); scheduleTarget(ctx, n); log(ctx, "RESUME paused=${paused}ms")
    }

    fun finish(ctx: Context) {
        val s = load(ctx); if (!s.running) { log(ctx, "FINISH: 도는 타이머 없음"); return }
        val r = read(ctx)
        val startUtc = Instant.ofEpochMilli(System.currentTimeMillis() - r.activeMs - s.pausedTotal)
        log(ctx, "FINISH ${s.activity} active=${r.activeMs}ms source=${r.source} clockJump=${r.clockJumpMs}ms " +
            "date=${s.startLocalDate} startUtc=$startUtc needsCorrection=${r.needsCorrection}")
        save(ctx, TimerState())
        cancelTarget(ctx)
        ctx.getSystemService(NotificationManager::class.java).cancel(1)
    }

    /** 재부팅 뒤: 벽시계로 흐른 시간을 구해 elapsed 기준을 새 부팅으로 옮긴다. 알람도 다시 건다. */
    fun rebase(ctx: Context, why: String) {
        val s = load(ctx); if (!s.running) return
        val nowB = boot(ctx)
        if (s.boot == nowB) { log(ctx, "$why: 같은 부팅, 그대로(active=${read(ctx).activeMs})"); scheduleTarget(ctx, s); return }
        val nowWall = System.currentTimeMillis(); val nowEl = SystemClock.elapsedRealtime()
        val raw = nowWall - s.startWall
        val n = s.copy(startElapsed = nowEl - raw, boot = nowB,
            pausedAtElapsed = if (s.pausedAtElapsed >= 0) nowEl - (nowWall - s.pausedAtWall) else -1)
        save(ctx, n); scheduleTarget(ctx, n); showRunning(ctx, n)
        log(ctx, "$why: 재부팅 넘김 → 새 기준. active=${read(ctx).activeMs}")
    }

    private fun alarmPi(ctx: Context) = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun scheduleTarget(ctx: Context, s: TimerState) {
        if (s.targetSec <= 0 || s.pausedAtElapsed >= 0) return
        val at = s.startElapsed + s.pausedTotal + s.targetSec * 1000L
        ctx.getSharedPreferences(P, 0).edit().putLong("alarmAtElapsed", at).commit()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmPi(ctx))
        else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmPi(ctx))
        log(ctx, "ALARM 예약: ${(at - SystemClock.elapsedRealtime()) / 1000}s 뒤, exact=$exact")
    }

    private fun cancelTarget(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(alarmPi(ctx))

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, "도는 중", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "목표 시간", NotificationManager.IMPORTANCE_HIGH))
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun showRunning(ctx: Context, s: TimerState) {
        if (!canNotify(ctx)) { log(ctx, "알림 권한 없음: 조용한 알림 없이 그대로 돈다"); return }
        channels(ctx)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(ctx, CH_RUN)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("${s.activity}와 시간 보내는 중")
            .setUsesChronometer(true).setWhen(s.startWall + s.pausedTotal)
            .setOngoing(true).setContentIntent(open).build()
        ctx.getSystemService(NotificationManager::class.java).notify(1, n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val at = ctx.getSharedPreferences("s3", 0).getLong("alarmAtElapsed", 0)
        val late = SystemClock.elapsedRealtime() - at
        Timer.log(ctx, "ALARM 울림: 예정보다 ${late}ms 늦음, active=${Timer.read(ctx).activeMs}")
        if (Timer.canNotify(ctx)) {
            Timer.channels(ctx)
            val n = android.app.Notification.Builder(ctx, Timer.CH_DONE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("정한 시간이 됐어요").setContentText("천천히 마무리해도 돼요.")
                .setAutoCancel(true).build()
            ctx.getSystemService(NotificationManager::class.java).notify(2, n)
        }
    }
}

class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Timer.rebase(ctx, intent.action ?: "?")
}

class CmdReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getStringExtra("cmd")) {
            "start" -> Timer.start(ctx, intent.getStringExtra("activity") ?: "기타", intent.getIntExtra("target", 0))
            "pause" -> Timer.pause(ctx)
            "resume" -> Timer.resume(ctx)
            "finish" -> Timer.finish(ctx)
            "status" -> { val r = Timer.read(ctx); Timer.log(ctx, "STATUS running=${Timer.load(ctx).running} active=${r.activeMs} source=${r.source} jump=${r.clockJumpMs} needsCorrection=${r.needsCorrection} now=${System.currentTimeMillis()} zone=${ZoneId.systemDefault()}") }
        }
    }
}
