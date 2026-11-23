package app.sn62kr.grow

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.model.Source
import app.sn62kr.grow.data.toEntity
import app.sn62kr.grow.ui.records.YearScreen
import app.sn62kr.grow.ui.records.YearViewModel
import app.sn62kr.grow.ui.theme.GrowTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/** "한 해" 탭: 3년 치 가짜 기록(plan M2 완료 기준의 재료)으로 격자·누적·해 넘기기·그날 내역. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w400dp-h860dp-xxhdpi")
class YearTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock(LocalDateTime.of(2026, 11, 23, 21, 0))
    private val g = testGraph(app, clock)
    private val zone = ZoneId.of("Asia/Seoul")

    private fun waitFor(what: () -> Boolean) = compose.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idle(); what() }
    private fun waitText(t: String) = waitFor { compose.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty() }

    /** 2023-11-24 ~ 2026-11-23, 하루 0~5건(S4와 같은 모양). 활동 넷. */
    /** 넣은 세션 목록을 돌려준다. 기대값은 이것으로 계산한다 — 화면이 DB를 보는 동안 시험이 메인 스레드에서 DB를 막고 읽으면 가끔 멈췄다. */
    private fun seed(): List<Pair<String, Session>> = runBlocking {
        val acts = listOf("기타", "독서", "뜨개질", "그림").mapIndexed { i, n -> g.records.addActivity(n, i, "x") }
        val r = Random(2026)
        val out = ArrayList<Pair<String, Session>>()
        g.db.withTransaction {
            var d = LocalDate.of(2023, 11, 24)
            while (!d.isAfter(LocalDate.of(2026, 11, 23))) {
                val k = if (r.nextFloat() < 0.6f) 1 + r.nextInt(5) else 0
                repeat(k) {
                    val sec = 300 + r.nextInt(5400)
                    val st = d.atTime(8, 0).atZone(zone).toInstant().toEpochMilli() + r.nextInt(40_000) * 1000L
                    val a = acts[r.nextInt(4)]
                    val ses = Session(UUID.randomUUID().toString(), a.id, st, sec, d.toString(), Source.TIMER, st, st)
                    g.db.sessions().insert(ses.toEntity())
                    out += a.name to ses
                }
                d = d.plusDays(1)
            }
        }
        out
    }

    private fun List<Pair<String, Session>>.sumYear(y: Int, name: String? = null) =
        filter { (n, x) -> x.localDate.startsWith("$y-") && (name == null || n == name) }.sumOf { it.second.durationSec.toLong() }

    @Test fun threeYears() {
        val data = seed()
        val vm = YearViewModel(g)
        compose.setContent { AppFrame { YearScreen(vm) } }
        waitText("2026")
        waitFor { vm.grid.value?.totalSec == data.sumYear(2026) }
        compose.onRoot().captureRoboImage("screenshots/10-year-2026.png")

        // 해 넘기기: 2023까지만 간다
        repeat(3) { compose.onNodeWithContentDescription("이전 해").performClick() }
        waitText("2023")
        waitFor { vm.grid.value?.year == 2023 && vm.grid.value?.totalSec == data.sumYear(2023) }
        compose.onNodeWithContentDescription("이전 해").assertIsNotEnabled()

        // 활동별로 거르기: 격자 합이 그 활동의 합과 같다
        compose.onNodeWithContentDescription("다음 해").performClick(); compose.onNodeWithContentDescription("다음 해").performClick(); compose.onNodeWithContentDescription("다음 해").performClick()
        compose.onAllNodesWithText("뜨개질")[0].performClick() // 첫째가 거르기 칩, 둘째는 아래 목록
        val knit = data.sumYear(2026, "뜨개질")
        waitFor { vm.grid.value?.year == 2026 && vm.grid.value?.totalSec == knit }
        compose.onRoot().captureRoboImage("screenshots/11-year-filter.png")
    }

    @Test fun daySheetShowsThatDay() {
        val data = seed()
        val vm = YearViewModel(g)
        compose.setContent { AppFrame { YearScreen(vm) } }
        waitText("2026")
        val day = LocalDate.of(2026, 11, 20)
        val expected = data.filter { it.second.localDate == day.toString() }.sumOf { it.second.durationSec.toLong() }
        compose.runOnIdle { vm.select(day) }
        waitFor { vm.daySessions.value.sumOf { it.durationSec.toLong() } == expected }
        captureScreenRoboImage("screenshots/12-day-sheet.png") // 시트는 따로 된 창이라 화면 전체를 찍는다
        assertEquals(expected, vm.daySessions.value.sumOf { it.durationSec.toLong() })
    }

    @Test fun emptyYear() {
        compose.setContent { AppFrame { YearScreen(YearViewModel(g)) } }
        waitText("아직 보낸 시간이 없어요. 홈에서 시작해 보세요.")
    }

    @Test fun darkScreenshot() {
        RuntimeEnvironment.setQualifiers("+night") // @Config에 붙이면 순서가 틀린 문자열이 된다
        seed()
        val vm = YearViewModel(g)
        compose.setContent { AppFrame { YearScreen(vm) } }
        waitFor { vm.grid.value != null && vm.grid.value!!.totalSec > 0 }
        compose.onRoot().captureRoboImage("screenshots/13-year-dark.png")
    }
}
