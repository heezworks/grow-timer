package app.sn62kr.grow.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.sn62kr.grow.R
import app.sn62kr.grow.core.time.Reading
import app.sn62kr.grow.core.time.RunningTimer
import app.sn62kr.grow.ui.MainActivity
import app.sn62kr.grow.ui.Text

/**
 * 알림 둘: 도는 동안 조용한 알림 하나(포그라운드 서비스 아님, D44), 목표 시간이 됐을 때 하나.
 * 재촉하는 알림은 없다(D28). 권한이 없으면 띄우지 않고 타이머는 그대로 돈다(S3 ⑩).
 */
class Notifier(private val ctx: Context, private val enabled: () -> Boolean = { true }) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    /** 시스템 권한 + 앱 설정(알림 켬/끔) 둘 다. */
    fun canNotify() = enabled() && hasPermission()

    fun hasPermission() = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channels() {
        nm.createNotificationChannel(NotificationChannel(CH_RUNNING, ctx.getString(R.string.channel_running), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, ctx.getString(R.string.channel_done), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun openApp() = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    fun showRunning(t: RunningTimer, r: Reading, activityName: String, nowWall: Long) {
        if (!canNotify()) return
        channels()
        val b = Notification.Builder(ctx, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle(ctx.getString(R.string.notif_running, Text.with(ctx, activityName)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setContentIntent(openApp())
            .setCategory(Notification.CATEGORY_STOPWATCH)
        val target = t.targetSec
        when {
            t.isPaused -> b.setContentText(ctx.getString(R.string.notif_paused)).setUsesChronometer(false).setShowWhen(false)
            target != null && r.activeMs < target * 1000L -> b.setUsesChronometer(true).setChronometerCountDown(true)
                .setWhen(nowWall + target * 1000L - r.activeMs)
            else -> b.setUsesChronometer(true).setWhen(nowWall - r.activeMs)
        }
        nm.notify(ID_RUNNING, b.build())
    }

    fun clearRunning() = nm.cancel(ID_RUNNING)

    fun showTargetReached() {
        if (!canNotify()) return
        channels()
        nm.notify(ID_DONE, Notification.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle(ctx.getString(R.string.notif_target_title))
            .setContentText(ctx.getString(R.string.notif_target_text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build())
    }

    companion object {
        const val CH_RUNNING = "running"
        const val CH_DONE = "target"
        const val ID_RUNNING = 1
        const val ID_DONE = 2
    }
}
