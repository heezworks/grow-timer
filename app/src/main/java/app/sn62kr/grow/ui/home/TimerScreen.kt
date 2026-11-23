package app.sn62kr.grow.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sn62kr.grow.R
import app.sn62kr.grow.core.time.RunningTimer
import app.sn62kr.grow.core.time.TimerMath
import app.sn62kr.grow.ui.ColorDot
import app.sn62kr.grow.ui.Text
import kotlinx.coroutines.delay

/** 타이머(spec 2-2). 시작을 누르고 폰을 내려놓는다. 앱을 나가도 실패·벌이 없다. */
@Composable
fun TimerScreen(vm: HomeViewModel, t: RunningTimer, pad: PaddingValues) {
    val all by vm.activities.collectAsStateWithLifecycle()
    val ask by vm.ask.collectAsStateWithLifecycle()
    val a = all.firstOrNull { it.id == t.activityId }
    var activeMs by remember { mutableLongStateOf(TimerMath.read(t, vm.now()).activeMs) }
    LaunchedEffect(t) {
        vm.checkStale(t)
        while (true) {
            activeMs = TimerMath.read(t, vm.now()).activeMs
            delay(200)
        }
    }
    val target = t.targetSec?.let { it * 1000L }
    val countingDown = target != null && activeMs < target

    Column(
        Modifier.fillMaxSize().padding(pad).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorDot(a?.colorIndex ?: 0, 16.dp)
            Text(a?.name ?: "", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            Text.clock(if (countingDown) target!! - activeMs + 999 else activeMs),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, fontFeatureSettings = "tnum"),
            textAlign = TextAlign.Center,
        )
        when {
            t.isPaused -> Text(stringResource(R.string.timer_paused), color = MaterialTheme.colorScheme.onSurfaceVariant)
            countingDown -> Text(stringResource(R.string.timer_left), color = MaterialTheme.colorScheme.onSurfaceVariant)
            target != null -> Text(stringResource(R.string.timer_over), color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (target != null && !vm.canNotify()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.timer_notify_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(48.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (t.isPaused) vm.resume(t) else vm.pause(t) }, modifier = Modifier.weight(1f).height(56.dp)) {
                Text(stringResource(if (t.isPaused) R.string.timer_resume else R.string.timer_pause))
            }
            Button(onClick = { vm.requestFinish(t) }, modifier = Modifier.weight(1f).height(56.dp)) {
                Text(stringResource(R.string.timer_finish))
            }
        }
    }

    when (val q = ask) {
        is FinishAsk.Short -> AlertDialog(
            onDismissRequest = { vm.dismissAsk(t) },
            text = { Text(stringResource(R.string.timer_short_ask)) },
            confirmButton = { TextButton(onClick = { vm.commit(t, q.sec) }) { Text(stringResource(R.string.timer_short_save)) } },
            dismissButton = { TextButton(onClick = { vm.commit(t, null) }) { Text(stringResource(R.string.timer_short_discard)) } },
        )
        FinishAsk.OverMax -> CorrectionDialog(onDismiss = { vm.dismissAsk(t) }, onSave = { vm.commit(t, it) })
        FinishAsk.StalePause -> AlertDialog(
            onDismissRequest = { vm.dismissAsk(t) },
            text = { Text(stringResource(R.string.timer_stale_pause)) },
            confirmButton = { TextButton(onClick = { vm.finishFromStale(t) }) { Text(stringResource(R.string.timer_finish)) } },
            dismissButton = { TextButton(onClick = { vm.dismissAsk(t) }) { Text(stringResource(R.string.timer_stale_keep)) } },
        )
        null -> Unit
    }
}
