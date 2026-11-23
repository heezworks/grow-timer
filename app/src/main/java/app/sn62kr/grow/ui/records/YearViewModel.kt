package app.sn62kr.grow.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sn62kr.grow.Graph
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.stats.Sums
import app.sn62kr.grow.core.stats.Totals
import app.sn62kr.grow.core.stats.YearGrid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** "한 해" 탭: 연간 격자(전체·활동별), 누적, 그해 활동별 합. 주·월 나눔과 분석은 두지 않는다(D60). */
@OptIn(ExperimentalCoroutinesApi::class)
class YearViewModel(private val g: Graph) : ViewModel() {
    private val stay = SharingStarted.WhileSubscribed(5_000)

    val today: StateFlow<LocalDate> = g.today.stateIn(viewModelScope, SharingStarted.Eagerly, g.clock.today())
    val year = MutableStateFlow(g.clock.today().year)
    val filter = MutableStateFlow<String?>(null)
    val selectedDay = MutableStateFlow<LocalDate?>(null)

    val activities: StateFlow<List<Activity>> = g.records.allActivities.stateIn(viewModelScope, stay, emptyList())

    private val daily = filter.flatMapLatest { g.records.daily(it) }
    private val allDaily = g.records.daily(null)

    val grid: StateFlow<YearGrid?> = combine(year, daily, today) { y, d, t -> YearGrid.build(y, g.weekStart(), d, t) }
        .stateIn(viewModelScope, stay, null)

    val totals: StateFlow<Totals?> = combine(daily, today) { d, t -> Sums.totals(d, t, g.weekStart()) }.stateIn(viewModelScope, stay, null)

    /** 넘겨 볼 수 있는 해: 첫 기록의 해 ~ 올해. 기록이 없으면 null(빈 상태). */
    val years: StateFlow<IntRange?> = combine(allDaily, today) { d, t -> d.keys.minOrNull()?.let { it.year..t.year } }
        .stateIn(viewModelScope, stay, null)

    val byActivity: StateFlow<List<Pair<String, Long>>> = year.flatMapLatest { g.records.activityTotals(it) }.stateIn(viewModelScope, stay, emptyList())

    /** 그날 내역은 활동으로 거르지 않고 다 보여 준다. */
    val daySessions: StateFlow<List<Session>> = selectedDay.flatMapLatest { d -> if (d == null) flowOf(emptyList()) else g.records.day(d) }
        .stateIn(viewModelScope, stay, emptyList())

    fun shiftYear(by: Int) { year.value += by }
    fun select(d: LocalDate?) { selectedDay.value = d }
    fun save(s: Session) = viewModelScope.launch { g.records.updateSession(s) }
    fun delete(s: Session) = viewModelScope.launch { g.records.deleteSession(s.id) }
    fun add(activityId: String, date: LocalDate, sec: Int) = viewModelScope.launch {
        g.records.addManual(activityId, date, sec, g.clock.zone(), g.clock.today())
    }
}
