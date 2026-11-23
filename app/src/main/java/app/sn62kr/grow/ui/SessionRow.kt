package app.sn62kr.grow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sn62kr.grow.R
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.model.Source
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 세션 한 줄: 색 · 활동 · 시작 시각(또는 "직접 더함") · 길이. 누르면 고치기. */
@Composable
fun SessionRow(s: Session, activities: List<Activity>, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val a = activities.firstOrNull { it.id == s.activityId }
    val time = if (s.source == Source.MANUAL) stringResource(R.string.home_manual_tag)
    else Instant.ofEpochMilli(s.startUtc).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    ListItem(
        headlineContent = { Text(a?.name ?: "—") },
        supportingContent = { Text(time) },
        leadingContent = { ColorDot(a?.colorIndex ?: 0, 14.dp) },
        trailingContent = { Text(Text.duration(ctx, s.durationSec.toLong()), style = MaterialTheme.typography.bodyLarge) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}
