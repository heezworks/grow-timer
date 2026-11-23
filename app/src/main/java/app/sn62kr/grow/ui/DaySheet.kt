package app.sn62kr.grow.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sn62kr.grow.R
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.ui.home.ManualAddDialog
import app.sn62kr.grow.ui.home.SessionEditDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 격자 칸을 누르면 그날 내역(design.md 3절): 고치기·지우기, 그날에 시간 더하기. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    date: LocalDate, today: LocalDate, sessions: List<Session>, activities: List<Activity>,
    onDismiss: () -> Unit, onSave: (Session) -> Unit, onDelete: (Session) -> Unit,
    onAdd: (activityId: String, date: LocalDate, sec: Int) -> Unit,
) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Session?>(null) }
    var adding by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            val title = if (date == today || date == today.minusDays(1)) Text.dayName(ctx, date, today).replaceFirstChar { it.uppercase() }
            else date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            val total = sessions.sumOf { it.durationSec.toLong() }
            Text(
                if (sessions.isEmpty()) stringResource(R.string.day_empty) else Text.duration(ctx, total),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            sessions.forEach { s -> SessionRow(s, activities) { editing = s } }
            TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.day_add)) }
        }
    }
    editing?.let { s ->
        SessionEditDialog(s, activities, onDismiss = { editing = null },
            onSave = { editing = null; onSave(it) }, onDelete = { editing = null; onDelete(it) })
    }
    if (adding) ManualAddDialog(activities.filter { !it.archived }, null, today, initialDate = date, onDismiss = { adding = false }) { a, d, sec ->
        adding = false; onAdd(a, d, sec)
    }
}
