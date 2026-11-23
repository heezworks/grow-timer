package app.sn62kr.grow.core.stats

import app.sn62kr.grow.core.draw.GridArt
import app.sn62kr.grow.core.draw.GridPalette
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsTest {
    private val today = LocalDate.of(2026, 11, 23) // 월요일

    @Test fun levels() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4), listOf(0, 60, 14 * 60 + 59, 15 * 60, 44 * 60, 45 * 60, 119 * 60, 120 * 60).map(Level::of))
    }

    @Test fun gridShapeSunday() {
        // 2026-01-01은 목요일. 일요일 시작이면 첫 열의 일·월·화·수는 그해 밖.
        val g = YearGrid.build(2026, DayOfWeek.SUNDAY, emptyMap(), today)
        assertEquals(53, g.cols)
        assertNull(g.at(0, 0)); assertNull(g.at(0, 3))
        assertEquals(LocalDate.of(2026, 1, 1), g.at(0, 4)!!.date)
        assertEquals(365, g.cells.count { it != null })
        assertEquals(LocalDate.of(2026, 12, 31), g.cells.last { it != null }!!.date)
    }

    @Test fun gridShapeMondayAndLeapYear() {
        val g = YearGrid.build(2028, DayOfWeek.MONDAY, emptyMap(), today) // 2028은 윤년, 1월 1일 토요일
        assertEquals(366, g.cells.count { it != null })
        assertEquals(LocalDate.of(2028, 1, 1), g.at(0, 5)!!.date)
        assertTrue(g.cols in 53..54)
    }

    @Test fun todayFutureAndSums() {
        val daily = mapOf(today to 1800, today.minusDays(1) to 3600, LocalDate.of(2025, 12, 31) to 600)
        val g = YearGrid.build(2026, DayOfWeek.MONDAY, daily, today)
        val c = g.cells.first { it?.date == today }!!
        assertTrue(c.isToday); assertEquals(2, c.level)
        assertTrue(g.cells.first { it?.date == today.plusDays(1) }!!.isFuture)
        assertFalse(c.isFuture)
        assertEquals(5400L, g.totalSec)
        assertEquals(2, g.filledDays)
        assertEquals(g.colOf(today) - 1, g.colOf(today.minusDays(1))) // 월요일 시작: 어제(일)는 앞 주
    }

    @Test fun totals() {
        val daily = mapOf(today to 600, today.minusDays(1) to 1200, today.minusDays(8) to 1800, LocalDate.of(2025, 5, 1) to 3000)
        assertEquals(Totals(600, 600, 3600, 6600), Sums.totals(daily, today, DayOfWeek.MONDAY))
        assertEquals(Totals(600, 1800, 3600, 6600), Sums.totals(daily, today, DayOfWeek.SUNDAY))
    }

    @Test fun drawingIsDeterministicAndHits() {
        val daily = (0 until 300).associate { LocalDate.of(2026, 1, 1).plusDays(it.toLong()) to (it * 37 % 200) * 60 }
        val a = GridArt.build(YearGrid.build(2026, DayOfWeek.SUNDAY, daily, today), GridPalette.LIGHT)
        val b = GridArt.build(YearGrid.build(2026, DayOfWeek.SUNDAY, daily, today), GridPalette.LIGHT)
        assertEquals(a.fingerprint(), b.fingerprint())
        assertEquals(366, a.ops.size) // 365칸 + 오늘 테두리
        val g = YearGrid.build(2026, DayOfWeek.SUNDAY, daily, today)
        assertEquals(0 to 4, GridArt.hit(g, 1f, 4 * GridArt.STEP + 1f))
        assertNull(GridArt.hit(g, 1f, 1f)) // 그해 밖 칸
    }
}
