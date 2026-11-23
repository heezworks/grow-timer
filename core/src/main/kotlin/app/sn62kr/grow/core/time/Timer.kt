package app.sn62kr.grow.core.time

/**
 * 지금 시각 세 가지. 벽시계(wall)는 사용자가 바꿀 수 있고, elapsed는 부팅 뒤 흐른 시간이라 시계를 바꿔도
 * 안 흔들리지만 재부팅하면 0부터 다시 센다. boot는 몇 번째 부팅인지(Settings.Global.BOOT_COUNT, 모르면 -1).
 */
data class Now(val wall: Long, val elapsed: Long, val boot: Int)

/**
 * 도는 타이머의 상태(D44 "시작 시각 저장", 포그라운드 서비스 없음). 스파이크 S3에서 열 가지 시험을 통과한 방식이다.
 * 흐른 시간은 같은 부팅이면 elapsed 차, 재부팅을 넘으면 벽시계 차로 센다.
 */
data class RunningTimer(
    val activityId: String,
    /** 시작한 순간의 벽시계. 세션의 startUtc가 된다. */
    val startWall: Long,
    val startElapsed: Long,
    val boot: Int,
    /** 시작한 날(현지, YYYY-MM-DD). 자정을 넘겨도 이 날로 들어간다. */
    val startLocalDate: String,
    /** 끝난 멈춤들의 합. */
    val pausedTotalMs: Long = 0,
    /** 멈춘 순간. 멈춤이 아니면 null. */
    val pausedAtWall: Long? = null,
    val pausedAtElapsed: Long? = null,
    /** 목표 시간(카운트다운). 없으면 카운트업(Q10). */
    val targetSec: Int? = null,
) {
    val isPaused get() = pausedAtElapsed != null
}

data class Reading(
    /** 멈춘 시간을 뺀, 실제로 보낸 시간. */
    val activeMs: Long,
    /** 같은 부팅에서 벽시계가 elapsed와 얼마나 어긋났나(사용자가 시계를 바꾼 만큼). */
    val clockJumpMs: Long,
    val acrossReboot: Boolean,
) {
    /** 시계가 뒤로 갔거나 최대 길이를 넘었다 → 실제로 보낸 시간을 묻는다. */
    val needsCorrection get() = activeMs < 0 || activeMs > Rules.MAX_SESSION_MS
}

object TimerMath {
    fun start(activityId: String, now: Now, localDate: String, targetSec: Int? = null) =
        RunningTimer(activityId, now.wall, now.elapsed, now.boot, localDate, targetSec = targetSec)

    private fun sameBoot(t: RunningTimer, now: Now) = t.boot >= 0 && t.boot == now.boot

    fun read(t: RunningTimer, now: Now): Reading {
        val same = sameBoot(t, now)
        val raw = if (same) now.elapsed - t.startElapsed else now.wall - t.startWall
        val jump = if (same) (now.wall - t.startWall) - raw else 0L
        val pausedNow = when {
            t.pausedAtElapsed == null -> 0L
            same -> now.elapsed - t.pausedAtElapsed
            else -> now.wall - (t.pausedAtWall ?: now.wall)
        }
        return Reading(raw - t.pausedTotalMs - pausedNow, jump, !same)
    }

    fun pause(t: RunningTimer, now: Now): RunningTimer =
        if (t.isPaused) t else t.copy(pausedAtWall = now.wall, pausedAtElapsed = now.elapsed)

    fun resume(t: RunningTimer, now: Now): RunningTimer {
        val atEl = t.pausedAtElapsed ?: return t
        val paused = if (sameBoot(t, now)) now.elapsed - atEl else now.wall - (t.pausedAtWall ?: now.wall)
        return t.copy(pausedTotalMs = t.pausedTotalMs + paused.coerceAtLeast(0), pausedAtWall = null, pausedAtElapsed = null)
    }

    /** 멈춘 지 얼마나 됐나. 멈춤이 아니면 0. */
    fun pausedForMs(t: RunningTimer, now: Now): Long {
        val atEl = t.pausedAtElapsed ?: return 0
        return if (sameBoot(t, now)) now.elapsed - atEl else now.wall - (t.pausedAtWall ?: now.wall)
    }

    /**
     * 재부팅 뒤 첫 신호(BOOT_COMPLETED 또는 앱 열림)에서 elapsed 기준을 새 부팅으로 옮긴다.
     * 재부팅을 넘는 시간은 벽시계밖에 없으므로 기기 시계가 바로잡힌 만큼 틀릴 수 있다(S3 ②).
     */
    fun rebase(t: RunningTimer, now: Now): RunningTimer {
        if (sameBoot(t, now) || now.boot < 0) return t
        val raw = now.wall - t.startWall
        return t.copy(
            startElapsed = now.elapsed - raw,
            boot = now.boot,
            pausedAtElapsed = t.pausedAtWall?.let { now.elapsed - (now.wall - it) },
        )
    }

    /** 목표 알림을 울릴 elapsed 시각. 목표가 없거나 멈춤이면 null. */
    fun targetAtElapsed(t: RunningTimer): Long? {
        val target = t.targetSec ?: return null
        if (t.isPaused) return null
        return t.startElapsed + t.pausedTotalMs + target * 1000L
    }

    /** 끝냈을 때 저장할 초. 최대 길이로 자른다(넘었으면 화면이 먼저 고쳐 달라고 묻는다). */
    fun durationSec(r: Reading): Int = (r.activeMs.coerceIn(0, Rules.MAX_SESSION_MS) / 1000).toInt()
}
