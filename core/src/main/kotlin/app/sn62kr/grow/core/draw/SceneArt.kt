package app.sn62kr.grow.core.draw

import app.sn62kr.grow.core.garden.Species

/** "나"의 자세(D57). 지금(또는 그 세션의) 활동을 따른다. 얼굴은 그리지 않는다. */
enum class Pose { REST, BOOK, GUITAR, SKETCH, KNIT;

    companion object {
        /** 활동의 아이콘 키(첫 실행 예시 키) → 자세. 모르는 키는 쉬는 자세. */
        fun of(iconKey: String?): Pose = when (iconKey) {
            "reading", "writing", "study", "language" -> BOOK
            "instrument" -> GUITAR
            "drawing", "photo" -> SKETCH
            "knitting" -> KNIT
            else -> REST
        }
    }
}

/** 화분 하나: 종, 씨앗, 자란 정도. */
data class PlantSpot(val species: Species, val seed: Long, val growth: Float)

/** 장면의 입력. 그림은 저장하지 않고 이 값으로 매번 다시 그린다(D43). */
data class SceneInput(
    val seed: Long,
    val pose: Pose,
    /** 놓인 정물 수(쌓인 시간이 정한다, Garden.stillLife). */
    val objects: Int,
    /** 첫째가 지금 키우는 식물, 그다음은 다 자란 식물(최근 것부터). 셋까지 보인다. */
    val plants: List<PlantSpot>,
    /** 정사각형(폴라로이드). */
    val square: Boolean = false,
    /** 다크 모드 = 저녁. */
    val dusk: Boolean = false,
)

/** 테라스 장면: 하늘·바다·회벽·바닥·파라솔·선베드·"나"·화분·정물(D57). */
object SceneArt {
    const val W = 320f
    fun height(square: Boolean) = if (square) 320f else 240f

    /** 정물 종류 수. 넘으면 다 놓인 채로 있다. */
    const val OBJECT_KINDS = 12

    fun build(inp: SceneInput): Drawing {
        val H = height(inp.square)
        val b = DrawBuilder(W, H)
        val c = if (inp.dusk) Palette.DUSK else Palette.DAY
        val r = Rng(inp.seed)
        b.clip(0f, 0f, W, H)
        val oy = if (inp.square) 70f else 0f
        val horizon = 92f + oy; val wallTop = 118f + oy; val floorTop = 150f + oy

        // 하늘·해·바다·돛배
        b.gradient(0f, 0f, W, horizon, c.skyTop, c.skyBottom)
        b.circle(258f, horizon - 44f - oy * 0.4f, 17f, c.sun)
        b.rect(0f, horizon, W, wallTop - horizon, c.sea)
        for (i in 0 until 9) {
            val wx = r.f() * W; val wy = horizon + 5 + r.f() * (wallTop - horizon - 9)
            b.line(1.2f, 0x73FFFFFF, wx, wy, wx + 8 + r.f() * 10, wy)
        }
        val bx = 150 + r.f() * 70
        b.poly(c.white, bx, horizon + 6, bx + 9, horizon + 6, bx + 4, horizon - 9)

        // 회벽 난간과 부겐빌레아
        b.rect(0f, wallTop, W, floorTop - wallTop, c.wall)
        b.rect(0f, floorTop - 4, W, 4f, 0x0F000000)
        for (i in 0 until 26) b.circle(4 + r.f() * 70, wallTop - 4 + r.f() * 18, 2.4f + r.f() * 2.4f, if (r.f() > 0.5f) 0xFFC2307A.toInt() else 0xFFD9529A.toInt())
        for (i in 0 until 10) b.ellipse(6 + r.f() * 64, wallTop + 2 + r.f() * 14, 3.5f, 1.6f, r.f() * 3, c.olive)

        // 바닥 타일
        b.rect(0f, floorTop, W, H - floorTop, c.floor)
        var x = -200f
        while (x < W + 200) { b.line(1f, c.floorLine, x, floorTop, x + (x - W / 2) * 0.9f, H); x += 26 }
        var y = floorTop + 14; var step = 14f
        while (y < H) { b.line(1f, c.floorLine, 0f, y, W, y); step *= 1.18f; y += step }

        parasol(b, c, floorTop)
        val sy = floorTop + 46
        lounger(b, c, sy)
        me(b, c, sy, inp.pose)
        table(b, c, r.fork(5), floorTop, inp.objects)
        pots(b, c, inp, floorTop)
        return b.build()
    }

    private fun parasol(b: DrawBuilder, c: Palette, floorTop: Float) {
        val px = 112f; val ptop = floorTop - 92
        b.line(3f, c.wood, px, ptop, px + 6, floorTop + 38)
        for (s in 0 until 8) {
            val a0 = M.PI + s * M.PI / 8; val a1 = a0 + M.PI / 8
            b.wedge(px, ptop, px, ptop + 4, 66f, 26f, a0, a1, if (s % 2 == 1) c.white else c.stripe)
        }
        b.ellipse(px + 30, floorTop + 52, 70f, 9f, 0f, c.shade)
    }

    private fun lounger(b: DrawBuilder, c: Palette, sy: Float) {
        b.line(7f, c.white, 62f, sy - 40, 84f, sy, 200f, sy)
        b.line(2f, c.stripe, 62f, sy - 40, 84f, sy, 200f, sy)
        b.line(2.5f, c.wood, 90f, sy, 93f, sy + 10)
        b.line(2.5f, c.wood, 192f, sy, 195f, sy + 10)
    }

    /** "나": 얼굴 없는 납작한 도형. 몸은 같고 팔과 든 것이 자세마다 다르다. */
    private fun me(b: DrawBuilder, c: Palette, sy: Float, pose: Pose) {
        val hipX = 100f; val hipY = sy - 9; val shX = 76f; val shY = sy - 36
        // 다리(무릎을 세운 자세는 뜨개질·그림)
        val knee = pose == Pose.KNIT || pose == Pose.SKETCH
        if (knee) {
            b.line(11f, c.skin, hipX, hipY, 128f, sy - 34); b.line(10f, c.skin, 128f, sy - 34, 160f, sy - 9)
        } else {
            b.line(11f, c.skin, hipX, hipY, 140f, sy - 20); b.line(10f, c.skin, 140f, sy - 20, 184f, sy - 9)
        }
        b.line(15f, c.navy, hipX - 2, hipY, 118f, sy - 13)
        // 줄무늬 셔츠
        b.line(22f, c.white, hipX, hipY, shX, shY)
        for (k in 0 until 4) {
            val t = 0.2f + k * 0.2f
            val mx = hipX + (shX - hipX) * t; val my = hipY + (shY - hipY) * t
            b.line(1.6f, c.stripe, mx - 9, my - 6, mx + 9, my + 6)
        }
        // 머리와 밀짚모자
        b.circle(shX - 6, shY - 13, 9f, c.skin)
        b.ellipse(shX - 6, shY - 18, 15f, 4f, -0.25f, c.lemon)
        b.wedge(shX - 6, shY - 19, shX - 6, shY - 19, 7f, 7f, M.PI, 2 * M.PI, c.lemon)
        b.line(1.4f, c.stripe, shX - 13, shY - 19, shX + 1, shY - 22)
        when (pose) {
            Pose.REST -> {
                // 팔을 머리 뒤로, 옆에 레모네이드
                b.line(7f, c.skin, shX + 2, shY + 4, shX - 14, shY - 6)
                b.line(6f, c.skin, shX - 14, shY - 6, shX - 4, shY - 20)
                b.line(7f, c.skin, shX + 6, shY + 8, 104f, sy - 14)
            }
            Pose.BOOK -> {
                b.line(7f, c.skin, shX + 4, shY + 6, 104f, shY - 2)
                b.rotRect(108f, shY - 8, 24f, 17f, -0.5f, c.clay)
                b.rotRect(108f, shY - 8, 2f, 15f, -0.5f, c.white)
            }
            Pose.GUITAR -> {
                b.line(7f, c.skin, shX + 4, shY + 6, 108f, sy - 26)
                b.line(3.2f, 0xFF3A2A1E.toInt(), 104f, sy - 28, 138f, sy - 58)
                b.ellipse(98f, sy - 22, 15f, 11f, -0.6f, 0xFFB56E3C.toInt())
                b.ellipse(108f, sy - 32, 10f, 8f, -0.6f, 0xFFB56E3C.toInt())
                b.circle(103f, sy - 27, 3f, 0xFF3A2A1E.toInt())
                b.line(6.4f, c.skin, shX + 2, shY + 2, 126f, sy - 48)
            }
            Pose.SKETCH -> {
                // 무릎에 기댄 스케치북과 연필
                b.rotRect(118f, sy - 44, 26f, 19f, -0.9f, c.white)
                b.rotRect(118f, sy - 44, 26f, 19f, -0.9f, 0x10000000)
                b.line(1.2f, c.stripe, 112f, sy - 46, 120f, sy - 50, 124f, sy - 42)
                b.line(7f, c.skin, shX + 4, shY + 6, 110f, sy - 38)
                b.line(2f, c.lemon, 110f, sy - 38, 116f, sy - 47)
            }
            Pose.KNIT -> {
                // 두 바늘과 실타래, 무릎 위의 뜨개
                b.line(7f, c.skin, shX + 4, shY + 6, 108f, sy - 30)
                b.line(1.4f, c.wood, 98f, sy - 40, 118f, sy - 26)
                b.line(1.4f, c.wood, 102f, sy - 24, 120f, sy - 40)
                b.ellipse(110f, sy - 30, 9f, 5f, 0.2f, 0xFFD9529A.toInt())
                b.circle(140f, sy - 3, 6f, 0xFFD9529A.toInt())
                b.line(1f, 0xFFB83A7E.toInt(), 136f, sy - 6, 144f, sy, 140f, sy - 9)
                b.line(0.8f, 0xFFD9529A.toInt(), 116f, sy - 30, 136f, sy - 4)
            }
        }
    }

    /** 테이블과 정물. 놓이는 순서는 씨앗으로 섞은 차례(같은 정원이면 늘 같은 순서). */
    private fun table(b: DrawBuilder, c: Palette, r: Rng, floorTop: Float, objects: Int) {
        val tx = 206f; val ty = floorTop + 22
        b.line(2.5f, c.wood, tx, ty, tx, ty + 30)
        b.line(2f, c.wood, tx - 8, ty + 30, tx + 8, ty + 30)
        b.ellipse(tx, ty, 26f, 6f, 0f, c.white)
        val order = r.shuffled(OBJECT_KINDS)
        val n = objects.coerceIn(0, OBJECT_KINDS)
        // 자리: 테이블 위 다섯, 바닥 넷, 난간 위 셋
        for (k in 0 until n) item(b, c, order[k], k, tx, ty, floorTop)
    }

    private fun item(b: DrawBuilder, c: Palette, kind: Int, slot: Int, tx: Float, ty: Float, floorTop: Float) {
        val (ix, iy) = when (slot) {
            0 -> tx - 14 to ty - 2; 1 -> tx + 2 to ty - 2; 2 -> tx + 14 to ty - 1; 3 -> tx - 5 to ty - 4; 4 -> tx + 9 to ty - 5
            5 -> 196f to floorTop + 62; 6 -> 46f to floorTop + 66; 7 -> 30f to floorTop + 50; 8 -> 214f to floorTop + 74
            9 -> 136f to floorTop - 4; 10 -> 152f to floorTop - 4; 11 -> 200f to floorTop - 4
            else -> tx to ty
        }
        when (kind) {
            0 -> { b.rect(ix - 4, iy - 15, 8f, 15f, 0xD9B4D7EB.toInt()); b.rect(ix - 4, iy - 9, 8f, 9f, 0xCCF6E27A.toInt()); b.circle(ix + 3, iy - 15, 3.4f, c.lemon) } // 레모네이드
            1 -> { b.ellipse(ix, iy - 1, 8f, 2f, 0f, c.white); b.circle(ix - 3, iy - 5, 4.2f, 0xFFF2A07B.toInt()); b.circle(ix + 3, iy - 4.5f, 3.8f, 0xFFF0B080.toInt()) } // 복숭아 접시
            2 -> { b.ellipse(ix - 4, iy - 3, 3.4f, 2.8f, 0f, c.navy); b.ellipse(ix + 4, iy - 3, 3.4f, 2.8f, 0f, c.navy); b.line(1.4f, c.navy, ix - 1, iy - 3, ix + 1, iy - 3) } // 선글라스
            3 -> { b.rect(ix - 8, iy - 4, 16f, 4f, c.olive); b.rect(ix - 7, iy - 8, 14f, 4f, c.clay); b.rect(ix - 6, iy - 11.5f, 12f, 3.5f, c.stripe) } // 책 더미
            4 -> { b.roundRect(ix - 7, iy - 9, 14f, 9f, 2f, c.navy); b.circle(ix, iy - 4.5f, 2.8f, 0xFF9FB4C8.toInt()); b.rect(ix + 3, iy - 11, 3f, 2f, c.navy) } // 필름 카메라
            5 -> { b.roundRect(ix - 2.5f, iy - 12, 5f, 12f, 1.5f, 0xFFF4F0E4.toInt()); b.rect(ix - 2.5f, iy - 8, 5f, 3f, 0xFFF29A3A.toInt()) } // 선크림
            6 -> { b.ellipse(ix, iy - 4, 9f, 4.5f, 0f, 0xFF2F5F9E.toInt()); for (k in 0 until 3) b.circle(ix - 4 + k * 4, iy - 7, 2.8f, c.lemon) } // 레몬 그릇
            7 -> { b.poly(0xFF2F6DB5.toInt(), ix - 5, iy, ix + 5, iy, ix + 3, iy - 14, ix - 3, iy - 14); b.rect(ix - 2, iy - 17, 4f, 3f, 0xFF2F6DB5.toInt()); b.line(1.4f, c.white, ix - 4, iy - 7, ix + 4, iy - 7) } // 물병
            8 -> { b.wedge(ix, iy, ix, iy, 6f, 5f, M.PI, 2 * M.PI, 0xFFF3D9C4.toInt()); for (k in 0 until 4) b.line(0.7f, 0xFFD9A98A.toInt(), ix, iy, ix - 5 + k * 3.3f, iy - 4) } // 조개
            9 -> { b.roundRect(ix - 3, iy - 9, 6f, 9f, 2f, 0xFFF2EEE6.toInt()); b.line(0.9f, 0xFF5F7A42.toInt(), ix, iy - 9, ix - 1, iy - 15); b.circle(ix - 1, iy - 16, 2.2f, 0xFFD8323C.toInt()) } // 꽃병
            10 -> { b.rotRect(ix, iy - 5, 13f, 9f, -0.2f, 0xFFFFFDF2.toInt()); b.rotRect(ix - 2, iy - 6, 5f, 4f, -0.2f, c.sea) } // 엽서
            11 -> { b.ellipse(ix, iy - 2, 7f, 2.4f, 0f, 0xFFE9D9A0.toInt()); b.wedge(ix, iy - 2, ix, iy - 2, 4.5f, 4f, M.PI, 2 * M.PI, 0xFFE9D9A0.toInt()); b.line(1f, c.stripe, ix - 4, iy - 3, ix + 4, iy - 3) } // 밀짚모자 하나 더(손님용)
        }
    }

    private fun pots(b: DrawBuilder, c: Palette, inp: SceneInput, floorTop: Float) {
        val by = floorTop + 60
        // 지금 식물이 가장 크고 앞에. 다 자란 것은 뒤에 작게, 셋째는 난간 위(오른쪽 끝에서 잘렸다).
        val spots = listOf(Triple(248f, by + 6, 1.05f), Triple(290f, by - 6, 0.8f), Triple(176f, floorTop - 3, 0.55f))
        val shown = inp.plants.take(3)
        for (i in shown.indices.reversed()) {
            val (sx, sby, sc) = spots[i]
            val p = shown[i]
            PlantArt.draw(b, sx, sby, sc, p.seed, p.species, p.growth, c)
        }
    }
}
