package app.sn62kr.grow

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import app.sn62kr.grow.core.time.Now
import app.sn62kr.grow.data.GrowDb
import app.sn62kr.grow.timer.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** 손으로 돌리는 시계. 벽시계만 따로 움직이면 "사용자가 시계를 바꾼 것"이 된다. */
class FakeClock(start: LocalDateTime = LocalDateTime.of(2026, 11, 22, 10, 0), val zoneId: ZoneId = ZoneId.of("Asia/Seoul")) : Clock {
    var wall = start.atZone(zoneId).toInstant().toEpochMilli()
    var elapsed = 1_000_000L
    var boot = 3

    fun advance(ms: Long) { wall += ms; elapsed += ms }

    /** 재부팅: elapsed가 0 근처부터, 부팅 횟수 +1. 그 사이 [offMs]만큼 꺼져 있었다. */
    fun reboot(offMs: Long) { wall += offMs; elapsed = 5_000; boot++ }

    override fun now() = Now(wall, elapsed, boot)
    override fun zone() = zoneId
}

/**
 * 시험마다 새 DB 파일과 새 DataStore 파일. 메모리 DB는 연결이 하나라 화면이 Flow로 읽는 동안 쓰기가 멈췄다 —
 * 앱과 같은 파일 DB(WAL, 읽기 연결 따로)를 쓴다.
 */
fun testGraph(ctx: Context, clock: FakeClock = FakeClock()): Graph {
    val dir = File(ctx.cacheDir, "ds-${System.nanoTime()}").apply { mkdirs() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    return Graph(
        ctx, clock,
        Room.databaseBuilder(ctx, GrowDb::class.java, File(dir, "grow.db").path).build(),
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "timer.preferences_pb") },
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") },
    )
}

/** 앱과 같은 바탕(MainActivity는 Surface 안에 화면을 둔다). 다크 모드 사진의 바탕색이 맞게. */
@androidx.compose.runtime.Composable
fun AppFrame(content: @androidx.compose.runtime.Composable () -> Unit) =
    app.sn62kr.grow.ui.theme.GrowTheme {
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() }
    }
