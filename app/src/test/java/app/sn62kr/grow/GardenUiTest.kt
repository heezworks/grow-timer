package app.sn62kr.grow

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sn62kr.grow.core.model.Activity
import app.sn62kr.grow.core.model.Session
import app.sn62kr.grow.core.model.Source
import app.sn62kr.grow.ui.album.AlbumScreen
import app.sn62kr.grow.ui.album.AlbumViewModel
import app.sn62kr.grow.ui.album.polaroids
import app.sn62kr.grow.ui.plant.PlantScreen
import app.sn62kr.grow.ui.plant.PlantViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 식물 화면과 사진첩(M3). 기록은 적게(YearTest 머리말). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ko-w400dp-h860dp-xxhdpi")
class GardenUiTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val g = testGraph(app, FakeClock(LocalDateTime.of(2026, 11, 23, 21, 0)))
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 23)

    // 15초: CI 기계가 느리다
    private fun waitFor(what: () -> Boolean) = compose.waitUntil(15_000) { shadowOf(Looper.getMainLooper()).idle(); what() }

    private fun seed() = runBlocking {
        g.settings.ensureGardenSeed()
        val a = g.records.addActivity("기타", 0, "instrument"); val b = g.records.addActivity("뜨개질", 1, "knitting")
        listOf(90 to a, 4 to b, 45 to b, 120 to a, 30 to b, 3 to a).forEachIndexed { i, (min, act) ->
            g.records.addManual(act.id, today.minusDays(6L - i), min * 60, zone, today)
        }
    }

    @Test fun polaroidRule() {
        val a = Activity("a", "기타", 0, "instrument", 0, false, 0)
        fun s(id: String, sec: Int, t: Long) = Session(id, "a", t, sec, "2026-11-2${t}", Source.TIMER, t, t)
        val list = polaroids(listOf(s("1", 600, 1), s("2", 299, 2), s("3", 300, 3)), listOf(a))
        assertEquals(listOf("3", "1"), list.map { it.session.id }) // 5분 미만은 없고, 새것부터
        assertEquals(listOf(1199L, 600L), list.map { it.totalAtEndSec }) // 그때까지 쌓인 시간
    }

    @Test fun albumScreen() {
        seed()
        val vm = AlbumViewModel(g)
        compose.setContent { AppFrame { AlbumScreen(vm, onBack = {}) } }
        waitFor { vm.items.value?.size == 4 } // 4분·3분 세션은 사진이 없다
        compose.onRoot().captureRoboImage("screenshots/20-album.png")
    }

    @Test fun plantScreen() {
        seed()
        val vm = PlantViewModel(g)
        compose.setContent { AppFrame { PlantScreen(vm, onBack = {}) } }
        waitFor { compose.onAllNodesWithText("이 화분과", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("screenshots/21-plant.png")
    }
}
