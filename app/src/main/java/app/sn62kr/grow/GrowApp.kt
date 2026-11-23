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
import kotlinx.coroutines.flow.map
import app.sn62kr.grow.data.Backup
import app.sn62kr.grow.net.FriendApi
import app.sn62kr.grow.net.Friends
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
    val appContext: Context = ctx.applicationContext
    val records = Records(db)
    val timerStore = TimerStore(timerDs)
    val settings = SettingsStore(settingsDs)
    val notifier = Notifier(ctx) { notifyOn }
    val timer = TimerController(timerStore, records, clock, Alarms(ctx), notifier)
    val friends = Friends(FriendApi(BuildConfig.API_BASE), records, settings, clock)

    /** 오늘(현지). 자정이 지나면 바뀐다 — 30초마다 본다. */
    val today: Flow<LocalDate> = flow { while (true) { emit(clock.today()); delay(30_000) } }.distinctUntilChanged()

    /** 정원(식물·정물): 쌓인 시간 전체 + 정원 씨앗에서 계산(design.md 5절). */
    val garden: Flow<GardenState> = combine(records.totalSec, settings.gardenSeed) { t, s -> Garden.of(t, s) }

    /** 주의 첫날: 설정이 0이면 기기 로케일(design.md 2절). 화면은 [weekStartFlow]로 바뀜을 받는다. */
    val weekStartFlow: Flow<DayOfWeek> = settings.weekStart.map { if (it in 1..7) DayOfWeek.of(it) else WeekFields.of(Locale.getDefault()).firstDayOfWeek }
        .distinctUntilChanged()
    @Volatile private var weekStartNow: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    fun weekStart(): DayOfWeek = weekStartNow

    /** 알림 켬/끔 설정의 지금 값(Notifier가 묻는다). */
    @Volatile var notifyOn = true; private set

    /** 앱이 켜질 때 한 번: 설정 값을 따라간다. */
    fun follow(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch { weekStartFlow.collect { weekStartNow = it } }
        scope.launch { settings.notify.collect { notifyOn = it } }
    }

    /** 백업 파일 글자. */
    suspend fun exportBackup(): String {
        val (a, s) = records.snapshot()
        return Backup.encode(a, s, settings.export(), clock.now().wall)
    }

    /** 백업 가져오기(합치기). 이 폰에 기록이 없을 때만 정원 씨앗도 가져온다. */
    suspend fun importBackup(r: Backup.Read.Ok): Records.MergeResult {
        val empty = records.totalSec.first() == 0L
        val res = records.merge(r.activities, r.sessions)
        settings.import(r.settings, takeGardenSeed = empty)
        settings.setOnboarded()
        return res
    }

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
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        graph.follow(kotlinx.coroutines.GlobalScope)
        Friends.schedule(this)
    }
}
