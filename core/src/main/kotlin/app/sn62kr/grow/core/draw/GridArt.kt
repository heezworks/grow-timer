package app.sn62kr.grow.core.draw

import app.sn62kr.grow.core.stats.Level
import app.sn62kr.grow.core.stats.YearGrid

/** 격자 색. [levels]는 0(빈 날)부터 4까지 다섯. 미래 칸은 빈 칸보다 옅게. */
class GridPalette(val levels: IntArray, val future: Int, val today: Int) {
    init { require(levels.size == Level.COUNT) }

    companion object {
        // 올리브 녹색 단계(그림의 팔레트, D56). 밝은 화면과 어두운 화면 둘.
        val LIGHT = GridPalette(
            intArrayOf(0xFFE9E4D8.toInt(), 0xFFCFDAAE.toInt(), 0xFFA6BD72.toInt(), 0xFF7A9644.toInt(), 0xFF566F2A.toInt()),
            future = 0xFFF3F0E8.toInt(), today = 0xFFB5552F.toInt(),
        )
        val DARK = GridPalette(
            intArrayOf(0xFF2E3130.toInt(), 0xFF3E4C2A.toInt(), 0xFF5A7236.toInt(), 0xFF7F9C4C.toInt(), 0xFFAFC77A.toInt()),
            future = 0xFF242726.toInt(), today = 0xFFF0B79D.toInt(),
        )
    }
}

/** 격자 한 칸의 크기와 간격(단위). 렌더러가 원하는 픽셀로 늘린다. */
object GridArt {
    const val CELL = 10f
    const val GAP = 2.5f
    const val STEP = CELL + GAP

    fun width(cols: Int) = cols * STEP - GAP
    const val HEIGHT = 7 * STEP - GAP

    fun build(g: YearGrid, p: GridPalette): Drawing {
        val ops = ArrayList<Op>(g.cols * 7 + 1)
        var todayOp: Op? = null
        for (c in 0 until g.cols) for (r in 0..6) {
            val cell = g.at(c, r) ?: continue
            val x = c * STEP; val y = r * STEP
            ops += RoundRect(x, y, CELL, CELL, 2.2f, if (cell.isFuture) p.future else p.levels[cell.level])
            if (cell.isToday) todayOp = RoundRectStroke(x - 0.75f, y - 0.75f, CELL + 1.5f, CELL + 1.5f, 2.8f, 1.5f, p.today)
        }
        todayOp?.let { ops += it } // 오늘 테두리는 맨 위에
        return Drawing(width(g.cols), HEIGHT, ops)
    }

    /** 단위 좌표 → 칸(열, 행). 간격을 눌러도 가까운 칸으로. 밖이면 null. */
    fun hit(g: YearGrid, x: Float, y: Float): Pair<Int, Int>? {
        val c = (x / STEP).toInt(); val r = (y / STEP).toInt()
        return if (x >= 0 && y >= 0 && c < g.cols && r < 7 && g.at(c, r) != null) c to r else null
    }
}
