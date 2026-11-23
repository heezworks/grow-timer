package app.sn62kr.grow

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sn62kr.grow.core.draw.Pose
import app.sn62kr.grow.net.FriendApi
import app.sn62kr.grow.net.Friends
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 두 기기(두 그래프)가 로컬 서버로 친구가 되어 서로의 테라스 숫자를 본다(plan M4b 완료 기준의 앞 절반).
 * 서버가 떠 있을 때만 돈다: `cd server && npx wrangler d1 migrations apply grow-timer --local && npm run dev`. CI에서는 건너뛴다.
 */
@RunWith(AndroidJUnit4::class)
class FriendsServerTest {
    private val base = System.getenv("GT_API") ?: "http://127.0.0.1:8787"
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun up() = runCatching { (URL("$base/v1/x").openConnection() as HttpURLConnection).run { connectTimeout = 800; responseCode; true } }.getOrDefault(false)

    private fun device(clock: FakeClock): Pair<Graph, Friends> {
        val g = testGraph(ctx, clock)
        return g to Friends(FriendApi(base), g.records, g.settings, clock)
    }

    @Test fun twoDevicesBecomeFriends() = runBlocking {
        assumeTrue("로컬 서버 없음", up())
        val clock = FakeClock(LocalDateTime.of(2026, 11, 24, 9, 0))
        val (ga, a) = device(clock); val (gb, b) = device(clock)
        ga.settings.ensureGardenSeed(); gb.settings.ensureGardenSeed()
        val guitar = gb.records.addActivity("비밀 이름", 1, "instrument")
        gb.records.addManual(guitar.id, LocalDate.of(2026, 11, 23), 90 * 60, ZoneId.of("Asia/Seoul"), LocalDate.of(2026, 11, 24))
        gb.records.saveTimerSession(guitar.id, clock.now().wall, 30 * 60, "2026-11-24")

        val codeA = (a.enable() as Friends.Out.Ok).v
        val codeB = (b.enable() as Friends.Out.Ok).v
        assertEquals(false, (a.add(codeB) as Friends.Out.Ok).v) // 아직 기다림
        assertEquals(true, (b.add(codeA) as Friends.Out.Ok).v)  // 양쪽 다 넣었다

        val seen = (a.list() as Friends.Out.Ok).v.friends.single()
        assertEquals(codeB, seen.code)
        assertEquals(120 * 60L, seen.totalSec)
        assertEquals(gb.settings.gardenSeed.first(), seen.gardenSeed) // 같은 정원을 다시 그릴 수 있다
        assertEquals(Pose.GUITAR, seen.pose)
        assertEquals(90 * 60, seen.daily[LocalDate.of(2026, 11, 23)])
        assertTrue("활동 이름은 가지 않는다", b.snapshot().toString().contains("비밀 이름").not())

        assertTrue(a.block(codeB) is Friends.Out.Ok)
        assertTrue((b.list() as Friends.Out.Ok).v.friends.isEmpty())
        a.disable(); b.disable()
    }
}
