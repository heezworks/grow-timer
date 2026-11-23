package app.sn62kr.grow

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sn62kr.grow.data.Records
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

    @Test fun manualAddUsesThatDay() = runTest {
        val a = r.addActivity("기타", 0, "instrument")
        val res = r.addManual(a.id, today.minusDays(1), 1800, zone, today)
        assertTrue(res is Records.AddResult.Added)
        val s = r.day(today.minusDays(1)).first().single()
        assertEquals("2026-11-21", s.localDate)
        assertEquals(1800, s.durationSec)
    }

    @Test fun dayLimitAndFuture() = runTest {
        val a = r.addActivity("기타", 0, "instrument")
        assertTrue(r.addManual(a.id, today, 20 * 3600, zone, today) is Records.AddResult.Added)
        assertEquals(Records.AddResult.OverDay, r.addManual(a.id, today, 4 * 3600 + 60, zone, today))
        assertTrue(r.addManual(a.id, today, 4 * 3600, zone, today) is Records.AddResult.Added)
        assertEquals(Records.AddResult.Future, r.addManual(a.id, today.plusDays(1), 60, zone, today))
    }

    @Test fun editRespectsDayLimit() = runTest {
        val a = r.addActivity("기타", 0, "instrument")
        r.addManual(a.id, today, 10 * 3600, zone, today)
        r.addManual(a.id, today, 3600, zone, today)
        val small = r.day(today).first().first { it.durationSec == 3600 }
        assertFalse(r.updateSession(small.copy(durationSec = 15 * 3600)))
        assertTrue(r.updateSession(small.copy(durationSec = 14 * 3600)))
    }

    @Test fun hideKeepsRecordsDeleteRemovesThem() = runTest {
        val a = r.addActivity("기타", 0, "instrument")
        r.addManual(a.id, today, 3600, zone, today)
        r.setArchived(a, true)
        assertEquals(3600L, r.recordedSec(a.id))
        assertTrue(r.allActivities.first().single().archived)
        r.deleteActivityWithRecords(a.id)
        assertEquals(0L, r.recordedSec(a.id))
        assertTrue(r.allActivities.first().isEmpty())
    }

    @Test fun addManyInOneTransaction() = runTest {
        r.addActivities(listOf(Triple("A", 0, "x"), Triple("B", 1, "x")))
        assertEquals(listOf("A", "B"), r.allActivities.first().map { it.name })
    }

    @Test fun reorder() = runTest {
        val a = r.addActivity("A", 0, "x"); val b = r.addActivity("B", 1, "x"); val c = r.addActivity("C", 2, "x")
        r.reorder(listOf(c, a, b))
        assertEquals(listOf("C", "A", "B"), r.allActivities.first().map { it.name })
    }

    @Test fun undoDeleteRestoresSameSession() = runTest {
        val a = r.addActivity("기타", 0, "instrument")
        r.addManual(a.id, today, 3600, zone, today)
        val s = r.day(today).first().single()
        r.deleteSession(s.id)
        r.restoreSession(s)
        assertEquals(listOf(s), r.day(today).first())
    }
}
