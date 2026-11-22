package app.sn62kr.core.draw

enum class Pose { BOOK, GUITAR }

/** 장면의 입력. 그림은 저장하지 않고 이 값으로 매번 다시 그린다(D43). */
data class SceneInput(
    val seed: Long,
    val pose: Pose,
    val objects: Int,              // 놓인 정물 수(쌓인 시간이 정한다)
    val plants: List<Pair<Species, Float>>, // (종, 자란 정도 0~1) 왼쪽부터
    val square: Boolean = false,   // 폴라로이드
    val dusk: Boolean = false,     // 다크 모드 = 저녁
)

/** 테라스 장면: 하늘·바다·회벽·바닥·파라솔·선베드·"나"·화분·정물(D57). 시안 페이지(2026-11-19)의 그림을 옮겼다. */
object SceneArt {
    fun build(inp: SceneInput): Drawing {
        val W = 320f; val H = if (inp.square) 320f else 240f
        val b = DrawBuilder(W, H)
        val c = if (inp.dusk) Palette.DUSK else Palette.DAY
        val r = Rng(inp.seed)
        val oy = if (inp.square) 70f else 0f
        val horizon = 92f + oy; val wallTop = 118f + oy; val floorTop = 150f + oy

        b.ops += VGradient(0f, 0f, W, horizon, c.skyTop, c.skyBottom)
        b.circle(258f, horizon - 44f - oy * 0.4f, 17f, c.sun)
        b.rect(0f, horizon, W, wallTop - horizon, c.sea)
        for (i in 0 until 9) {
            val wx = r.f() * W; val wy = horizon + 5 + r.f() * (wallTop - horizon - 9)
            b.line(1.2f, 0x73FFFFFF, wx, wy, wx + 8 + r.f() * 10, wy)
        }
        val bx = 150 + r.f() * 70
        b.poly(c.white, bx, horizon + 6, bx + 9, horizon + 6, bx + 4, horizon - 9)

        b.rect(0f, wallTop, W, floorTop - wallTop, c.wall)
        b.rect(0f, floorTop - 4, W, 4f, 0x0F000000)
        for (i in 0 until 26) b.circle(4 + r.f() * 70, wallTop - 4 + r.f() * 18, 2.4f + r.f() * 2.4f, if (r.f() > 0.5f) 0xFFC2307A.toInt() else 0xFFD9529A.toInt())
        for (i in 0 until 10) b.ellipse(6 + r.f() * 64, wallTop + 2 + r.f() * 14, 3.5f, 1.6f, r.f() * 3, c.olive)

        b.rect(0f, floorTop, W, H - floorTop, c.floor)
        var x = -200f
        while (x < W + 200) { b.line(1f, c.floorLine, x, floorTop, x + (x - W / 2) * 0.9f, H); x += 26 }
        var y = floorTop + 14; var step = 14f
        while (y < H) { b.line(1f, c.floorLine, 0f, y, W, y); step *= 1.18f; y += step }

        // 파라솔
        val px = 112f; val ptop = floorTop - 92
        b.line(3f, c.wood, px, ptop, px + 6, floorTop + 38)
        for (s in 0 until 8) {
            val a0 = M.PI + s * M.PI / 8; val a1 = a0 + M.PI / 8
            b.wedge(px, ptop, px, ptop + 4, 66f, 26f, a0, a1, if (s % 2 == 1) c.white else c.stripe)
        }
        b.ellipse(px + 30, floorTop + 52, 70f, 9f, 0f, 0x1A000000)

        // 선베드
        val sy = floorTop + 46
        b.line(7f, c.white, 62f, sy - 40, 84f, sy, 200f, sy)
        b.line(2f, c.stripe, 62f, sy - 40, 84f, sy, 200f, sy)
        b.line(2.5f, c.wood, 90f, sy, 93f, sy + 10)
        b.line(2.5f, c.wood, 192f, sy, 195f, sy + 10)

        // "나": 얼굴 없는 납작한 도형
        val hipX = 100f; val hipY = sy - 9; val shX = 76f; val shY = sy - 36
        b.line(11f, c.skin, hipX, hipY, 140f, sy - 20)
        b.line(10f, c.skin, 140f, sy - 20, 184f, sy - 9)
        b.line(15f, c.navy, hipX - 2, hipY, 118f, sy - 13)
        b.line(22f, c.white, hipX, hipY, shX, shY)
        for (k in 0 until 4) {
            val t = 0.2f + k * 0.2f
            val mx = hipX + (shX - hipX) * t; val my = hipY + (shY - hipY) * t
            b.line(1.6f, c.stripe, mx - 9, my - 6, mx + 9, my + 6)
        }
        b.circle(shX - 6, shY - 13, 9f, c.skin)
        b.ellipse(shX - 6, shY - 18, 15f, 4f, -0.25f, c.lemon)
        b.wedge(shX - 6, shY - 19, shX - 6, shY - 19, 7f, 7f, M.PI, 2 * M.PI, c.lemon)
        when (inp.pose) {
            Pose.GUITAR -> {
                b.line(7f, c.skin, shX + 4, shY + 6, 108f, sy - 26)
                b.line(3.2f, 0xFF3A2A1E.toInt(), 104f, sy - 28, 138f, sy - 58)
                b.ellipse(98f, sy - 22, 15f, 11f, -0.6f, 0xFFB56E3C.toInt())
                b.ellipse(108f, sy - 32, 10f, 8f, -0.6f, 0xFFB56E3C.toInt())
                b.circle(103f, sy - 27, 3f, 0xFF3A2A1E.toInt())
                b.line(6.4f, c.skin, shX + 2, shY + 2, 126f, sy - 48)
            }
            Pose.BOOK -> {
                b.line(7f, c.skin, shX + 4, shY + 6, 104f, shY - 2)
                // 책: 기울어진 직사각형(-0.5 라디안)
                val bcx = 108f; val bcy = shY - 8
                fun rot(dx: Float, dy: Float): Pair<Float, Float> {
                    val a = -0.5f
                    return Pair(bcx + dx * M.cos(a) - dy * M.sin(a), bcy + dx * M.sin(a) + dy * M.cos(a))
                }
                val p = listOf(rot(-12f, -9f), rot(12f, -9f), rot(12f, 8f), rot(-12f, 8f))
                b.poly(c.clay, p[0].first, p[0].second, p[1].first, p[1].second, p[2].first, p[2].second, p[3].first, p[3].second)
                val q = listOf(rot(-1f, -8f), rot(1f, -8f), rot(1f, 7f), rot(-1f, 7f))
                b.poly(c.white, q[0].first, q[0].second, q[1].first, q[1].second, q[2].first, q[2].second, q[3].first, q[3].second)
            }
        }

        // 화분들(오른쪽)
        val by = floorTop + 58
        val spots = listOf(232f to by, 272f to by + 4, 304f to by + 2)
        inp.plants.take(3).forEachIndexed { i, (sp, gr) ->
            val (sx, sby) = spots[i]
            PlantArt.draw(b, sx, sby, if (i == 0) 1.05f else 0.85f, inp.seed * 7 + i, sp, gr, c)
        }

        // 테이블과 정물(저절로 놓인다)
        val tx = 206f; val ty = floorTop + 22
        b.line(2.5f, c.wood, tx, ty, tx, ty + 30)
        b.ellipse(tx, ty, 26f, 6f, 0f, c.white)
        val items: List<() -> Unit> = listOf(
            { b.rect(tx - 20, ty - 17, 8f, 15f, 0xD9B4D7EB.toInt()); b.circle(tx - 13, ty - 17, 3.6f, c.lemon) },
            { b.circle(tx - 3, ty - 5, 5f, 0xFFF2A07B.toInt()); b.ellipse(tx - 1, ty - 11, 3f, 1.4f, 0.6f, c.olive) },
            { b.ellipse(tx + 7, ty - 3, 3.2f, 3.2f, 0f, c.navy); b.ellipse(tx + 14, ty - 3, 3.2f, 3.2f, 0f, c.navy); b.line(1.6f, c.navy, tx + 10.2f, ty - 3, tx + 10.8f, ty - 3) },
            { b.rect(tx + 6, ty - 12, 16f, 4f, c.olive); b.rect(tx + 7, ty - 16, 14f, 4f, c.clay) },
            { b.rect(tx - 9, ty - 26, 12f, 8f, c.navy); b.circle(tx - 3, ty - 22, 2.6f, 0xFF9FB4C8.toInt()) },
            { b.rect(tx + 12, ty - 26, 9f, 13f, 0xFFE7E2D6.toInt()); b.rect(tx + 12, ty - 22, 9f, 2f, c.stripe) },
        )
        for (o in 0 until minOf(inp.objects, items.size)) items[o]()
        return b.build()
    }
}

/** 연간 격자(53주 × 7일, 월요일 시작). minutes[i] = 첫 칸부터 i번째 날의 분, 음수 = 아직 오지 않은 날. */
object GridArt {
    val OLIVE_RAMP = intArrayOf(0xFFE6E4D8.toInt(), 0xFFCFD8B4.toInt(), 0xFFA9BE7E.toInt(), 0xFF7E9A4E.toInt(), 0xFF5E7D3A.toInt())

    fun level(min: Int) = when { min <= 0 -> 0; min < 15 -> 1; min < 45 -> 2; min < 120 -> 3; else -> 4 }

    fun build(minutes: IntArray, weeks: Int = 53, ramp: IntArray = OLIVE_RAMP): Drawing {
        val cell = 10f; val gap = 2f
        val W = weeks * (cell + gap) - gap; val H = 7 * (cell + gap) - gap
        val b = DrawBuilder(W, H)
        for (w in 0 until weeks) for (d in 0 until 7) {
            val i = w * 7 + d
            if (i >= minutes.size || minutes[i] < 0) continue
            b.ops += RoundRect(w * (cell + gap), d * (cell + gap), cell, cell, 2.2f, ramp[level(minutes[i])])
        }
        return b.build()
    }
}
