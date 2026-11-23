package app.sn62kr.grow.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sn62kr.grow.R
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.model.Source
import app.sn62kr.grow.ui.ColorDot
import app.sn62kr.grow.ui.SessionRow
import app.sn62kr.grow.ui.SceneView
import app.sn62kr.grow.ui.plant.label
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import app.sn62kr.grow.ui.DaySheet
import app.sn62kr.grow.ui.YearGridView
import app.sn62kr.grow.ui.Text
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 홈. 타이머가 돌고 있으면 타이머 화면이 먼저 나온다(design.md 3절). */
@Composable
fun HomeRoute(vm: HomeViewModel, onActivities: () -> Unit, onPlant: () -> Unit = {}) {
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is HomeEvent.Finished -> snack.showSnackbar(
                    ctx.getString(R.string.timer_done, Text.with(ctx, e.activityName), Text.duration(ctx, e.sec.toLong())) + " " +
                        ctx.getString(when (e.growth) {
                            Growth.LEAF -> R.string.grew_leaf; Growth.STAGE -> R.string.grew_stage
                            Growth.FULL -> R.string.grew_full; Growth.STILL_LIFE -> R.string.grew_still
                        }))
                is HomeEvent.Added -> snack.showSnackbar(ctx.getString(R.string.manual_added, e.activityName, Text.duration(ctx, e.sec.toLong()),
                    Text.dayName(ctx, e.date, vm.today.value)))
                is HomeEvent.Deleted -> scope.launch {
                    if (snack.showSnackbar(ctx.getString(R.string.edit_deleted), ctx.getString(R.string.undo)) == SnackbarResult.ActionPerformed) vm.undoDelete(e.session)
                }
                HomeEvent.DayOver -> snack.showSnackbar(ctx.getString(R.string.day_over))
            }
        }
    }
    val running by vm.running.collectAsStateWithLifecycle()
    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        when (val r = running) {
            is Loaded.Loading -> Box(Modifier.fillMaxSize())
            is Loaded.Value -> r.v?.let { TimerScreen(vm, it, pad) } ?: HomeScreen(vm, pad, onActivities, onPlant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(vm: HomeViewModel, pad: PaddingValues, onActivities: () -> Unit, onPlant: () -> Unit) {
    val ctx = LocalContext.current
    val all by vm.activities.collectAsStateWithLifecycle()
    val active = all.filter { !it.archived }
    val sessions by vm.todaySessions.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val selected by vm.selectedId.collectAsStateWithLifecycle()
    val target by vm.targetMin.collectAsStateWithLifecycle()
    val grid by vm.grid.collectAsStateWithLifecycle()
    val yearSec by vm.yearSec.collectAsStateWithLifecycle()
    val day by vm.selectedDay.collectAsStateWithLifecycle()
    val garden by vm.garden.collectAsStateWithLifecycle()
    val welcomeBack by vm.welcomeBack.collectAsStateWithLifecycle()
    val dbError by vm.dbError.collectAsStateWithLifecycle()
    val seed by vm.gardenSeed.collectAsStateWithLifecycle()
    val daySessions by vm.daySessions.collectAsStateWithLifecycle()
    val chosen = selected?.takeIf { id -> active.any { it.id == id } } ?: active.firstOrNull()?.id
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Session?>(null) }

    // 알림 권한은 처음 시작할 때 한 번만 묻는다. 거부해도 다 된다(design.md 7절).
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.start() }
    val askedBefore by vm.askedNotify.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            // 장면이 홈의 맨 위(spec 2-1: 지금 키우는 식물). 누르면 식물 화면. 설정은 장면 위 오른쪽.
            Box {
                garden?.let { gs ->
                    val plantName = stringResource(gs.current.species.label())
                    SceneView(gs, seed, vm.poseFor(chosen), stringResource(R.string.scene_desc, plantName, gs.stillLife),
                        Modifier.fillMaxWidth().clickable(onClick = onPlant), cropTop = 34f) // 하늘을 잘라 낮게(파라솔 꼭대기까지는 남긴다)
                }
                IconButton(onClick = onActivities, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings_title), tint = Color(0xFF1C2C44))
                }
            }
        }
        if (dbError) item {
            Text(stringResource(R.string.home_db_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        if (welcomeBack && sessions.isEmpty()) item {
            Text(stringResource(R.string.home_welcome_back), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        }
        item {
            val total = sessions.sumOf { it.durationSec.toLong() }
            Text(
                if (sessions.isEmpty()) stringResource(R.string.home_empty) else stringResource(R.string.home_today_total, Text.duration(ctx, total)),
                style = if (sessions.isEmpty()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )
            if (yearSec > 0) Text(stringResource(R.string.home_year_total, Text.duration(ctx, yearSec)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        item {
            if (active.isEmpty()) {
                TextButton(onClick = onActivities, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.home_no_activity)) }
            } else {
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    active.forEach { a ->
                        FilterChip(a.id == chosen, { vm.select(a.id) }, { Text(a.name) }, leadingIcon = { ColorDot(a.colorIndex) })
                    }
                }
                FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(target == null, { vm.targetMin.value = null }, { Text(stringResource(R.string.home_target_none)) })
                    listOf(15, 25, 45, 60).forEach { m ->
                        FilterChip(target == m, { vm.targetMin.value = m }, { Text(stringResource(R.string.home_target_min, m)) })
                    }
                }
                Button(
                    onClick = {
                        chosen?.let(vm::select)
                        if (Build.VERSION.SDK_INT >= 33 && !vm.canNotify() && !askedBefore) {
                            vm.markAskedNotify(); ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else vm.start()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(56.dp),
                ) { Text(stringResource(R.string.home_start), style = MaterialTheme.typography.titleMedium) }
                TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.home_add_manual)) }
            }
        }
        grid?.let { g ->
            item {
                // 시작 버튼 아래(첫 화면에 시작이 보이게). 빈 격자도 그대로 보여 준다 — 채워질 자리가 보이게(spec 2-1)
                YearGridView(g, onDay = { vm.selectedDay.value = it }, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), cell = 12.dp)
            }
        }
        if (sessions.isNotEmpty()) {
            item {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.home_today), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
            }
            items(sessions, key = { it.id }) { s -> SessionRow(s, all) { editing = s } }
        }
    }

    if (adding) ManualAddDialog(active, chosen, today, onDismiss = { adding = false }) { id, date, sec ->
        adding = false; vm.addManual(id, date, sec)
    }
    day?.let { d ->
        DaySheet(d, today, daySessions, all, onDismiss = { vm.selectedDay.value = null },
            onSave = { vm.updateSession(it) }, onDelete = { vm.deleteSession(it) }, onAdd = { a, dd, sec -> vm.addManual(a, dd, sec) })
    }
    editing?.let { s ->
        SessionEditDialog(s, all, onDismiss = { editing = null },
            onSave = { editing = null; vm.updateSession(it) },
            onDelete = { editing = null; vm.deleteSession(it) })
    }
}
