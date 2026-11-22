package app.sn62kr.spike.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// 자정 뒤 새 칸: 다음 자정 + 1분에 부정확 알람(권한 필요 없음, Doze에서도 setAndAllowWhileIdle)
// 시간 바꿈·시간대 바꿈·재부팅 때도 다시 그리고 다시 건다.
class DayChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("S1", "day change: ${intent.action}")
        Store.mark(context, "midnightFiredAt", System.currentTimeMillis())
        Store.mark(context, "midnightAction", intent.action ?: "")
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updateAllWidgets(context)
                scheduleNextMidnight(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "app.sn62kr.spike.widget.MIDNIGHT"

        fun scheduleAt(context: Context, atMillis: Long) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                context, 1, Intent(context, DayChangeReceiver::class.java).setAction(ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAndAllowWhileIdle(AlarmManager.RTC, atMillis, pi)
            Store.mark(context, "midnightScheduledFor", atMillis)
        }

        fun scheduleNextMidnight(context: Context) {
            val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli() + 60_000
            scheduleAt(context, next)
        }

        suspend fun updateAllWidgets(context: Context) {
            GridWidget(true).updateAll(context)
            GridWidget(false).updateAll(context)
        }
    }
}
