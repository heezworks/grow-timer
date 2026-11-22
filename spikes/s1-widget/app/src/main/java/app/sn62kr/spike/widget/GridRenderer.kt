package app.sn62kr.spike.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.time.DayOfWeek
import java.time.LocalDate

// 격자를 "알파 마스크" 비트맵으로 그린다. 색은 위젯 쪽에서 낮/밤 색으로 칠한다(tint).
// 그래서 다크 모드가 바뀌어도 비트맵을 다시 만들 필요가 없다.
object GridRenderer {
    private val LEVEL_ALPHA = intArrayOf(28, 80, 130, 190, 255)

    fun level(min: Int) = when {
        min <= 0 -> 0
        min < 15 -> 1
        min < 45 -> 2
        min < 120 -> 3
        else -> 4
    }

    /** 높이를 7칸으로 채우고 너비에 맞는 만큼의 주를 보여 준다(최대 53주). */
    fun weeksThatFit(wPx: Int, hPx: Int, gapPx: Float): Int {
        val cell = (hPx - 6 * gapPx) / 7f
        return ((wPx + gapPx) / (cell + gapPx)).toInt().coerceIn(4, 53)
    }

    fun render(wPx: Int, hPx: Int, weeks: Int, data: Map<Long, Int>, today: LocalDate = LocalDate.now()): Bitmap {
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val gap = (minOf(wPx / weeks, hPx / 7) * 0.18f).coerceAtLeast(1f)
        val cell = minOf((wPx - (weeks - 1) * gap) / weeks, (hPx - 6 * gap) / 7f)
        val gridW = weeks * cell + (weeks - 1) * gap
        val left = (wPx - gridW) / 2f
        val top = (hPx - (7 * cell + 6 * gap)) / 2f
        val r = cell * 0.22f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        // 마지막 열 = 이번 주(월요일 시작)
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val firstMonday = thisMonday.minusWeeks((weeks - 1).toLong())
        val rect = RectF()
        for (w in 0 until weeks) for (d in 0 until 7) {
            val date = firstMonday.plusWeeks(w.toLong()).plusDays(d.toLong())
            if (date.isAfter(today)) continue
            val lv = level(data[date.toEpochDay()] ?: 0)
            paint.alpha = LEVEL_ALPHA[lv]
            val x = left + w * (cell + gap)
            val y = top + d * (cell + gap)
            rect.set(x, y, x + cell, y + cell)
            c.drawRoundRect(rect, r, r, paint)
        }
        return bmp
    }
}
