package app.sn62kr.grow.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 격자 진하기 5단계(design.md 2절): 0 / 15분 미만 / 15~45분 / 45분~2시간 / 2시간 이상. */
object Level {
    const val COUNT = 5

    fun of(sec: Int): Int {
        val min = sec / 60
        return when {
            sec <= 0 -> 0
            min < 15 -> 1
            min < 45 -> 2
            min < 120 -> 3
            else -> 4
        }
    }
}

/** 격자의 칸 하나. 그해 밖의 칸은 만들지 않는다(null). */
data class Cell(val date: LocalDate, val sec: Int, val level: Int, val isToday: Boolean, val isFuture: Boolean)

/**
 * 한 해의 격자: 열 = 주, 행 = 요일(주의 첫날부터). GitHub 잔디와 같은 배치.
 * [cells]는 열 우선으로 cols × 7개, 그해 밖은 null.
 */
class YearGrid(val year: Int, val weekStart: DayOfWeek, val cols: Int, val cells: List<Cell?>) {
    fun at(col: Int, row: Int): Cell? = if (col in 0 until cols && row in 0..6) cells[col * 7 + row] else null

    /** 그 날짜가 놓인 열. 처음 열 때 오늘이 보이게 스크롤하는 데 쓴다. */
    fun colOf(date: LocalDate): Int = cells.indexOfFirst { it?.date == date }.let { if (it < 0) -1 else it / 7 }

    val totalSec: Long get() = cells.sumOf { it?.sec?.toLong() ?: 0L }
    val filledDays: Int get() = cells.count { (it?.sec ?: 0) > 0 }

    companion object {
        /** [daily]는 날짜 → 그날 합(초). 없는 날은 0. */
        fun build(year: Int, weekStart: DayOfWeek, daily: Map<LocalDate, Int>, today: LocalDate): YearGrid {
            val first = LocalDate.of(year, 1, 1)
            val last = LocalDate.of(year, 12, 31)
            val start = first.with(TemporalAdjusters.previousOrSame(weekStart))
            val days = java.time.temporal.ChronoUnit.DAYS.between(start, last).toInt() + 1
            val cols = (days + 6) / 7
            val cells = ArrayList<Cell?>(cols * 7)
            for (i in 0 until cols * 7) {
                val d = start.plusDays(i.toLong())
                if (d.year != year) { cells += null; continue }
                val sec = daily[d] ?: 0
                cells += Cell(d, sec, Level.of(sec), d == today, d.isAfter(today))
            }
            return YearGrid(year, weekStart, cols, cells)
        }
    }
}
