package app.sn62kr.grow.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sn62kr.grow.R
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.time.Rules
import app.sn62kr.grow.ui.ColorDot
import app.sn62kr.grow.ui.DurationFields
import app.sn62kr.grow.ui.Text
import app.sn62kr.grow.ui.fieldsToSec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityPicker(activities: List<Activity>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        activities.forEach { a ->
            FilterChip(selected = a.id == selected, onClick = { onSelect(a.id) }, label = { Text(a.name) },
                leadingIcon = { ColorDot(a.colorIndex) })
        }
    }
}

/** 시간 직접 더하기(spec 2-3). 미래 날짜는 고를 수 없다. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ManualAddDialog(
    activities: List<Activity>, initialActivity: String?, today: LocalDate,
    initialDate: LocalDate = today,
    onDismiss: () -> Unit, onSave: (activityId: String, date: LocalDate, sec: Int) -> Unit,
) {
    val ctx = LocalContext.current
    var act by rememberSaveable { mutableStateOf(initialActivity ?: activities.firstOrNull()?.id) }
    var date by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var h by rememberSaveable { mutableStateOf("") }
    var m by rememberSaveable { mutableStateOf("30") }
    var picking by remember { mutableStateOf(false) }
    val day = LocalDate.parse(date)
    val sec = fieldsToSec(h, m)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.manual_hint), style = MaterialTheme.typography.bodyMedium)
                ActivityPicker(activities, act) { act = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(day == today, { date = today.toString() }, { Text(stringResource(R.string.manual_today)) })
                    FilterChip(day == today.minusDays(1), { date = today.minusDays(1).toString() }, { Text(stringResource(R.string.manual_yesterday)) })
                    val other = day != today && day != today.minusDays(1)
                    FilterChip(other, { picking = true },
                        { Text(if (other) Text.dayName(ctx, day, today) else stringResource(R.string.manual_pick_date)) })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { q ->
                        FilterChip(sec == q * 60, { h = if (q >= 60) "${q / 60}" else ""; m = if (q >= 60) "" else "$q" },
                            { Text(Text.duration(ctx, q * 60L)) })
                    }
                }
                DurationFields(h, m, { h = it }, { m = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { act?.let { onSave(it, day, sec) } }, enabled = act != null && sec > 0 && sec <= Rules.DAY_LIMIT_SEC) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (picking) {
        val todayUtc = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

/** 오늘 기록 하나 고치기·지우기. */
@Composable
fun SessionEditDialog(
    session: Session, activities: List<Activity>,
    onDismiss: () -> Unit, onSave: (Session) -> Unit, onDelete: (Session) -> Unit,
) {
    var act by rememberSaveable { mutableStateOf(session.activityId) }
    var h by rememberSaveable { mutableStateOf((session.durationSec / 3600).takeIf { it > 0 }?.toString() ?: "") }
    var m by rememberSaveable { mutableStateOf((session.durationSec / 60 % 60).toString()) }
    val sec = fieldsToSec(h, m)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActivityPicker(activities.filter { !it.archived || it.id == session.activityId }, act) { act = it }
                DurationFields(h, m, { h = it }, { m = it }, Modifier.fillMaxWidth())
                TextButton(onClick = { onDelete(session) }) {
                    Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(session.copy(activityId = act, durationSec = sec)) }, enabled = sec > 0 && sec <= Rules.DAY_LIMIT_SEC) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** 8시간을 넘었거나 시계가 뒤로 갔을 때 실제로 보낸 시간을 받는다(spec 2-2). */
@Composable
fun CorrectionDialog(onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var h by rememberSaveable { mutableStateOf("1") }
    var m by rememberSaveable { mutableStateOf("0") }
    val sec = fieldsToSec(h, m)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.timer_over_max))
                DurationFields(h, m, { h = it }, { m = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sec) }, enabled = sec > 0 && sec * 1000L <= Rules.MAX_SESSION_MS) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
