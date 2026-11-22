package app.sn62kr.spike.widget

import android.content.Context
import java.time.LocalDate
import kotlin.random.Random

// 스파이크용 가짜 기록. 날짜별 분을 SharedPreferences에 "epochDay:분" 줄로 둔다.
object Store {
    private const val PREFS = "s1"
    // 위젯이 구독하는 판 번호. 저장할 때마다 오른다(제품에서는 Room Flow가 이 일을 한다)
    val version = kotlinx.coroutines.flow.MutableStateFlow(0L)

    fun minutesByDay(ctx: Context): Map<Long, Int> {
        val raw = ctx.getSharedPreferences(PREFS, 0).getString("days", null)
            ?: return regenerate(ctx)
        return raw.split(',').filter { it.isNotEmpty() }.associate {
            val (d, m) = it.split(':'); d.toLong() to m.toInt()
        }
    }

    fun regenerate(ctx: Context, seed: Int = 7): Map<Long, Int> {
        val rnd = Random(seed)
        val today = LocalDate.now().toEpochDay()
        val map = (0 until 371).associate { back ->
            val day = today - back
            // 대략 55%의 날만 시간을 보냄, 그날은 10~150분
            day to if (rnd.nextFloat() < 0.55f) 10 + rnd.nextInt(140) else 0
        }.filterValues { it > 0 }.toMutableMap()
        map.remove(today) // 오늘은 비워 두고 버튼으로 더한다
        save(ctx, map)
        return map
    }

    fun addToday(ctx: Context, minutes: Int) {
        val map = minutesByDay(ctx).toMutableMap()
        val today = LocalDate.now().toEpochDay()
        map[today] = (map[today] ?: 0) + minutes
        save(ctx, map)
        mark(ctx, "savedAt", System.currentTimeMillis())
    }

    private fun save(ctx: Context, map: Map<Long, Int>) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString("days", map.entries.joinToString(",") { "${it.key}:${it.value}" })
            .commit()
        version.value = System.nanoTime()
    }

    fun mark(ctx: Context, key: String, value: Long) {
        ctx.getSharedPreferences(PREFS, 0).edit().putLong(key, value).commit()
    }

    fun mark(ctx: Context, key: String, value: String) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString(key, value).commit()
    }

    fun long(ctx: Context, key: String) = ctx.getSharedPreferences(PREFS, 0).getLong(key, 0)
    fun str(ctx: Context, key: String) = ctx.getSharedPreferences(PREFS, 0).getString(key, "") ?: ""
}
