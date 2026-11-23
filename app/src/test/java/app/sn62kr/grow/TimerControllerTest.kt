package app.sn62kr.grow

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sn62kr.grow.timer.TimerController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalDateTime

/** S3 시험 표 가운데 기기 없이 되는 것을 제품 코드로 다시 한다. */
@RunWith(AndroidJUnit4::class)
class TimerControllerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val g = testGraph(ctx, clock)
    private val t = g.timer
    private val min = 60_000L

    @Test fun onlyOneTimer() = runBlocking { // S3 ⑨
        val a = g.records.addActivity("기타", 0, "x")
        assertEquals(TimerController.StartResult.Started, t.start(a.id, a.name, null))
        assertEquals(TimerController.StartResult.AlreadyRunning, t.start(a.id, a.name, null))
    }

    @Test fun finishSavesStartDayAcrossMidnight() = runBlocking { // S3 ⑤
        clock.wall = LocalDateTime.of(2026, 11, 21, 23, 50).atZone(clock.zoneId).toInstant().toEpochMilli()
        val a = g.records.addActivity("기타", 0, "x")
        t.start(a.id, a.name, null)
        clock.advance(40 * min)
        val sec = (t.read(t.running.first()!!).activeMs / 1000).toInt()
        val s = t.finish(sec)!!
        assertEquals("2026-11-21", s.localDate)
        assertEquals(40 * 60, s.durationSec)
        assertNull(t.running.first())
        assertEquals(1, g.records.day(LocalDate.of(2026, 11, 21)).first().size)
    }

    @Test fun clockChangeAndPause() = runBlocking { // S3 ⑥
        val a = g.records.addActivity("기타", 0, "x")
        t.start(a.id, a.name, null)
        clock.advance(10 * min)
        clock.wall += 2 * 3600_000 // 사용자가 시계를 2시간 앞으로
        t.pause(a.name)
        clock.advance(30 * min)
        t.resume(a.name)
        clock.advance(5 * min)
        assertEquals(15 * min, t.read(t.running.first()!!).activeMs)
    }

    @Test fun rebootRebasesAndReschedules() = runBlocking { // S3 ②
        val a = g.records.addActivity("기타", 0, "x")
        t.start(a.id, a.name, 1500)
        clock.advance(5 * min)
        clock.reboot(offMs = 2 * min)
        g.rebaseTimer()
        val run = t.running.first()!!
        assertEquals(clock.boot, run.boot)
        assertEquals(7 * min, t.read(run).activeMs)
        assertNotNull(shadowOf(ctx.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm())
    }

    @Test fun targetAlarmCancelledOnFinish() = runBlocking {
        val a = g.records.addActivity("기타", 0, "x")
        t.start(a.id, a.name, 1500)
        val am = shadowOf(ctx.getSystemService(AlarmManager::class.java))
        assertNotNull(am.peekNextScheduledAlarm())
        t.finish(60)
        assertNull(am.peekNextScheduledAlarm())
    }
}
