package app.sn62kr.grow

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import app.sn62kr.grow.data.settingsDataStore
import app.sn62kr.grow.data.timerDataStore
import app.sn62kr.grow.data.GrowDb
import app.sn62kr.grow.data.Records
import app.sn62kr.grow.data.SettingsStore
import app.sn62kr.grow.data.TimerStore
import app.sn62kr.grow.timer.Alarms
import app.sn62kr.grow.timer.Clock
import app.sn62kr.grow.timer.Notifier
import app.sn62kr.grow.timer.SystemClockSource
import app.sn62kr.grow.timer.TimerController
import kotlinx.coroutines.delay
import app.sn62kr.grow.core.garden.Garden
import app.sn62kr.grow.core.garden.GardenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import app.sn62kr.grow.widget.WidgetUpdates
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/** 수동 주입(design.md 4절: DI 라이브러리 없음). */
class Graph(
    ctx: Context,
    val clock: Clock = SystemClockSource(ctx),
    val db: GrowDb = Room.databaseBuilder(ctx, GrowDb::class.java, "grow.db").build(),
    timerDs: DataStore<Preferences> = ctx.applicationContext.timerDataStore,
    settingsDs: DataStore<Preferences> = ctx.applicationContext.settingsDataStore,
) {
    val records = Records(db)
    val timerStore = TimerStore(timerDs)
    val settings = SettingsStore(settingsDs)
    val notifier = Notifier(ctx)
    val timer = TimerController(timerStore, records, clock, Alarms(ctx), notifier)

    /** 오늘(현지). 자정이 지나면 바뀐다 — 30초마다 본다. */
    val today: Flow<LocalDate> = flow { while (true) { emit(clock.today()); delay(30_000) } }.distinctUntilChanged()

    /** 정원(식물·정물): 쌓인 시간 전체 + 정원 씨앗에서 계산(design.md 5절). */
    val garden: Flow<GardenState> = combine(records.totalSec, settings.gardenSeed) { t, s -> Garden.of(t, s) }

    /** 주의 첫날. 기기 로케일을 따른다(design.md 2절). 설정에서 바꾸기는 M5. */
    fun weekStart(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    suspend fun rebaseTimer() {
        val names = records.allActivities.first().associate { it.id to it.name }
        timer.rebase { names[it] }
    }
}

class GrowApp : Application() {
    val graph by lazy { Graph(this) }

    override fun onCreate() {
        super.onCreate()
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch { graph.settings.ensureGardenSeed() } // 정원 씨앗은 처음 한 번
        // 기록이 바뀌면 위젯을 다시 그린다(앱 프로세스가 있을 때만 기록이 바뀐다). 자정 갱신도 건다.
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch {
            graph.records.allChrono.drop(1).collectLatest { delay(500); WidgetUpdates.all(this@GrowApp) }
        }
        WidgetUpdates.scheduleMidnight(this)
    }
}
