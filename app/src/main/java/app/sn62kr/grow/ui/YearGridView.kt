package app.sn62kr.grow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sn62kr.grow.R
import app.sn62kr.grow.core.draw.GridArt
import app.sn62kr.grow.core.draw.GridPalette
import app.sn62kr.grow.core.stats.YearGrid
import app.sn62kr.grow.render.AndroidRenderer
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 연간 격자(53주 × 7일). core가 그리기 목록을 만들고 AndroidRenderer가 칠한다 — 위젯·카드와 같은 그림(D45).
 * 가로로 스크롤되고, 처음에는 오늘이 오른쪽에 보이게 맞춘다. 칸을 누르면 [onDay].
 */
@Composable
fun YearGridView(grid: YearGrid, onDay: (LocalDate) -> Unit, modifier: Modifier = Modifier, cell: Dp = 14.dp) {
    val ctx = LocalContext.current
    val palette = if (isSystemInDarkTheme()) GridPalette.DARK else GridPalette.LIGHT
    val drawing = remember(grid, palette) { GridArt.build(grid, palette) }
    val density = LocalDensity.current
    val k = with(density) { cell.toPx() } / GridArt.CELL // 단위 → 픽셀
    val widthDp = with(density) { (GridArt.width(grid.cols) * k).toDp() }
    val heightDp = with(density) { (GridArt.HEIGHT * k).toDp() }
    val stepDp = with(density) { (GridArt.STEP * k).toDp() }
    val locale = Locale.getDefault()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val scroll = rememberScrollState()
    val desc = stringResource(R.string.grid_desc, grid.year, grid.filledDays, Text.duration(ctx, grid.totalSec))

    Row(modifier) {
        // 요일 이름은 둘째·넷째·여섯째 줄에만(GitHub처럼 겹치지 않게)
        Column(Modifier.padding(top = 16.dp, end = 4.dp)) {
            for (r in 0..6) Box(Modifier.height(stepDp), contentAlignment = Alignment.CenterStart) {
                if (r % 2 == 1) Text(grid.weekStart.plus(r.toLong()).getDisplayName(TextStyle.NARROW, locale), style = labelStyle, color = dim)
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val viewport = with(density) { maxWidth.toPx() }
            LaunchedEffect(grid.year, viewport) {
                val col = grid.colOf(grid.cells.firstOrNull { it?.isToday == true }?.date ?: LocalDate.of(grid.year, 12, 31))
                val target = if (col < 0) scroll.maxValue else ((col + 2) * GridArt.STEP * k - viewport).toInt()
                scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
            }
            Column(Modifier.horizontalScroll(scroll)) {
                Box(Modifier.width(widthDp).height(16.dp)) {
                    for (m in 1..12) {
                        val col = grid.colOf(LocalDate.of(grid.year, m, 1))
                        // 1일이 그 주의 끝 쪽이면 다음 열에 이름을 둔다(앞달과 겹치지 않게)
                        val c = if (grid.at(col, 0)?.date?.monthValue != m && m > 1) col + 1 else col
                        Text(java.time.Month.of(m).getDisplayName(TextStyle.SHORT, locale), style = labelStyle, color = dim,
                            modifier = Modifier.offset(x = stepDp * c))
                    }
                }
                Canvas(
                    Modifier.width(widthDp).height(heightDp)
                        .semantics { contentDescription = desc }
                        .pointerInput(grid) {
                            detectTapGestures { o ->
                                val hit = GridArt.hit(grid, o.x / k, o.y / k) ?: return@detectTapGestures
                                val c = grid.at(hit.first, hit.second) ?: return@detectTapGestures
                                if (!c.isFuture) onDay(c.date)
                            }
                        },
                ) { drawIntoCanvas { AndroidRenderer.draw(it.nativeCanvas, drawing, size.width) } }
            }
        }
    }
}

/** 적게 ■■■■■ 많이 */
@Composable
fun GridLegend(modifier: Modifier = Modifier) {
    val p = if (isSystemInDarkTheme()) GridPalette.DARK else GridPalette.LIGHT
    val style = MaterialTheme.typography.labelSmall
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.grid_less), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 2.dp))
        p.levels.forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(it))) }
        Text(stringResource(R.string.grid_more), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
    }
}
