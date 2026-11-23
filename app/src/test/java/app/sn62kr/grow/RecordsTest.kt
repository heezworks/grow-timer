package app.sn62kr.grow

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sn62kr.grow.data.Records
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** design.md 2절의 규칙(하루 24시간, 미래 날짜 금지, 지우기 = 보관이 기본). */
@RunWith(AndroidJUnit4::class)
class RecordsTest {
    private val g = testGraph(ApplicationProvider.getApplicationContext())
    private val r = g.records
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 22)

    @Test fun manualAddUsesThatDay() = runBlocking {
        val a = r.addActivity("기타", 0, "instrument")
        val res = r.addManual(a.id, today.minusDays(1), 1800, zone, today)
        assertTrue(res is Records.AddResult.Added)
        val s = r.day(today.minusDays(1)).first().single()
        assertEquals("2026-11-21", s.localDate)
        assertEquals(1800, s.durationSec)
    }

    @Test fun dayLimitAndFuture() = runBlocking {
        val a = r.addActivity("기타", 0, "instrument")
        assertTrue(r.addManual(a.id, today, 20 * 3600, zone, today) is Records.AddResult.Added)
        assertEquals(Records.AddResult.OverDay, r.addManual(a.id, today, 4 * 3600 + 60, zone, today))
        assertTrue(r.addManual(a.id, today, 4 * 3600, zone, today) is Records.AddResult.Added)
        assertEquals(Records.AddResult.Future, r.addManual(a.id, today.plusDays(1), 60, zone, today))
    }

    @Test fun editRespectsDayLimit() = runBlocking {
        val a = r.addActivity("기타", 0, "instrument")
        r.addManual(a.id, today, 10 * 3600, zone, today)
        r.addManual(a.id, today, 3600, zone, today)
        val small = r.day(today).first().first { it.durationSec == 3600 }
        assertFalse(r.updateSession(small.copy(durationSec = 15 * 3600)))
        assertTrue(r.updateSession(small.copy(durationSec = 14 * 3600)))
    }

    @Test fun hideKeepsRecordsDeleteRemovesThem() = runBlocking {
        val a = r.addActivity("기타", 0, "instrument")
        r.addManual(a.id, today, 3600, zone, today)
        r.setArchived(a, true)
        assertEquals(3600L, r.recordedSec(a.id))
        assertTrue(r.allActivities.first().single().archived)
        r.deleteActivityWithRecords(a.id)
        assertEquals(0L, r.recordedSec(a.id))
        assertTrue(r.allActivities.first().isEmpty())
    }

    @Test fun addManyInOneTransaction() = runBlocking {
        r.addActivities(listOf(Triple("A", 0, "x"), Triple("B", 1, "x")))
        assertEquals(listOf("A", "B"), r.allActivities.first().map { it.name })
    }

    /** 3년 치(약 2,000건): 날짜별 합과 활동별 합이 넣은 값과 같다. 화면 없이(YearTest 머리말). */
    @Test fun threeYearsOfDailySums() = runBlocking {
        val acts = listOf(r.addActivity("A", 0, "x"), r.addActivity("B", 1, "x"))
        val rnd = kotlin.random.Random(2026)
        val want = HashMap<LocalDate, Int>(); var bSec2026 = 0L
        var d = LocalDate.of(2023, 11, 24)
        while (!d.isAfter(today)) {
            repeat(rnd.nextInt(4)) {
                val a = acts[rnd.nextInt(2)]; val sec = 300 + rnd.nextInt(3000)
                r.addManual(a.id, d, sec, zone, today)
                want[d] = (want[d] ?: 0) + sec
                if (a.name == "B" && d.year == 2026) bSec2026 += sec
            }
            d = d.plusDays(1)
        }
        assertEquals(want, r.daily().first())
        assertEquals(bSec2026, r.activityTotals(2026).first().first { it.first == acts[1].id }.second)
    }

    @Test fun reorder() = runBlocking {
        val a = r.addActivity("A", 0, "x"); val b = r.addActivity("B", 1, "x"); val c = r.addActivity("C", 2, "x")
        r.reorder(listOf(c, a, b))
        assertEquals(listOf("C", "A", "B"), r.allActivities.first().map { it.name })
    }

    @Test fun undoDeleteRestoresSameSession() = runBlocking {
        val a = r.addActivity("기타", 0, "instrument")
        r.addManual(a.id, today, 3600, zone, today)
        val s = r.day(today).first().single()
        r.deleteSession(s.id)
        r.restoreSession(s)
        assertEquals(listOf(s), r.day(today).first())
    }
}
