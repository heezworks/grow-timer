package app.sn62kr.grow

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.sn62kr.grow.data.GrowDb
import app.sn62kr.grow.data.Records
import app.sn62kr.grow.data.SettingsStore
import app.sn62kr.grow.data.TimerStore
import app.sn62kr.grow.timer.Alarms
import app.sn62kr.grow.timer.Clock
import app.sn62kr.grow.timer.Notifier
import app.sn62kr.grow.timer.SystemClockSource
import app.sn62kr.grow.timer.TimerController
import kotlinx.coroutines.flow.first

/** 수동 주입(design.md 4절: DI 라이브러리 없음). */
class Graph(ctx: Context) {
    val clock: Clock = SystemClockSource(ctx)
    val db: GrowDb = Room.databaseBuilder(ctx, GrowDb::class.java, "grow.db").build()
    val records = Records(db)
    val timerStore = TimerStore(ctx)
    val settings = SettingsStore(ctx)
    val notifier = Notifier(ctx)
    val timer = TimerController(timerStore, records, clock, Alarms(ctx), notifier)

    suspend fun rebaseTimer() {
        val names = records.allActivities.first().associate { it.id to it.name }
        timer.rebase { names[it] }
    }
}

class GrowApp : Application() {
    val graph by lazy { Graph(this) }
}
