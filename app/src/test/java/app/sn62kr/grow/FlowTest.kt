package app.sn62kr.grow

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sn62kr.grow.ui.home.HomeRoute
import app.sn62kr.grow.ui.home.HomeViewModel
import app.sn62kr.grow.ui.onboarding.OnboardingScreen
import app.sn62kr.grow.ui.onboarding.OnboardingViewModel
import app.sn62kr.grow.ui.theme.GrowTheme
import com.github.takahirom.roborazzi.captureRoboImage
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
import kotlinx.coroutines.flow.first

/**
 * 화면 흐름 시험 + 화면 사진(D62). 폰 없이 JVM에서 돈다.
 * 사진은 app/screenshots/에 남는다. 기준 사진 비교는 CI가 리눅스 기준을 만든 뒤에 켠다(.github/workflows/ci.yml).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w400dp-h860dp-xxhdpi")
class FlowTest {
    @get:Rule val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val g = testGraph(app, clock)

    init { shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS) }

    /** Robolectric에서 compose.waitUntil은 메인 스레드 큐를 돌리지 않는다 — 메인으로 돌아오는 코루틴이 멈춘 채 남는다. 직접 돌린다. */
    // 15초: CI 기계가 느리다
    private fun waitFor(what: () -> Boolean) = compose.waitUntil(15_000) { shadowOf(Looper.getMainLooper()).idle(); what() }

    private fun waitText(text: String) = waitFor { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun onboardingCreatesPickedActivities() {
        var done = false
        compose.setContent { AppFrame { OnboardingScreen(OnboardingViewModel(g)) { done = true } } }
        compose.onRoot().captureRoboImage("screenshots/01-onboarding.png")
        compose.onNodeWithText("독서").performClick()
        compose.onNodeWithText("악기").performClick()
        compose.onNodeWithText("시작").performClick()
        waitFor { done }
        val names = runBlocking { g.records.allActivities.first().map { it.name } }
        assertEquals(listOf("독서", "악기"), names)
    }

    @Test fun skipMakesDefaultActivity() {
        var done = false
        compose.setContent { AppFrame { OnboardingScreen(OnboardingViewModel(g)) { done = true } } }
        compose.onNodeWithText("건너뛰기").performClick()
        waitFor { done }
        assertEquals(listOf("나의 시간"), runBlocking { g.records.allActivities.first().map { it.name } })
    }

    @Test fun startFinishShortAndNormal() {
        runBlocking { g.records.addActivities(listOf(Triple("독서", 0, "reading"), Triple("뜨개질", 1, "knitting"))) }
        val vm = HomeViewModel(g)
        compose.setContent { AppFrame { HomeRoute(vm, onActivities = {}) } }
        waitText("첫 시간을 재면 싹이 나요.")
        waitText("독서")
        compose.onRoot().captureRoboImage("screenshots/02-home-empty.png")

        // 1분 안에 끝내면 묻는다 → 남기기
        compose.onNodeWithText("시작").performClick()
        waitText("끝내기")
        clock.advance(20_000)
        compose.onNodeWithText("끝내기").performClick()
        waitText("1분이 안 됐어요. 그래도 남길까요?")
        compose.onRoot().captureRoboImage("screenshots/03-short-ask.png")
        compose.onNodeWithText("남기기").performClick()
        waitText(nb("1분 미만"))

        // 뜨개질 32분: 받침이 있으니 "뜨개질과"
        compose.onNodeWithText("뜨개질").performClick()
        compose.onNodeWithText("25분").performClick()
        compose.onNodeWithText("시작").performClick()
        waitText("끝내기")
        clock.advance(10 * 60_000)
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("screenshots/04-timer-countdown.png")
        clock.advance(22 * 60_000)
        compose.onNodeWithText("끝내기").performClick()
        waitText("뜨개질과 " + nb("32분") + "을 보냈어요. 식물이 조금 달라졌어요.") // 20초 + 32분: 첫 식물이 30분 단계를 넘었다
        compose.onRoot().captureRoboImage("screenshots/05-home-today.png")
        compose.onNodeWithText("오늘 " + nb("32분")).assertIsDisplayed()

        assertEquals(listOf(32 * 60, 20), vm.todaySessions.value.map { it.durationSec }) // 화면이 보는 값으로 본다(메인에서 DB를 막고 읽지 않는다)
    }

    @Test fun homeLargeFont() {
        RuntimeEnvironment.setFontScale(2f)
        runBlocking { g.records.addActivities(listOf(Triple("정원 가꾸기", 0, "gardening"), Triple("뜨개질", 1, "knitting"))) }
        compose.setContent { AppFrame { HomeRoute(HomeViewModel(g), onActivities = {}) } }
        waitText("정원 가꾸기")
        compose.onRoot().captureRoboImage("screenshots/06-home-font2x.png")
    }

    @Test fun welcomeBackAfterAWeek() {
        runBlocking {
            val a = g.records.addActivity("독서", 0, "reading")
            g.records.addManual(a.id, clock.today().minusDays(10), 1800, java.time.ZoneId.of("Asia/Seoul"), clock.today())
        }
        compose.setContent { AppFrame { HomeRoute(HomeViewModel(g), onActivities = {}) } }
        waitText("다시 왔네요. 식물은 그대로 기다리고 있었어요.")
    }

    @Test fun dismissingShortAskResumes() {
        runBlocking { g.records.addActivities(listOf(Triple("독서", 0, "reading"))) }
        compose.setContent { AppFrame { HomeRoute(HomeViewModel(g), onActivities = {}) } }
        waitText("독서")
        compose.onNodeWithText("시작").performClick()
        waitText("끝내기")
        compose.onNodeWithText("끝내기").performClick()
        waitText("1분이 안 됐어요. 그래도 남길까요?")
        androidx.test.espresso.Espresso.pressBack() // 창 닫기 → 멈추기 전으로
        waitFor { runBlocking { g.timerStore.get()?.isPaused == false } }
        assertTrue(runBlocking { g.timerStore.get() != null })
    }
}
