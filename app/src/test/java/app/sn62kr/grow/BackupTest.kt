package app.sn62kr.grow

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sn62kr.grow.data.Backup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** S4의 시험을 제품 코드로(plan M5 완료 기준). 화면 없이. */
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 23)

    private fun filled(): Graph = testGraph(ctx).also { g ->
        runBlocking {
            g.settings.ensureGardenSeed(); g.settings.setWeekStart(1)
            val a = g.records.addActivity("기타", 0, "instrument"); val b = g.records.addActivity("독서", 1, "reading")
            var d = today.minusDays(400)
            var i = 0
            while (!d.isAfter(today)) { g.records.addManual(if (i % 3 == 0) a.id else b.id, d, 600 + (i % 7) * 300, zone, today); d = d.plusDays(2); i++ }
        }
    }

    @Test fun roundTripToEmptyPhone() = runBlocking {
        val src = filled()
        val text = src.exportBackup()
        val dst = testGraph(ctx)
        val r = Backup.decode(text) as Backup.Read.Ok
        val res = dst.importBackup(r)
        assertEquals(2, res.activitiesAdded); assertEquals(src.records.snapshot().second.size, res.sessionsAdded); assertEquals(0, res.skipped)
        assertEquals(src.records.daily().first(), dst.records.daily().first()) // 격자가 같다
        assertEquals(src.settings.gardenSeed.first(), dst.settings.gardenSeed.first()) // 빈 폰이면 정원도 같다
        assertEquals(1, dst.settings.weekStart.first()) // 설정도 온다(S4에서 빠졌던 것)
        assertTrue(dst.settings.isOnboarded.first())
    }

    @Test fun importTwiceAddsNothing() = runBlocking {
        val src = filled(); val text = src.exportBackup()
        val r = Backup.decode(text) as Backup.Read.Ok
        val again = src.importBackup(r)
        assertEquals(0, again.activitiesAdded + again.sessionsAdded)
        assertEquals(r.activities.size + r.sessions.size, again.skipped)
    }

    @Test fun gardenSeedKeptWhenPhoneHasRecords() = runBlocking {
        val src = filled(); val text = src.exportBackup()
        val dst = filled() // 다른 정원 씨앗, 기록 있음
        val before = dst.settings.gardenSeed.first()
        dst.importBackup(Backup.decode(text) as Backup.Read.Ok)
        assertEquals(before, dst.settings.gardenSeed.first())
    }

    @Test fun rejects() {
        assertEquals(Backup.Read.NotOurs, Backup.decode("""{"format":"something-else","version":1,"exportedAt":0,"activities":[],"sessions":[]}"""))
        assertEquals(Backup.Read.NotOurs, Backup.decode("""{"hello":"world"}"""))
        assertEquals(Backup.Read.Newer, Backup.decode("""{"format":"grow-timer-backup","version":99,"exportedAt":0,"activities":[],"sessions":[]}"""))
        val good = runBlocking { filled().exportBackup() }
        assertEquals(Backup.Read.Broken, Backup.decode(good.substring(0, good.length / 2))) // 잘린 파일
        assertEquals(Backup.Read.Broken, Backup.decode("not json at all"))
    }

    @Test fun sizeIsSmall() = runBlocking {
        val text = filled().exportBackup() // 약 200건
        assertTrue("${text.length} bytes", text.length < 200 * 400)
    }

    @Test fun deleteAll() = runBlocking {
        val g = filled()
        g.records.deleteAll()
        assertEquals(0L, g.records.totalSec.first())
        assertTrue(g.records.allActivities.first().isEmpty())
    }
}
