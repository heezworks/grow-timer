package app.sn62kr.grow.ui

import android.content.Context
import app.sn62kr.grow.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 화면 문구 조립. */
object Text {
    private fun isKo(ctx: Context) = ctx.resources.configuration.locales[0].language == "ko"

    /** 한국어면 "기타와" · "뜨개질과"처럼 받침에 맞춰 붙인다. 다른 말이면 이름 그대로. */
    fun with(ctx: Context, name: String): String = if (isKo(ctx)) name + josaWa(name) else name

    fun josaWa(name: String): String {
        val c = name.trimEnd().lastOrNull() ?: return "와"
        if (c in '가'..'힣') return if ((c - '가') % 28 != 0) "과" else "와"
        if (c.isDigit()) return if (c in "013678") "과" else "와" // 영·일·삼·육·칠·팔·십
        return "와(과)"
    }

    fun duration(ctx: Context, sec: Long): String = noBreakInside(durationRaw(ctx, sec))

    /** "30분"이 "30 / 분"으로 접히지 않게 낱말 안에 줄바꿈 금지 문자(U+2060)를 넣는다. 낱말 사이(공백)에서만 접힌다. */
    private fun noBreakInside(s: String) = s.split(' ').joinToString(" ") { w -> w.toList().joinToString("\u2060") }

    private fun durationRaw(ctx: Context, sec: Long): String {
        val m = sec / 60
        val h = m / 60
        return when {
            m == 0L -> ctx.getString(R.string.dur_under_min)
            h == 0L -> ctx.getString(R.string.dur_m, m.toInt())
            m % 60 == 0L -> ctx.getString(R.string.dur_h, h.toInt())
            else -> ctx.getString(R.string.dur_h_m, h.toInt(), (m % 60).toInt())
        }
    }

    /** 타이머 숫자: 1:02:03 또는 02:03. */
    fun clock(ms: Long): String {
        val s = (ms.coerceAtLeast(0) / 1000)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    fun dayName(ctx: Context, date: LocalDate, today: LocalDate): String = when (date) {
        today -> ctx.getString(R.string.today_lower)
        today.minusDays(1) -> ctx.getString(R.string.yesterday_lower)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}
