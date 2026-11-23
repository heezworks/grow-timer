package app.sn62kr.grow.core.time

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 스파이크 S3의 시험 표를 순수 계산으로 다시 한다. 기기에서만 되는 것(알람·알림)은 M1 실기기 시험에서. */
class TimerMathTest {
    private val t0 = Now(wall = 1_800_000_000_000, elapsed = 50_000, boot = 7)
    private fun later(ms: Long, wallShift: Long = 0) = Now(t0.wall + ms + wallShift, t0.elapsed + ms, t0.boot)
    private val min = 60_000L

    @Test fun countsElapsed() {
        val t = TimerMath.start("a", t0, "2026-11-22")
        assertEquals(25 * min, TimerMath.read(t, later(25 * min)).activeMs)
    }

    @Test fun clockChangeDoesNotMoveTime() { // S3 ⑥
        val t = TimerMath.start("a", t0, "2026-11-22")
        val r = TimerMath.read(t, later(10 * min, wallShift = 2 * 3600_000))
        assertEquals(10 * min, r.activeMs)
        assertEquals(2 * 3600_000L, r.clockJumpMs)
        assertFalse(r.needsCorrection)
    }

    @Test fun pauseIsSubtracted() {
        var t = TimerMath.start("a", t0, "2026-11-22")
        t = TimerMath.pause(t, later(10 * min))
        assertEquals(10 * min, TimerMath.read(t, later(30 * min)).activeMs) // 멈춘 동안 안 흐른다
        t = TimerMath.resume(t, later(30 * min))
        assertEquals(15 * min, TimerMath.read(t, later(35 * min)).activeMs)
        assertEquals(20 * min, t.pausedTotalMs)
    }

    @Test fun rebootUsesWallThenRebases() { // S3 ②
        val t = TimerMath.start("a", t0, "2026-11-22")
        val afterBoot = Now(t0.wall + 40 * min, elapsed = 30_000, boot = 8)
        val r = TimerMath.read(t, afterBoot)
        assertEquals(40 * min, r.activeMs)
        assertTrue(r.acrossReboot)
        val rb = TimerMath.rebase(t, afterBoot)
        assertEquals(8, rb.boot)
        val r2 = TimerMath.read(rb, Now(afterBoot.wall + 5 * min, afterBoot.elapsed + 5 * min, 8))
        assertEquals(45 * min, r2.activeMs)
        assertFalse(r2.acrossReboot)
    }

    @Test fun pausedAcrossReboot() {
        var t = TimerMath.start("a", t0, "2026-11-22")
        t = TimerMath.pause(t, later(10 * min))
        val afterBoot = Now(t0.wall + 60 * min, elapsed = 20_000, boot = 8)
        t = TimerMath.rebase(t, afterBoot)
        assertEquals(10 * min, TimerMath.read(t, afterBoot).activeMs)
        assertEquals(50 * min, TimerMath.pausedForMs(t, afterBoot))
        t = TimerMath.resume(t, Now(afterBoot.wall + min, afterBoot.elapsed + min, 8))
        assertEquals(10 * min, TimerMath.read(t, Now(afterBoot.wall + min, afterBoot.elapsed + min, 8)).activeMs)
    }

    @Test fun clockBackwardsAcrossRebootAsksForCorrection() {
        val t = TimerMath.start("a", t0, "2026-11-22")
        val r = TimerMath.read(t, Now(t0.wall - 5 * min, 10_000, 8))
        assertTrue(r.needsCorrection)
    }

    @Test fun overMaxAsksAndIsClamped() {
        val t = TimerMath.start("a", t0, "2026-11-22")
        val r = TimerMath.read(t, later(9 * 3600_000L))
        assertTrue(r.needsCorrection)
        assertEquals(8 * 3600, TimerMath.durationSec(r))
    }

    @Test fun targetAlarm() {
        var t = TimerMath.start("a", t0, "2026-11-22", targetSec = 1500)
        assertEquals(t0.elapsed + 1_500_000, TimerMath.targetAtElapsed(t))
        t = TimerMath.pause(t, later(min))
        assertNull(TimerMath.targetAtElapsed(t))
        t = TimerMath.resume(t, later(3 * min))
        assertEquals(t0.elapsed + 1_500_000 + 2 * min, TimerMath.targetAtElapsed(t))
    }

    @Test fun startDayIsKeptAcrossMidnight() { // S3 ⑤, ⑦
        val seoul = ZoneId.of("Asia/Seoul")
        val start = LocalDate.of(2026, 11, 21).atTime(23, 59, 46).atZone(seoul).toInstant().toEpochMilli()
        val day = Days.localDate(start, seoul)
        val t = TimerMath.start("a", Now(start, 0, 1), Days.key(day))
        assertEquals("2026-11-21", t.startLocalDate)
        assertEquals(LocalDate.of(2026, 11, 21), Days.localDate(start, ZoneId.of("Asia/Seoul")))
    }

    @Test fun dayLimit() {
        assertTrue(Days.fitsInDay(20 * 3600, 4 * 3600))
        assertFalse(Days.fitsInDay(20 * 3600, 4 * 3600 + 1))
    }
}
