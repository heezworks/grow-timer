package app.sn62kr.grow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sn62kr.grow.Graph
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.stats.Sums
import app.sn62kr.grow.core.stats.YearGrid
import app.sn62kr.grow.core.time.Rules
import app.sn62kr.grow.core.time.RunningTimer
import app.sn62kr.grow.core.time.TimerMath
import app.sn62kr.grow.data.Records
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 끝내기를 눌렀을 때 먼저 물어야 하는 것(design.md 2절). */
sealed interface FinishAsk {
    data class Short(val sec: Int) : FinishAsk
    data object OverMax : FinishAsk
    data object StalePause : FinishAsk
}

sealed interface HomeEvent {
    data class Finished(val activityName: String, val sec: Int) : HomeEvent
    data class Added(val activityName: String, val sec: Int, val date: LocalDate) : HomeEvent
    data class Deleted(val session: Session) : HomeEvent
    data object DayOver : HomeEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val g: Graph) : ViewModel() {
    private val stay = SharingStarted.WhileSubscribed(5_000)

    val activities: StateFlow<List<Activity>> = g.records.allActivities.stateIn(viewModelScope, stay, emptyList())

    val today: StateFlow<LocalDate> = g.today.stateIn(viewModelScope, SharingStarted.Eagerly, g.clock.today())

    /** 홈의 격자는 올해·전체 활동. 해 넘기기와 활동별은 "한 해" 탭에서. */
    private val daily = g.records.daily(null)
    val grid: StateFlow<YearGrid?> = combine(daily, today) { d, t -> YearGrid.build(t.year, g.weekStart(), d, t) }.stateIn(viewModelScope, stay, null)
    val yearSec: StateFlow<Long> = combine(daily, today) { d, t -> Sums.totals(d, t, g.weekStart()).yearSec }.stateIn(viewModelScope, stay, 0L)

    val selectedDay = MutableStateFlow<LocalDate?>(null)
    val daySessions: StateFlow<List<Session>> = selectedDay.flatMapLatest { d -> if (d == null) flowOf(emptyList()) else g.records.day(d) }
        .stateIn(viewModelScope, stay, emptyList())

    val todaySessions: StateFlow<List<Session>> = today.flatMapLatest { g.records.day(it) }.stateIn(viewModelScope, stay, emptyList())

    /** null = 아직 모름(첫 읽기 전). */
    val running: StateFlow<Loaded<RunningTimer?>> = g.timer.running.let { f ->
        flow { f.collect { emit(Loaded(it)) } }.stateIn(viewModelScope, SharingStarted.Eagerly, Loaded.Loading())
    }

    val ask = MutableStateFlow<FinishAsk?>(null)
    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val selectedId = MutableStateFlow<String?>(null)
    val targetMin = MutableStateFlow<Int?>(null)

    private var staleChecked = false

    fun now() = g.clock.now()
    fun canNotify() = g.notifier.canNotify()
    fun nameOf(id: String) = activities.value.firstOrNull { it.id == id }?.name ?: ""

    fun start() {
        val id = selectedId.value ?: activities.value.firstOrNull { !it.archived }?.id ?: return
        viewModelScope.launch { g.timer.start(id, nameOf(id), targetMin.value?.times(60)) }
    }

    fun pause(t: RunningTimer) = viewModelScope.launch { g.timer.pause(nameOf(t.activityId)) }
    fun resume(t: RunningTimer) = viewModelScope.launch { g.timer.resume(nameOf(t.activityId)) }

    /** 앱을 열었을 때 한 번: 멈춘 채 12시간이 지났으면 끝낼지 묻는다. */
    fun checkStale(t: RunningTimer) {
        if (staleChecked) return
        staleChecked = true
        if (t.isPaused && TimerMath.pausedForMs(t, now()) > Rules.STALE_PAUSE_MS) ask.value = FinishAsk.StalePause
    }

    fun requestFinish(t: RunningTimer) {
        val r = TimerMath.read(t, now())
        val q = when {
            r.needsCorrection -> FinishAsk.OverMax
            r.activeMs < Rules.SHORT_SESSION_MS -> FinishAsk.Short(TimerMath.durationSec(r))
            else -> return commit(t, TimerMath.durationSec(r))
        }
        // 묻는 동안 시간이 흐르지 않게 먼저 멈춘다. 멈춤이 저장된 뒤에 창을 띄운다(닫으면 resume이 뒤따르도록).
        viewModelScope.launch {
            g.timer.pause(nameOf(t.activityId))
            ask.value = q
        }
    }

    fun commit(t: RunningTimer, sec: Int?) {
        ask.value = null
        viewModelScope.launch {
            val s = g.timer.finish(sec)
            if (s != null) _events.emit(HomeEvent.Finished(nameOf(t.activityId), s.durationSec))
        }
    }

    /** 묻는 창을 닫으면 멈추기 전으로 돌린다. */
    fun dismissAsk(t: RunningTimer) {
        val a = ask.value
        ask.value = null
        if (a is FinishAsk.Short || a is FinishAsk.OverMax) resume(t)
    }

    fun finishFromStale(t: RunningTimer) {
        ask.value = null
        requestFinish(t)
    }

    fun addManual(activityId: String, date: LocalDate, sec: Int) = viewModelScope.launch {
        when (val r = g.records.addManual(activityId, date, sec, g.clock.zone(), g.clock.today())) {
            is Records.AddResult.Added -> _events.emit(HomeEvent.Added(nameOf(activityId), sec, date))
            Records.AddResult.OverDay -> _events.emit(HomeEvent.DayOver)
            Records.AddResult.Future -> Unit // 날짜 고르기에서 미래는 못 고른다
        }
    }

    fun updateSession(s: Session) = viewModelScope.launch {
        if (!g.records.updateSession(s)) _events.emit(HomeEvent.DayOver)
    }

    fun deleteSession(s: Session) = viewModelScope.launch {
        g.records.deleteSession(s.id)
        _events.emit(HomeEvent.Deleted(s))
    }

    fun undoDelete(s: Session) = viewModelScope.launch { g.records.restoreSession(s) }

    fun select(id: String) = selectedId.update { id }

    val askedNotify: StateFlow<Boolean> = g.settings.hasAskedNotify.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun markAskedNotify() = viewModelScope.launch { g.settings.setAskedNotify() }
}

/** 첫 읽기 전과 "없음"을 가른다 — 앱을 열 때 홈이 잠깐 보였다가 타이머로 바뀌는 깜빡임을 막는다. */
sealed interface Loaded<T> {
    class Loading<T> : Loaded<T>
    data class Value<T>(val v: T) : Loaded<T>

    companion object {
        operator fun <T> invoke(v: T): Loaded<T> = Value(v)
    }
}
