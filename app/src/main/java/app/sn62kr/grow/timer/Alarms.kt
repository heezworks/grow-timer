package app.sn62kr.grow.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** 목표 시간 알람 하나. elapsed 기준이라 시계를 바꿔도 안 흔들린다(S3 ④: Doze에서 0.95초). */
class Alarms(private val ctx: Context) {
    private val am = ctx.getSystemService(AlarmManager::class.java)

    private fun pi() = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, TargetAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun scheduleTarget(atElapsed: Long) {
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, pi())
        else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, pi()) // 몇 분 늦을 수 있다
    }

    fun cancelTarget() = am.cancel(pi())
}
