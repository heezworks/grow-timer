package app.sn62kr.spike.backup

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

// 스파이크: 제품은 Room이지만 자동 백업이 보는 것은 같은 DB 파일이다. 여기서는 SQLite 그대로.
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "grow.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE activity(id TEXT PRIMARY KEY, name TEXT, colorIndex INT, iconKey TEXT, sortOrder INT, archived INT, createdAt INT)")
        db.execSQL("CREATE TABLE session(id TEXT PRIMARY KEY, activityId TEXT, startUtc INT, durationSec INT, localDate TEXT, source TEXT, createdAt INT, updatedAt INT)")
        db.execSQL("CREATE INDEX s_date ON session(localDate)")
        db.execSQL("CREATE TABLE plant(id TEXT PRIMARY KEY, seed INT, speciesKey TEXT, ord INT, plantedAt INT, grownAt INT, name TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
}

object Backup {
    private fun log(ctx: Context, m: String) { Log.i("S4", m) }

    fun generate(ctx: Context) {
        val db = Db(ctx).writableDatabase
        db.beginTransaction()
        try {
            listOf("activity", "session", "plant").forEach { db.delete(it, null, null) }
            val r = Random(2026)
            val names = listOf("기타", "독서", "뜨개질", "그림", "외국어", "운동")
            val acts = names.mapIndexed { i, n ->
                val id = UUID(r.nextLong(), r.nextLong()).toString()
                db.insert("activity", null, ContentValues().apply {
                    put("id", id); put("name", n); put("colorIndex", i); put("iconKey", "icon$i"); put("sortOrder", i); put("archived", 0); put("createdAt", 1700000000000L + i)
                }); id
            }
            val start = LocalDate.of(2023, 11, 22)
            var n = 0
            for (d in 0 until 365 * 3) {
                val date = start.plusDays(d.toLong())
                val k = if (r.nextFloat() < 0.6f) 1 + r.nextInt(5) else 0 // 바쁜 날은 하루 5건까지
                repeat(k) {
                    val sec = 300 + r.nextInt(7200)
                    val st = date.atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli() + r.nextInt(80000) * 1000L
                    db.insert("session", null, ContentValues().apply {
                        put("id", UUID(r.nextLong(), r.nextLong()).toString()); put("activityId", acts[r.nextInt(acts.size)])
                        put("startUtc", st); put("durationSec", sec); put("localDate", date.toString())
                        put("source", if (r.nextFloat() < 0.1f) "manual" else "timer"); put("createdAt", st); put("updatedAt", st)
                    }); n++
                }
            }
            listOf("OLIVE", "LEMON", "LAVENDER", "OLIVE").forEachIndexed { i, sp ->
                db.insert("plant", null, ContentValues().apply {
                    put("id", UUID(r.nextLong(), r.nextLong()).toString()); put("seed", r.nextLong()); put("speciesKey", sp); put("ord", i)
                    put("plantedAt", 1700000000000L + i * 86400000L * 200); if (i < 3) put("grownAt", 1700000000000L + (i + 1) * 86400000L * 200)
                })
            }
            db.setTransactionSuccessful()
            log(ctx, "GEN sessions=$n")
        } finally { db.endTransaction() }
        ctx.getSharedPreferences("settings", 0).edit().putInt("weekStart", 1).putBoolean("signal", true).commit()
        ctx.getSharedPreferences("running_timer", 0).edit().putBoolean("running", true).commit() // 자동 백업에서 빠져야 한다
    }

    fun export(ctx: Context): File {
        val t0 = System.nanoTime()
        val db = Db(ctx).readableDatabase
        fun table(name: String): JSONArray {
            val a = JSONArray()
            db.rawQuery("SELECT * FROM $name ORDER BY id", null).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in 0 until c.columnCount) when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> o.put(c.getColumnName(i), c.getLong(i))
                        android.database.Cursor.FIELD_TYPE_NULL -> {}
                        else -> o.put(c.getColumnName(i), c.getString(i))
                    }
                    a.put(o)
                }
            }
            return a
        }
        val root = JSONObject()
            .put("format", "grow-timer-backup").put("version", 1).put("exportedAt", System.currentTimeMillis())
            .put("activities", table("activity")).put("sessions", table("session")).put("plants", table("plant"))
            .put("settings", JSONObject(ctx.getSharedPreferences("settings", 0).all as Map<*, *>))
        val f = File(ctx.getExternalFilesDir(null), "backup.json")
        f.writeText(root.toString())
        log(ctx, "EXPORT bytes=${f.length()} ms=${(System.nanoTime() - t0) / 1_000_000}")
        return f
    }

    /** 합치기: 같은 id는 한 번만(design.md 1절 Q17). */
    fun import(ctx: Context) {
        val t0 = System.nanoTime()
        val f = File(ctx.getExternalFilesDir(null), "import.json")
        val root = try { JSONObject(f.readText()) } catch (e: Exception) { log(ctx, "IMPORT 실패: 읽을 수 없는 파일 (${e.javaClass.simpleName})"); return }
        if (root.optString("format") != "grow-timer-backup") { log(ctx, "IMPORT 거절: 「이름」 백업 파일이 아니에요"); return }
        if (root.optInt("version") > 1) { log(ctx, "IMPORT 거절: 더 새 버전에서 만든 백업이에요"); return }
        val db = Db(ctx).writableDatabase
        var added = 0; var skipped = 0
        db.beginTransaction()
        try {
            for ((table, key) in listOf("activity" to "activities", "session" to "sessions", "plant" to "plants")) {
                val a = root.getJSONArray(key)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val cv = ContentValues()
                    o.keys().forEach { k -> when (val v = o.get(k)) { is Number -> cv.put(k, v.toLong()); else -> cv.put(k, v.toString()) } }
                    if (db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_IGNORE) == -1L) skipped++ else added++
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        log(ctx, "IMPORT added=$added skipped=$skipped ms=${(System.nanoTime() - t0) / 1_000_000}")
    }

    /** 합계·격자·식물 입력의 지문. 두 기기에서 같아야 한다. */
    fun summary(ctx: Context) {
        val db = Db(ctx).readableDatabase
        val md = MessageDigest.getInstance("SHA-256")
        var count = 0; var total = 0L
        db.rawQuery("SELECT localDate, SUM(durationSec), COUNT(*) FROM session GROUP BY localDate ORDER BY localDate", null).use { c ->
            while (c.moveToNext()) { md.update("${c.getString(0)}:${c.getLong(1)};".toByteArray()); total += c.getLong(1); count += c.getInt(2) }
        }
        val grid = md.digest().take(6).joinToString("") { "%02x".format(it) }
        val md2 = MessageDigest.getInstance("SHA-256")
        db.rawQuery("SELECT seed, speciesKey, ord, plantedAt, IFNULL(grownAt,-1) FROM plant ORDER BY ord", null).use { c ->
            while (c.moveToNext()) md2.update("${c.getLong(0)}|${c.getString(1)}|${c.getInt(2)}|${c.getLong(3)}|${c.getLong(4)};".toByteArray())
        }
        val plants = md2.digest().take(6).joinToString("") { "%02x".format(it) }
        val perAct = StringBuilder()
        db.rawQuery("SELECT a.name, SUM(s.durationSec)/3600 FROM session s JOIN activity a ON a.id = s.activityId GROUP BY a.name ORDER BY a.name", null).use { c ->
            while (c.moveToNext()) perAct.append("${c.getString(0)}=${c.getLong(1)}h ")
        }
        val running = ctx.getSharedPreferences("running_timer", 0).getBoolean("running", false)
        val week = ctx.getSharedPreferences("settings", 0).getInt("weekStart", -1)
        log(ctx, "SUMMARY sessions=$count totalH=${total / 3600} grid=$grid plants=$plants weekStart=$week runningTimer=$running | $perAct")
    }

    fun wipe(ctx: Context) {
        ctx.deleteDatabase("grow.db")
        ctx.getSharedPreferences("settings", 0).edit().clear().commit()
        ctx.getSharedPreferences("running_timer", 0).edit().clear().commit()
        log(ctx, "WIPE")
    }
}

class Cmd : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getStringExtra("cmd")) {
            "gen" -> Backup.generate(ctx)
            "export" -> Backup.export(ctx)
            "import" -> Backup.import(ctx)
            "summary" -> Backup.summary(ctx)
            "wipe" -> Backup.wipe(ctx)
        }
    }
}
