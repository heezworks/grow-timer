package app.sn62kr.grow.timer

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import app.sn62kr.grow.core.time.Days
import app.sn62kr.grow.core.time.Now
import java.time.LocalDate
import java.time.ZoneId

/** 지금 시각 세 가지를 읽는다. 시험에서는 바꿔 끼운다. */
interface Clock {
    fun now(): Now
    fun zone(): ZoneId
    /** 오늘(현지). 벽시계에서 구한다 — 타이머가 박는 시작한 날과 같은 규칙. */
    fun today(): LocalDate = Days.localDate(now().wall, zone())
}

class SystemClockSource(private val ctx: Context) : Clock {
    override fun now() = Now(
        System.currentTimeMillis(),
        SystemClock.elapsedRealtime(),
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1),
    )

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
