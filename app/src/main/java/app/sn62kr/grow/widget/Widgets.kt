package app.sn62kr.grow.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.sn62kr.grow.GrowApp
import app.sn62kr.grow.R
import app.sn62kr.grow.core.stats.Level
import app.sn62kr.grow.core.stats.Sums
import app.sn62kr.grow.ui.MainActivity
import app.sn62kr.grow.ui.Text as Words
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// 홈 화면 위젯 둘(D14: 위젯은 무료). 스파이크 S1에서 배운 것:
// ① updateAll만으로는 다시 안 그린다 → Room Flow와 "오늘"을 위젯 안에서 구독한다.
// ② SizeMode.Exact(크기 후보마다 비트맵을 만들지 않게). ③ 알파 마스크 + tint로 다크 모드(비트맵을 다시 안 만든다).

private val BG = ColorProvider(day = Color(0xFFFBF7EF), night = Color(0xFF1F2328))
private val INK = ColorProvider(day = Color(0xFF2E3A2F), night = Color(0xFFE8E4DA))
private val DIM = ColorProvider(day = Color(0xFF6E6A5E), night = Color(0xFFA9A396))
private val TILE = ColorProvider(day = Color(0xFF566F2A), night = Color(0xFFAFC77A)) // 올리브(격자 4단계 색)

/** 칸 단계 → 알파. 0단계(빈 날)도 옅게 보이게. */
private val ALPHA = intArrayOf(28, 80, 140, 200, 255)

/**
 * 알파 마스크 격자: 열 = 주(마지막 열이 이번 주), 행 = 요일. [weeks]가 0이면 들어가는 만큼.
 * 오늘 칸은 테두리 고리를 하나 더 그린다(색은 tint가 정한다).
 */
internal fun gridMask(wPx: Int, hPx: Int, weeksWanted: Int, daily: Map<LocalDate, Int>, today: LocalDate, weekStart: DayOfWeek, gapRatio: Float = 0.22f): Bitmap {
    val rowStep = hPx / 7f
    val weeks = if (weeksWanted > 0) weeksWanted else (wPx / rowStep).toInt().coerceIn(4, 53)
    val step = minOf(wPx / weeks.toFloat(), rowStep)
    val cell = step * (1 - gapRatio)
    val bmp = Bitmap.createBitmap(wPx.coerceAtLeast(1), hPx.coerceAtLeast(1), Bitmap.Config.ALPHA_8)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = step * 0.12f; alpha = 255 }
    val left = (wPx - step * weeks) / 2f; val top = (hPx - step * 7) / 2f
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(weekStart))
    val first = thisWeek.minusWeeks((weeks - 1).toLong())
    val r = RectF()
    for (w in 0 until weeks) for (d in 0..6) {
        val date = first.plusDays((w * 7 + d).toLong())
        if (date.isAfter(today)) continue
        p.alpha = ALPHA[Level.of(daily[date] ?: 0)]
        val x = left + w * step; val y = top + d * step
        r.set(x, y, x + cell, y + cell)
        c.drawRoundRect(r, cell * 0.22f, cell * 0.22f, p)
        if (date == today) { r.inset(-step * 0.1f, -step * 0.1f); c.drawRoundRect(r, cell * 0.3f, cell * 0.3f, ring) }
    }
    return bmp
}

abstract class GridWidgetBase : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val g = (context.applicationContext as GrowApp).graph
        provideContent {
            val daily by g.records.daily().collectAsState(initial = emptyMap())
            val today by g.today.collectAsState(initial = g.clock.today())
            Content(daily, today, g.weekStart())
        }
    }

    @Composable abstract fun Content(daily: Map<LocalDate, Int>, today: LocalDate, weekStart: DayOfWeek)
}

/** 4×2: 올해 합(크게) + 오늘 + 최근 주들 격자. 누르면 앱. */
class YearWidget : GridWidgetBase() {
    @Composable
    override fun Content(daily: Map<LocalDate, Int>, today: LocalDate, weekStart: DayOfWeek) {
        val ctx = LocalContext.current
        val size = LocalSize.current
        val t = Sums.totals(daily, today, weekStart)
        val density = ctx.resources.displayMetrics.density
        val pad = 12.dp
        val headH = 44.dp
        val wPx = ((size.width - pad * 2).value * density).toInt().coerceAtLeast(40)
        val hPx = ((size.height - pad * 2 - headH).value * density).toInt().coerceAtLeast(20)
        val bmp = gridMask(wPx, hPx, 0, daily, today, weekStart)
        val desc = ctx.getString(R.string.widget_desc, Words.duration(ctx, t.yearSec), Words.duration(ctx, t.todaySec))
        Column(GlanceModifier.fillMaxSize().background(BG).cornerRadius(18.dp).padding(pad).clickable(actionStartActivity<MainActivity>())
            .semantics { contentDescription = desc }) {
            Row(GlanceModifier.fillMaxWidth().height(headH), verticalAlignment = Alignment.CenterVertically) {
                Text(Words.duration(ctx, t.yearSec), style = TextStyle(color = INK, fontSize = 22.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.width(8.dp))
                Text(ctx.getString(R.string.widget_this_year), style = TextStyle(color = DIM, fontSize = 13.sp))
                Spacer(GlanceModifier.defaultWeight())
                Text(ctx.getString(R.string.home_today_total, Words.duration(ctx, t.todaySec)), style = TextStyle(color = INK, fontSize = 13.sp))
            }
            Image(ImageProvider(bmp), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(TILE))
        }
    }
}

/** 4×1: 최근 주들(칸이 크다) + 오늘. */
class RecentWidget : GridWidgetBase() {
    @Composable
    override fun Content(daily: Map<LocalDate, Int>, today: LocalDate, weekStart: DayOfWeek) {
        val ctx = LocalContext.current
        val size = LocalSize.current
        val t = Sums.totals(daily, today, weekStart)
        val density = ctx.resources.displayMetrics.density
        val pad = 10.dp
        val textW = 78.dp
        val wPx = ((size.width - pad * 2 - textW).value * density).toInt().coerceAtLeast(40)
        val hPx = ((size.height - pad * 2).value * density).toInt().coerceAtLeast(20)
        val bmp = gridMask(wPx, hPx, 0, daily, today, weekStart)
        val desc = ctx.getString(R.string.widget_desc, Words.duration(ctx, t.yearSec), Words.duration(ctx, t.todaySec))
        Row(GlanceModifier.fillMaxSize().background(BG).cornerRadius(18.dp).padding(pad).clickable(actionStartActivity<MainActivity>())
            .semantics { contentDescription = desc }, verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(textW)) {
                Text(ctx.getString(R.string.totals_today), style = TextStyle(color = DIM, fontSize = 12.sp))
                Text(Words.duration(ctx, t.todaySec), style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.Medium))
            }
            Image(ImageProvider(bmp), null, GlanceModifier.defaultWeight().fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(TILE))
        }
    }
}

class YearWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = YearWidget() }
class RecentWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RecentWidget() }

object WidgetUpdates {
    suspend fun all(ctx: Context) {
        YearWidget().updateAll(ctx)
        RecentWidget().updateAll(ctx)
    }

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 3, Intent(ctx, MidnightReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** 다음 자정 1분 뒤(현지) 한 번. 부정확 알람이면 된다 — 새 날 칸이 30분 안에 보이면 된다(T2 기준). */
    fun scheduleMidnight(ctx: Context, zone: ZoneId = ZoneId.systemDefault()) {
        val at = LocalDate.now(zone).plusDays(1).atTime(0, 1).atZone(zone).toInstant().toEpochMilli()
        ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC, at, pi(ctx))
    }
}

/** 자정: 위젯을 다시 그리고 다음 자정을 건다. */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch {
            try { WidgetUpdates.all(ctx); WidgetUpdates.scheduleMidnight(ctx) } finally { pending.finish() }
        }
    }
}
