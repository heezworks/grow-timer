package app.sn62kr.grow.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sn62kr.grow.GrowApp
import app.sn62kr.grow.widget.WidgetUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private fun BroadcastReceiver.work(ctx: Context, block: suspend (GrowApp) -> Unit) {
    val pending = goAsync()
    val app = ctx.applicationContext as GrowApp
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try { block(app) } finally { pending.finish() }
    }
}

/** 목표 시간이 됐다. 타이머는 끝내지 않는다 — 천천히 마무리해도 된다. */
class TargetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = work(ctx) { app ->
        if (app.graph.timerStore.get() != null) app.graph.notifier.showTargetReached()
    }
}

/** 재부팅·앱 업데이트 뒤: 기준을 다시 잡고 알람·알림을 다시 건다. BOOT_COMPLETED는 늦게 온다(S3) — 앱을 열 때도 같은 일을 한다. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = work(ctx) { app ->
        app.graph.rebaseTimer()
        WidgetUpdates.scheduleMidnight(ctx) // 재부팅하면 알람이 사라진다
        WidgetUpdates.all(ctx)
    }
}
