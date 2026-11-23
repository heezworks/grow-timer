package app.sn62kr.grow

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sn62kr.grow.data.GrowDb
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 스키마 1 → 2(후기 표를 더함): 주인 폰의 기록이 그대로 남는다. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), GrowDb::class.java)

    @Test fun v1ToV2KeepsRecords() {
        helper.createDatabase("m.db", 1).use { db ->
            db.execSQL("INSERT INTO activity VALUES ('a','기타',0,'instrument',0,0,1)")
            db.execSQL("INSERT INTO session VALUES ('s','a',1,1800,'2026-11-23','timer',1,1)")
        }
        helper.runMigrationsAndValidate("m.db", 2, true).use { db ->
            db.query("SELECT COUNT(*), SUM(durationSec) FROM session").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)); assertEquals(1800, c.getInt(1)) }
            db.execSQL("INSERT INTO review VALUES ('r','a','preset',5,1,NULL,NULL,1,2)")
        }
    }
}
