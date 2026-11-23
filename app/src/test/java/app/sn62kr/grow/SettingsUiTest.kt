package app.sn62kr.grow

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sn62kr.grow.ui.home.HomeRoute
import app.sn62kr.grow.ui.home.HomeViewModel
import app.sn62kr.grow.ui.settings.SettingsScreen
import app.sn62kr.grow.ui.settings.SettingsViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val g = testGraph(app)

    // 15초: CI 기계가 느리다
    private fun waitText(t: String) = compose.waitUntil(15_000) { shadowOf(Looper.getMainLooper()).idle(); compose.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty() }

    @Config(qualifiers = "ko-w400dp-h860dp-xxhdpi")
    @Test fun settingsKo() {
        compose.setContent { AppFrame { SettingsScreen(SettingsViewModel(g), {}, {}, {}) } }
        waitText("파일로 내보내기")
        compose.onRoot().captureRoboImage("screenshots/30-settings.png")
    }

    /** plan M5 완료 기준 "영어로 바꿔 모든 화면에 한국어가 안 남음"의 한 조각: 홈과 설정 사진을 영어로 찍는다. */
    @Config(qualifiers = "en-w400dp-h860dp-xxhdpi")
    @Test fun english() {
        runBlocking { g.records.addActivities(listOf(Triple("Guitar", 0, "instrument"))) }
        compose.setContent { AppFrame { HomeRoute(HomeViewModel(g), onActivities = {}) } }
        waitText("Start")
        compose.onRoot().captureRoboImage("screenshots/31-home-en.png")
    }

    @Config(qualifiers = "en-w400dp-h860dp-xxhdpi")
    @Test fun settingsEn() {
        compose.setContent { AppFrame { SettingsScreen(SettingsViewModel(g), {}, {}, {}) } }
        waitText("Save to a file")
        compose.onRoot().captureRoboImage("screenshots/32-settings-en.png")
    }
}
