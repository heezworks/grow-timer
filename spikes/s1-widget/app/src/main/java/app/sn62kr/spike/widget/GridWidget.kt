package app.sn62kr.spike.widget

import android.content.Context
import android.util.Log
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.time.LocalDate

private val BG = ColorProvider(day = Color(0xFFFBF7EF), night = Color(0xFF1F2328))
private val INK = ColorProvider(day = Color(0xFF2E3A2F), night = Color(0xFFE8E4DA))
private val TILE = ColorProvider(day = Color(0xFF5E7D3A), night = Color(0xFFA9C97A)) // 올리브

class GridWidget(private val showHeader: Boolean) : GlanceAppWidget() {
    // Exact: 실제 크기 하나만 그린다(Responsive는 크기 후보마다 비트맵을 만들어 4배 무겁다)
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val ctx = LocalContext.current
        val size = LocalSize.current
        val v by Store.version.collectAsState()
        v.hashCode() // 판이 바뀌면 다시 그린다
        val data = Store.minutesByDay(ctx)
        val today = LocalDate.now()
        val yearMin = data.filterKeys { LocalDate.ofEpochDay(it).year == today.year }.values.sum()
        val todayMin = data[today.toEpochDay()] ?: 0
        val header = showHeader && size.height >= 90.dp
        val density = ctx.resources.displayMetrics.density
        val pad = 10.dp
        val wPx = ((size.width - pad * 2).value * density).toInt().coerceAtLeast(40)
        val hPx = ((size.height - pad * 2 - (if (header) 22.dp else 0.dp)).value * density).toInt().coerceAtLeast(20)

        val t0 = System.nanoTime()
        val weeks = if (header) 53 else GridRenderer.weeksThatFit(wPx, hPx, 2f * density)
        val bmp = GridRenderer.render(wPx, hPx, weeks, data, today)
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        val info = "size=${size.width.value.toInt()}x${size.height.value.toInt()}dp px=${wPx}x${hPx} weeks=$weeks render=${"%.1f".format(ms)}ms bytes=${bmp.allocationByteCount}"
        Log.i("S1", info)
        Store.mark(ctx, "renderedAt", System.currentTimeMillis())
        Store.mark(ctx, "renderInfo", info)

        Column(
            modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(16.dp).padding(pad)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            if (header) {
                Text(
                    "올해 ${yearMin / 60}시간 · 오늘 ${todayMin}분",
                    style = TextStyle(color = INK, fontSize = 13.sp),
                    modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
            Image(
                provider = ImageProvider(bmp),
                contentDescription = "올해 ${yearMin / 60}시간을 보낸 격자",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(TILE),
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

class YearWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GridWidget(showHeader = true)
}

class RecentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GridWidget(showHeader = false)
}
