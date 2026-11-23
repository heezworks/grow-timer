package app.sn62kr.grow

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sn62kr.grow.ui.records.YearScreen
import app.sn62kr.grow.ui.records.YearViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
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

/**
 * "한 해" 탭의 화면 흐름. 기록은 적게 넣는다 — 3년 치를 넣은 화면 시험은 Robolectric에서 가끔 멈췄다(2026-11-23).
 * 3년 치 합계가 맞는지는 화면 없이 RecordsTest.threeYearsOfDailySums가 본다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w400dp-h860dp-xxhdpi")
class YearTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock(LocalDateTime.of(2026, 11, 23, 21, 0))
    private val g = testGraph(app, clock)
    private val zone = ZoneId.of("Asia/Seoul")

    private fun waitFor(what: () -> Boolean) = compose.waitUntil(15_000) // CI 기계가 느리다 { shadowOf(Looper.getMainLooper()).idle(); what() }
    private fun waitText(t: String) = waitFor { compose.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty() }

    /** 2025-11 ~ 2026-11, 사흘에 한 번 30분씩(약 120건). 활동 둘. */
    private fun seed() = runBlocking {
        val a = g.records.addActivity("기타", 0, "x"); val b = g.records.addActivity("뜨개질", 1, "x")
        var d = LocalDate.of(2025, 11, 1); var i = 0
        while (!d.isAfter(LocalDate.of(2026, 11, 23))) {
            g.records.addManual(if (i % 2 == 0) a.id else b.id, d, 1800 + (i % 5) * 900, zone, LocalDate.of(2026, 11, 23))
            d = d.plusDays(3); i++
        }
    }

    @Test fun yearsAndFilter() {
        seed()
        val vm = YearViewModel(g)
        compose.setContent { AppFrame { YearScreen(vm) } }
        waitFor { (vm.grid.value?.totalSec ?: 0) > 0 }
        compose.onRoot().captureRoboImage("screenshots/10-year.png")
        compose.onNodeWithContentDescription("이전 해").performClick()
        waitFor { vm.grid.value?.year == 2025 }
        compose.onNodeWithContentDescription("이전 해").assertIsNotEnabled() // 첫 기록이 2025년
        compose.onNodeWithContentDescription("다음 해").performClick()
        waitFor { vm.grid.value?.year == 2026 } // 해가 바뀐 뒤에 합을 읽는다(CI에서 먼저 읽어 틀렸다)
        val all = vm.grid.value!!.totalSec
        compose.onAllNodesWithText("뜨개질")[0].performClick() // 첫째가 거르기 칩
        waitFor { vm.grid.value!!.totalSec in 1 until all }
    }

    @Test fun daySheet() {
        seed()
        val vm = YearViewModel(g)
        compose.setContent { AppFrame { YearScreen(vm) } }
        waitFor { vm.grid.value != null }
        compose.runOnIdle { vm.select(LocalDate.of(2026, 11, 20)) } // 2025-11-01부터 사흘 간격에 드는 날(384일 뒤)
        waitFor { vm.daySessions.value.isNotEmpty() }
    }

    @Test fun emptyYear() {
        compose.setContent { AppFrame { YearScreen(YearViewModel(g)) } }
        waitText("아직 보낸 시간이 없어요. 홈에서 시작해 보세요.")
    }

    @Test fun darkAndLargeFont() {
        RuntimeEnvironment.setQualifiers("+night")
        RuntimeEnvironment.setFontScale(2f)
        seed()
        val vm = YearViewModel(g)
        compose.setContent { AppFrame { YearScreen(vm) } }
        waitFor { (vm.grid.value?.totalSec ?: 0) > 0 }
        compose.onRoot().captureRoboImage("screenshots/13-year-dark-font2x.png")
    }
}
