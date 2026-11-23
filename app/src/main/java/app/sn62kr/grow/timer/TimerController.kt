package app.sn62kr.grow.timer

import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.time.Days
import app.sn62kr.grow.core.time.Reading
import app.sn62kr.grow.core.time.RunningTimer
import app.sn62kr.grow.core.time.TimerMath
import app.sn62kr.grow.data.Records
import app.sn62kr.grow.data.TimerStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 타이머의 모든 변화가 지나는 곳. 상태를 저장하고, 알람과 조용한 알림을 맞춘다.
 * 화면·알람·부팅 신호가 동시에 부를 수 있어서 잠금 하나로 줄을 세운다.
 */
class TimerController(
    private val store: TimerStore,
    private val records: Records,
    private val clock: Clock,
    private val alarms: Alarms,
    private val notifier: Notifier,
) {
    private val lock = Mutex()

    val running: Flow<RunningTimer?> = store.running

    fun read(t: RunningTimer): Reading = TimerMath.read(t, clock.now())

    sealed interface StartResult {
        data object Started : StartResult
        data object AlreadyRunning : StartResult
    }

    suspend fun start(activityId: String, activityName: String, targetSec: Int?): StartResult = lock.withLock {
        if (store.get() != null) return StartResult.AlreadyRunning
        val now = clock.now()
        val t = TimerMath.start(activityId, now, Days.key(Days.localDate(now.wall, clock.zone())), targetSec)
        store.set(t)
        sync(t, activityName)
        StartResult.Started
    }

    suspend fun pause(activityName: String) = change(activityName) { TimerMath.pause(it, clock.now()) }

    suspend fun resume(activityName: String) = change(activityName) { TimerMath.resume(it, clock.now()) }

    private suspend fun change(activityName: String, f: (RunningTimer) -> RunningTimer) = lock.withLock {
        val t = store.get() ?: return@withLock
        val n = f(t)
        store.set(n)
        sync(n, activityName)
    }

    /** 끝낸다. 저장할 길이는 화면이 정한다(1분 미만·8시간 초과는 먼저 묻는다). null이면 버린다. */
    suspend fun finish(durationSec: Int?): Session? = lock.withLock {
        val t = store.get() ?: return null
        val saved = durationSec?.let { records.saveTimerSession(t.activityId, t.startWall, it, t.startLocalDate) }
        store.set(null)
        alarms.cancelTarget()
        notifier.clearRunning()
        saved
    }

    /** 재부팅·업데이트 뒤나 앱을 열 때: 기준을 다시 잡고 알람·알림을 다시 건다(S3 배운 것 3). */
    suspend fun rebase(activityName: (String) -> String?) = lock.withLock {
        val t = store.get() ?: return@withLock
        val n = TimerMath.rebase(t, clock.now())
        if (n != t) store.set(n)
        sync(n, activityName(n.activityId) ?: "")
    }

    private fun sync(t: RunningTimer, activityName: String) {
        val at = TimerMath.targetAtElapsed(t)
        if (at != null && at > clock.now().elapsed) alarms.scheduleTarget(at) else alarms.cancelTarget()
        notifier.showRunning(t, read(t), activityName, clock.now().wall)
    }
}
