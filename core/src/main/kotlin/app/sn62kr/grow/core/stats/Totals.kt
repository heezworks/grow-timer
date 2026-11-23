package app.sn62kr.grow.core.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 누적(design.md 4절 stats). 앞에는 누적만 둔다 — 달성률·연속 일수·순위 없음(D22, Q11). */
data class Totals(val todaySec: Long, val weekSec: Long, val yearSec: Long, val allSec: Long)

object Sums {
    /** [daily]는 모든 날의 합(초). 이번 주는 [weekStart]부터 오늘까지. */
    fun totals(daily: Map<LocalDate, Int>, today: LocalDate, weekStart: DayOfWeek): Totals {
        val weekFrom = today.with(TemporalAdjusters.previousOrSame(weekStart))
        var t = 0L; var w = 0L; var y = 0L; var all = 0L
        for ((d, s) in daily) {
            all += s
            if (d.year == today.year && !d.isAfter(today)) y += s
            if (!d.isBefore(weekFrom) && !d.isAfter(today)) w += s
            if (d == today) t += s
        }
        return Totals(t, w, y, all)
    }
}
