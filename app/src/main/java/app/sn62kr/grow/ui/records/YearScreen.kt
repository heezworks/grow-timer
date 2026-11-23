package app.sn62kr.grow.ui.records

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sn62kr.grow.R
import app.sn62kr.grow.core.stats.Totals
import app.sn62kr.grow.ui.ColorDot
import app.sn62kr.grow.ui.DaySheet
import app.sn62kr.grow.ui.GridLegend
import app.sn62kr.grow.ui.Text
import app.sn62kr.grow.ui.YearGridView
import app.sn62kr.grow.ui.review.ReviewSheet
import androidx.compose.foundation.clickable

/** "한 해" 탭(spec 2-4). 성적표가 아니라 올해 다녀온 작은 휴가들 — 누적만, 연속 일수·달성률 없음(D22, Q11). */
@Composable
fun YearScreen(vm: YearViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val grid by vm.grid.collectAsStateWithLifecycle()
    val year by vm.year.collectAsStateWithLifecycle()
    val years by vm.years.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val activities by vm.activities.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val byActivity by vm.byActivity.collectAsStateWithLifecycle()
    val day by vm.selectedDay.collectAsStateWithLifecycle()
    val daySessions by vm.daySessions.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftYear(-1) }, enabled = years?.let { year > it.first } ?: false) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.year_prev))
                }
                Text("$year", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = { vm.shiftYear(1) }, enabled = year < today.year) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.year_next))
                }
            }
        }
        item { WidgetTip(vm) }
        if (years == null) item {
            Text(stringResource(R.string.year_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == null, { vm.filter.value = null }, { Text(stringResource(R.string.filter_all)) })
                activities.filter { !it.archived }.forEach { a ->
                    FilterChip(filter == a.id, { vm.filter.value = a.id }, { Text(a.name) }, leadingIcon = { ColorDot(a.colorIndex) })
                }
            }
        }
        grid?.let { g ->
            item {
                YearGridView(g, onDay = vm::select, modifier = Modifier.padding(start = 12.dp, end = 0.dp, top = 12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.grid_summary, pluralStringResource(R.plurals.grid_days, g.filledDays, g.filledDays), Text.duration(ctx, g.totalSec)),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    GridLegend()
                }
            }
        }
        totals?.let { t -> item { TotalsRow(t) } }
        if (byActivity.isNotEmpty()) {
            item {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.by_activity, year), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() })
            }
            items(byActivity, key = { it.first }) { (id, sec) ->
                val a = activities.firstOrNull { it.id == id }
                ListItem(
                    headlineContent = { Text(a?.name ?: "—") },
                    leadingContent = { ColorDot(a?.colorIndex ?: 0, 14.dp) },
                    trailingContent = { Text(Text.duration(ctx, sec), style = MaterialTheme.typography.bodyLarge) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { vm.reviewing.value = id }.padding(horizontal = 4.dp), // 누르면 그 활동의 후기
                )
            }
        }
    }

    val reviewing by vm.reviewing.collectAsStateWithLifecycle()
    val reviews by vm.reviews.collectAsStateWithLifecycle()
    val friendsOn by vm.friendsOn.collectAsStateWithLifecycle()
    reviewing?.let { id ->
        activities.firstOrNull { it.id == id }?.let { a ->
            ReviewSheet(a, reviews.filter { it.activityId == id }, friendsOn, onDismiss = { vm.reviewing.value = null },
                onAdd = { s, l, c, t, sh -> vm.addReview(id, s, l, c, t, sh) }, onDelete = { vm.deleteReview(it.id) })
        }
    }

    day?.let { d ->
        DaySheet(d, today, daySessions, activities, onDismiss = { vm.select(null) },
            onSave = vm::save, onDelete = vm::delete, onAdd = vm::add)
    }
}

@Composable
private fun TotalsRow(t: Totals) {
    val ctx = LocalContext.current
    // 2 × 2: "538시간 57분"이 한 줄에 들어가게(넷을 한 줄에 두면 접혔다)
    val cells = listOf(R.string.totals_today to t.todaySec, R.string.totals_week to t.weekSec, R.string.totals_year to t.yearSec, R.string.totals_all to t.allSec)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(2).forEach { pair ->
            Row {
                pair.forEach { (label, sec) ->
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Text.duration(ctx, sec), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

/** "홈 화면에 올해를 놓아 둘 수 있어요" — 런처가 위젯 고정을 지원하고, 놓인 위젯이 없고, 닫지 않았을 때만(plan M4 "위젯 추가 안내"). */
@Composable
private fun WidgetTip(vm: YearViewModel) {
    val ctx = LocalContext.current
    val closed by vm.widgetTipClosed.collectAsStateWithLifecycle()
    val am = android.appwidget.AppWidgetManager.getInstance(ctx)
    val provider = android.content.ComponentName(ctx, app.sn62kr.grow.widget.YearWidgetReceiver::class.java)
    val placed = am.getAppWidgetIds(provider).isNotEmpty() ||
        am.getAppWidgetIds(android.content.ComponentName(ctx, app.sn62kr.grow.widget.RecentWidgetReceiver::class.java)).isNotEmpty()
    if (closed || placed || !am.isRequestPinAppWidgetSupported) return
    androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.widget_tip), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = { vm.closeWidgetTip() }) { Text(stringResource(R.string.widget_tip_close)) }
                androidx.compose.material3.TextButton(onClick = { am.requestPinAppWidget(provider, null, null) }) { Text(stringResource(R.string.widget_tip_add)) }
            }
        }
    }
}
