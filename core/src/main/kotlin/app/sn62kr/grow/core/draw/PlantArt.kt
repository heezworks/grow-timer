package app.sn62kr.grow.core.draw

import app.sn62kr.grow.core.garden.Species

/**
 * 화분 식물(D66: 조립식 + 시간 붙은 성장). 부품(줄기·가지·잎·꽃·열매)마다 씨앗 난수로 "돋는 때"(0~1)를 정하고,
 * growth가 그 때를 지나면 짧은 구간에 걸쳐 0 → 제 크기로 자란다. 같은 (seed, species, growth)면 언제나 같은 그리기 목록.
 * **난수는 그리든 안 그리든 같은 순서로 꺼낸다** — 그래야 자라는 동안 잎이 제자리에 있다(S2에서 배움).
 * 좌표: (x, y) = 화분 바닥 가운데, 위가 -y. s = 크기 배율(화분 높이 약 20s, 다 자란 키 약 70s).
 */
object PlantArt {
    private const val BARK = 0xFF7A5E44.toInt()
    private const val BARK_DARK = 0xFF5E4734.toInt()
    private const val SOIL = 0xFF5A4232.toInt()

    fun draw(b: DrawBuilder, x: Float, y: Float, s: Float, seed: Long, sp: Species, growth: Float, pal: Palette) {
        val g = M.clamp01(growth)
        val r = Rng(seed * 31 + sp.ordinal)
        val potW = r.f(24f, 30f) * s
        val potH = r.f(18f, 22f) * s
        val potStyle = intArrayOf(1, 2, 0, 0)[r.i(4)] // 0 테라코타(절반) · 1 흰 칠 · 2 파란 유약
        b.ellipse(x, y + 1.5f * s, potW * 0.8f, 3f * s, 0f, pal.shade)
        pot(b, x, y, potW, potH, s, potStyle, pal, r.fork(9))
        val top = y - potH - 3f * s
        // 흙은 식물 뒤에 먼저
        b.ellipse(x, top + 1.5f * s, potW * 0.45f, 2.4f * s, 0f, SOIL)
        if (g <= 0f) { seedMark(b, x, top, s); return }
        when (sp) {
            Species.OLIVE -> olive(b, x, top, s, g, r.fork(1), pal)
            Species.LEMON -> lemon(b, x, top, s, g, r.fork(2), pal)
            Species.LAVENDER -> lavender(b, x, top, s, g, r.fork(3), potW)
            Species.GERANIUM -> geranium(b, x, top, s, g, r.fork(4), potW)
        }
    }

    private fun grow(g: Float, birth: Float, span: Float = 0.07f) = M.easeOut((g - birth) / span)

    /** 아직 아무것도 안 났을 때: 흙 위의 씨앗 하나(홈 빈 상태 "첫 시간을 재면 싹이 나요"). */
    private fun seedMark(b: DrawBuilder, x: Float, top: Float, s: Float) {
        b.ellipse(x, top + 0.3f * s, 2.2f * s, 1.5f * s, 0.3f, 0xFF8A6A3E.toInt())
        b.ellipse(x - 0.6f * s, top - 0.2f * s, 0.8f * s, 0.5f * s, 0.3f, 0x55FFFFFF)
    }

    /** 화분 셋: 테라코타 / 흰 칠 / 파란 유약. */
    private fun pot(b: DrawBuilder, cx: Float, by: Float, w: Float, h: Float, s: Float, style: Int, pal: Palette, r: Rng) {
        val taper = r.f(0.32f, 0.40f)
        val body = when (style) { 0 -> pal.clay; 1 -> 0xFFE9E2D4.toInt(); else -> 0xFF2F5F9E.toInt() }
        val rim = when (style) { 0 -> M.mix(pal.clay, 0xFFFFFFFF.toInt(), 0.12f); 1 -> 0xFFFFFFFF.toInt(); else -> 0xFF3A6FB0.toInt() }
        b.poly(body, cx - w / 2, by - h, cx + w / 2, by - h, cx + w * taper, by, cx - w * taper, by)
        // 그늘 한 줄(오른쪽)
        b.poly(if (style == 1) 0x33000000 else 0x22000000, cx + w * 0.18f, by - h, cx + w / 2, by - h, cx + w * taper, by, cx + w * 0.12f, by)
        if (style == 2) b.line(1.6f * s, 0xFFF2EEE6.toInt(), cx - w * 0.45f, by - h * 0.55f, cx + w * 0.45f, by - h * 0.55f) // 흰 띠
        if (style == 1) b.line(1.4f * s, 0xFF2F6DB5.toInt(), cx - w * 0.44f, by - h * 0.7f, cx + w * 0.44f, by - h * 0.7f) // 흰 칠 위 파란 줄
        b.rect(cx - w / 2 - 2f * s, by - h - 4.5f * s, w + 4f * s, 5f * s, rim)
        b.rect(cx - w / 2 - 2f * s, by - h - 0.5f * s, w + 4f * s, 1f * s, 0x22000000)
    }

    // 올리브: 짧고 비틀린 줄기가 낮게 갈라져 굵은 가지 셋, 가지 끝마다 가늘고 은빛 도는 잎 덩이. 늦게 검보라 열매.
    private fun olive(b: DrawBuilder, x: Float, top: Float, s: Float, g: Float, r: Rng, pal: Palette) {
        val tg = M.easeOut(g / 0.95f)
        val trunkH = M.lerp(6f, 26f, tg) * s
        val lean = r.f(-5f, 5f) * s
        val fx = x + lean; val fy = top - trunkH
        // 줄기: 두 가닥이 꼬인 듯(굵은 선 + 가는 어두운 선)
        b.quad(M.lerp(1.6f, 6.5f, tg) * s, BARK, x, top, x - lean * 1.2f, top - trunkH * 0.5f, fx, fy)
        b.quad(M.lerp(0.4f, 1.6f, tg) * s, BARK_DARK, x + 1f * s, top, x - lean * 0.4f + 1.5f * s, top - trunkH * 0.55f, fx + 0.8f * s, fy)

        val limbs = 3
        val tipX = FloatArray(limbs); val tipY = FloatArray(limbs); val midX = FloatArray(limbs); val midY = FloatArray(limbs)
        for (i in 0 until limbs) {
            val birth = i * 0.12f + r.f(0f, 0.06f)
            val lg = grow(g, birth, 0.45f)
            val ang = -M.PI / 2 + (i - 1) * r.f(0.55f, 0.85f) + r.f(-0.15f, 0.15f)
            val len = r.f(20f, 30f) * s * lg
            val ex = fx + M.cos(ang) * len; val ey = fy + M.sin(ang) * len
            val bend = r.f(-6f, 6f) * s * lg
            val cxp = (fx + ex) / 2 + bend; val cyp = (fy + ey) / 2 - 2f * s
            if (lg > 0f) b.quad(M.lerp(1f, 3.6f, lg) * s, BARK, fx, fy, cxp, cyp, ex, ey, 10)
            tipX[i] = ex; tipY[i] = ey
            val m = b.quadAt(fx, fy, cxp, cyp, ex, ey, 0.55f); midX[i] = m.first; midY[i] = m.second
        }
        // 잎 덩이 여섯(가지 끝 셋 + 가운데 셋). 덩이 크기도 자란다.
        val leafCols = intArrayOf(pal.olive, 0xFF8A9A62.toInt(), 0xFF56683A.toInt(), 0xFFA9B58C.toInt())
        val n = 120
        for (i in 0 until n) {
            val birth = if (i < 3) 0f else (i / n.toFloat()) * 0.92f + r.f(0f, 0.06f)
            val lg = grow(g, birth)
            val c = r.i(6)
            val cx = if (c < 3) tipX[c] else midX[c - 3]; val cy = if (c < 3) tipY[c] else midY[c - 3]
            val rad = M.lerp(4f, 13f, tg) * s
            val a = r.f(0f, 2 * M.PI); val d = M.sqrt(r.f()) * rad
            val px = cx + M.cos(a) * d; val py = cy + M.sin(a) * d * 0.7f
            val rot = r.f(-0.9f, 0.9f) + if (i % 2 == 0) 0.35f else -0.35f
            val col = leafCols[r.i(4)]
            if (lg > 0f) b.ellipse(px, py, 5.2f * s * lg, 1.5f * s * lg, rot, col)
        }
        val nFruit = 6 + r.i(5)
        for (i in 0 until nFruit) {
            val birth = r.f(0.84f, 0.98f)
            val fg = grow(g, birth, 0.05f)
            val c = r.i(3)
            val px = tipX[c] + r.f(-8f, 8f) * s; val py = tipY[c] + r.f(-4f, 7f) * s
            if (fg > 0f) b.ellipse(px, py, 1.9f * s * fg, 1.4f * s * fg, 0.3f, if (i % 2 == 0) 0xFF3B2F4A.toInt() else 0xFF5B4A6B.toInt())
        }
    }

    // 레몬: 곧은 줄기, 둥글고 짙은 녹색 수관(빛 받는 쪽이 밝다), 흰 꽃 → 노란 열매.
    private fun lemon(b: DrawBuilder, x: Float, top: Float, s: Float, g: Float, r: Rng, pal: Palette) {
        val tg = M.easeOut(g / 0.85f)
        val h = M.lerp(6f, 30f, tg) * s
        val tx = x + r.f(-3f, 3f) * s
        b.quad(M.lerp(1.6f, 4f, tg) * s, BARK, x, top, x + r.f(-4f, 4f) * s, top - h * 0.5f, tx, top - h)
        val rad = M.lerp(4f, 25f, M.easeOut(g / 0.98f)) * s
        val ccx = tx; val ccy = top - h - rad * 0.35f
        b.line(M.lerp(1.2f, 2.8f, tg) * s, BARK, tx, top - h, ccx, ccy) // 줄기가 수관 안까지 이어진다(S2: 잎이 떠 보였다)
        val greens = intArrayOf(0xFF3F6B34.toInt(), 0xFF4E7D3E.toInt(), 0xFF2F5A2A.toInt(), 0xFF6A9650.toInt())
        val n = 90
        for (i in 0 until n) {
            val rr = M.sqrt(r.f()) * if (i < 3) 0.3f else 1f
            val a = r.f(0f, 2 * M.PI)
            val birth = if (i < 3) 0f else M.clamp01(rr * rr * 0.8f + r.f(0f, 0.2f)) // 안쪽 잎부터
            val lg = grow(g, birth)
            val px = ccx + M.cos(a) * rr * rad * 1.1f
            val py = ccy + M.sin(a) * rr * rad * 0.85f
            val rot = r.f(0f, M.PI)
            // 왼쪽 위(해 쪽)는 밝은 잎이 많다
            val lit = M.cos(a + 2.2f) * rr
            val c = if (lit > 0.35f) greens[3] else greens[r.i(3)]
            if (lg > 0f) b.ellipse(px, py, 4.4f * s * lg, 2.6f * s * lg, rot, c)
        }
        for (i in 0 until 8) {
            val birth = r.f(0.55f, 0.75f)
            val a = r.f(0f, 2 * M.PI); val rr = M.sqrt(r.f()) * 0.9f
            val fg = grow(g, birth, 0.04f) * (1 - grow(g, 0.9f, 0.08f))
            if (fg > 0f) b.circle(ccx + M.cos(a) * rr * rad, ccy + M.sin(a) * rr * rad * 0.85f, 1.6f * s * fg, 0xFFFFFDF2.toInt())
        }
        val nFruit = 4 + r.i(5)
        for (i in 0 until nFruit) {
            val birth = r.f(0.74f, 0.97f)
            val a = r.f(0f, 2 * M.PI); val rr = M.sqrt(r.f()) * 0.88f
            val fg = grow(g, birth, 0.06f)
            val rot = r.f(-0.4f, 0.4f)
            val fx = ccx + M.cos(a) * rr * rad; val fy = ccy + M.sin(a) * rr * rad * 0.85f
            if (fg > 0f) {
                b.ellipse(fx, fy, 3.5f * s * fg, 2.7f * s * fg, rot, pal.lemon)
                b.ellipse(fx - 1f * s * fg, fy - 0.9f * s * fg, 1.1f * s * fg, 0.7f * s * fg, rot, 0x66FFFFFF)
            }
        }
    }

    // 라벤더: 부채꼴로 퍼진 가는 줄기, 아래 은녹색 잎, 위 보라 꽃 이삭.
    private fun lavender(b: DrawBuilder, x: Float, top: Float, s: Float, g: Float, r: Rng, potW: Float) {
        val n = 11 + r.i(5)
        val sage = intArrayOf(0xFF8FA588.toInt(), 0xFF7A9474.toInt(), 0xFFA3B59B.toInt())
        for (i in 0 until 18) {
            val birth = if (i < 3) 0f else r.f(0f, 0.35f)
            val lg = grow(g, birth, 0.1f)
            val lx = x + r.f(-0.32f, 0.32f) * potW; val ly = top - r.f(1f, 7f) * s
            val rot = r.f(-0.9f, 0.9f); val col = sage[r.i(3)]
            if (lg > 0f) b.ellipse(lx, ly, 4.4f * s * lg, 1.5f * s * lg, rot, col)
        }
        val purples = intArrayOf(0xFF8B6CC2.toInt(), 0xFF7A5BB0.toInt(), 0xFF9D80D0.toInt())
        for (i in 0 until n) {
            val birth = if (i == 0) 0.02f else r.f(0.02f, 0.45f)
            val sg = grow(g, birth, 0.25f)
            val bx = x + (i - n / 2f) / n * potW * 0.6f
            val ang = -M.PI / 2 + (i - n / 2f) / n * 0.95f + r.f(-0.12f, 0.12f)
            val len = r.f(26f, 40f) * s * sg
            val ex = bx + M.cos(ang) * len; val ey = top + M.sin(ang) * len
            val bend = r.f(-1f, 1f) * s
            if (sg > 0f) b.line(1.1f * s, 0xFF6F8A55.toInt(), bx, top, (bx + ex) / 2 + bend, (top + ey) / 2, ex, ey)
            val fg = grow(g, r.f(0.45f, 0.9f), 0.08f)
            for (k in 0 until 7) {
                val t = 1f - k * 0.05f
                val px = M.lerp(bx, ex, t) + r.f(-1.2f, 1.2f) * s
                val py = M.lerp(top, ey, t)
                val col = purples[r.i(3)]
                if (fg > 0f && sg > 0.6f) b.ellipse(px, py, 1.5f * s * fg, 1.9f * s * fg, ang + M.PI / 2, col)
            }
        }
    }

    // 제라늄: 낮고 둥근 잎 무더기(가장자리가 진한 둥근 잎), 그 위로 긴 꽃대 끝에 붉은 꽃송이.
    private fun geranium(b: DrawBuilder, x: Float, top: Float, s: Float, g: Float, r: Rng, potW: Float) {
        val mound = M.easeOut(g / 0.8f)
        val leafG = intArrayOf(0xFF4F7A3A.toInt(), 0xFF5F8C45.toInt(), 0xFF436B32.toInt())
        val n = 34
        for (i in 0 until n) {
            val birth = if (i < 2) 0f else (i / n.toFloat()) * 0.55f + r.f(0f, 0.05f)
            val lg = grow(g, birth, 0.12f)
            val a = r.f(M.PI, 2 * M.PI) // 위쪽 반원
            val d = M.sqrt(r.f()) * M.lerp(4f, potW * 0.62f, mound)
            val px = x + M.cos(a) * d; val py = top - 2f * s + M.sin(a) * d * 0.55f
            val rr = r.f(3.4f, 4.6f) * s * lg
            val c = leafG[r.i(3)]
            if (lg > 0f) {
                b.circle(px, py, rr, c)
                b.circle(px, py, rr * 0.55f, M.mix(c, 0xFF2E4A22.toInt(), 0.35f)) // 잎 가운데 짙은 고리
                b.circle(px, py, rr * 0.35f, c)
            }
        }
        val reds = intArrayOf(0xFFD8323C.toInt(), 0xFFE8505A.toInt(), 0xFFC22630.toInt())
        val stems = 3 + r.i(3)
        for (i in 0 until stems) {
            val birth = r.f(0.5f, 0.75f)
            val sg = grow(g, birth, 0.15f)
            val bx = x + r.f(-0.25f, 0.25f) * potW
            val ang = -M.PI / 2 + r.f(-0.5f, 0.5f)
            val len = r.f(20f, 30f) * s * sg
            val ex = bx + M.cos(ang) * len; val ey = top - 4f * s + M.sin(ang) * len
            val bend = r.f(-2f, 2f) * s
            if (sg > 0f) b.quad(0.9f * s, 0xFF5F7A42.toInt(), bx, top - 3f * s, (bx + ex) / 2 + bend, (top + ey) / 2, ex, ey, 8)
            val fg = grow(g, birth + 0.12f, 0.1f)
            if (sg > 0.3f) b.circle(ex, ey, 2.6f * s * (1 - fg * 0.6f), 0xFF7FA05A.toInt()) // 봉오리
            for (k in 0 until 11) {
                val a = r.f(0f, 2 * M.PI); val d = M.sqrt(r.f()) * 5.5f * s
                val c = reds[r.i(3)]
                if (fg > 0f) b.circle(ex + M.cos(a) * d, ey + M.sin(a) * d * 0.8f, 1.9f * s * fg, c)
            }
        }
    }
}
