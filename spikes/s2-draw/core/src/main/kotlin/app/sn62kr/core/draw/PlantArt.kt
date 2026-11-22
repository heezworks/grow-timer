package app.sn62kr.core.draw

import kotlin.math.sqrt

enum class Species { OLIVE, LEMON, LAVENDER }

/**
 * 조립식 + 시간 붙은 성장(D66).
 * 부품(줄기·가지·잎·꽃·열매)마다 씨앗 난수로 "돋는 때"(0~1)를 정하고, growth(0~1)가 그 때를 지나면
 * 짧은 구간에 걸쳐 0 → 제 크기로 자란다. 같은 (seed, species, growth)면 언제나 같은 그리기 목록.
 * 좌표: (x, y) = 화분 바닥 가운데, 위쪽이 -y. s = 크기 배율.
 */
object PlantArt {
    private const val BARK = 0xFF7A5E44.toInt()

    fun draw(b: DrawBuilder, x: Float, y: Float, s: Float, seed: Long, sp: Species, growth: Float, pal: Palette) {
        val g = M.clamp01(growth)
        val r = Rng(seed * 31 + sp.ordinal)
        val potW = r.f(22f, 30f) * s
        val potH = r.f(17f, 23f) * s
        // 그림자 + 화분
        b.ellipse(x, y + 1.5f * s, potW * 0.75f, 3f * s, 0f, pal.shade)
        pot(b, x, y, potW, potH, s, pal.clay, r)
        val top = y - potH - 2f * s
        when (sp) {
            Species.OLIVE -> olive(b, x, top, s, g, r.fork(1), pal)
            Species.LEMON -> lemon(b, x, top, s, g, r.fork(2), pal)
            Species.LAVENDER -> lavender(b, x, top, s, g, r.fork(3), potW)
        }
        // 흙
        b.ellipse(x, top + 2f * s, potW * 0.42f, 2.2f * s, 0f, 0xFF5A4232.toInt())
    }

    private fun grow(g: Float, birth: Float, span: Float = 0.07f) = M.easeOut((g - birth) / span)

    private fun pot(b: DrawBuilder, cx: Float, by: Float, w: Float, h: Float, s: Float, clay: Int, r: Rng) {
        val taper = r.f(0.30f, 0.40f)
        b.poly(clay, cx - w / 2, by - h, cx + w / 2, by - h, cx + w * taper, by, cx - w * taper, by)
        b.rect(cx - w / 2 - 2f * s, by - h - 4f * s, w + 4f * s, 5f * s, 0x1F000000)
        b.rect(cx - w / 2 - 2f * s, by - h - 5f * s, w + 4f * s, 4f * s, clay)
    }

    // 올리브: 비틀린 줄기, 가지 3~5, 가늘고 은빛 도는 잎, 늦게 맺히는 검보라 열매
    private fun olive(b: DrawBuilder, x: Float, top: Float, s: Float, g: Float, r: Rng, pal: Palette) {
        val trunkG = M.easeOut(g / 0.55f)
        val h = M.lerp(10f, 62f, trunkG) * s
        val lean = r.f(-9f, 9f) * s
        val tx = x + lean * 0.6f; val ty = top - h
        val cx = x - lean; val cy = top - h * 0.5f
        b.quad(M.lerp(2f, 4.5f, trunkG) * s, BARK, x, top, cx, cy, tx, ty)

        data class Branch(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val birth: Float)
        val branches = ArrayList<Branch>()
        branches += Branch(tx, ty, tx, ty, 0f) // 줄기 끝
        val nb = 3 + r.i(3)
        for (i in 0 until nb) {
            val birth = r.f(0.12f, 0.55f)
            val bg = grow(g, birth, 0.2f)
            val t = r.f(0.45f, 0.95f); val u = 1 - t
            val ax = u * u * x + 2 * u * t * cx + t * t * tx
            val ay = u * u * top + 2 * u * t * cy + t * t * ty
            val side = if (i % 2 == 0) -1f else 1f
            val ang = -M.PI / 2 + side * r.f(0.5f, 1.2f)
            val len = r.f(14f, 26f) * s * bg
            val ex = ax + M.cos(ang) * len; val ey = ay + M.sin(ang) * len
            if (bg > 0f) b.quad(M.lerp(1f, 2.6f, bg) * s, BARK, ax, ay, (ax + ex) / 2 + side * 2f * s, (ay + ey) / 2 - 3f * s, ex, ey, 8)
            branches += Branch(ax, ay, ex, ey, birth)
        }
        val leafColors = listOf(pal.olive, 0xFF8A9A62.toInt(), 0xFF5D6E3A.toInt(), 0xFF9AA67A.toInt())
        val nLeaves = 80
        for (i in 0 until nLeaves) {
            val birth = if (i < 2) 0f else (i / nLeaves.toFloat()) * 0.95f + r.f(0f, 0.05f)
            val lg = grow(g, birth)
            val br = if (i < 2) branches[0] else branches[r.i(branches.size)]
            val along = r.f(0.35f, 1f)
            val px = M.lerp(br.x0, br.x1, along) + r.f(-7f, 7f) * s * trunkG
            val py = M.lerp(br.y0, br.y1, along) + r.f(-6f, 4f) * s * trunkG
            // 난수는 그리든 안 그리든 같은 순서로 꺼낸다 — 그래야 자라는 동안 잎이 제자리에 있다
            val rot = r.f(-1.2f, 1.2f) + if (i % 2 == 0) 0.4f else -0.4f
            val col = leafColors[r.i(leafColors.size)]
            if (lg > 0f) b.ellipse(px, py, 5.8f * s * lg, 1.7f * s * lg, rot, col)
        }
        val nFruit = 5 + r.i(6)
        for (i in 0 until nFruit) {
            val birth = r.f(0.82f, 0.98f)
            val fg = grow(g, birth, 0.05f)
            val br = branches[1 + r.i(branches.size - 1)]
            val px = M.lerp(br.x0, br.x1, r.f(0.5f, 1f)) + r.f(-5f, 5f) * s
            val py = M.lerp(br.y0, br.y1, r.f(0.5f, 1f)) + r.f(-2f, 5f) * s
            if (fg > 0f) b.ellipse(px, py, 1.9f * s * fg, 1.4f * s * fg, 0.3f, if (i % 2 == 0) 0xFF3B2F4A.toInt() else 0xFF5B4A6B.toInt())
        }
    }

    // 레몬: 짧은 줄기, 둥근 짙은 녹색 수관, 흰 꽃 → 노란 열매
    private fun lemon(b: DrawBuilder, x: Float, top: Float, s: Float, g: Float, r: Rng, pal: Palette) {
        val trunkG = M.easeOut(g / 0.5f)
        val h = M.lerp(6f, 30f, trunkG) * s
        val tx = x + r.f(-3f, 3f) * s
        b.quad(M.lerp(1.8f, 3.6f, trunkG) * s, BARK, x, top, x + r.f(-5f, 5f) * s, top - h * 0.5f, tx, top - h)
        val rad = M.lerp(4f, 26f, M.easeOut(g / 0.85f)) * s
        val ccx = tx; val ccy = top - h - rad * 0.3f
        b.line(M.lerp(1.4f, 2.6f, trunkG) * s, BARK, tx, top - h, ccx, ccy) // 줄기가 수관 안까지 이어진다
        val greens = listOf(0xFF3F6B34.toInt(), 0xFF4E7D3E.toInt(), 0xFF2F5A2A.toInt(), 0xFF5C8A45.toInt())
        val n = 70
        for (i in 0 until n) {
            val rr = sqrt(r.f())
            val a = r.f(0f, 2 * M.PI)
            // 안쪽 잎부터 돋는다(줄기 끝 가까이)
            val birth = if (i < 2) 0f else M.clamp01(rr * rr * 0.8f + r.f(0f, 0.2f))
            val lg = grow(g, birth)
            val px = ccx + M.cos(a) * rr * rad * 1.1f
            val py = ccy + M.sin(a) * rr * rad * 0.85f
            val rot = r.f(0f, M.PI)
            val c = greens[r.i(greens.size)]
            if (lg > 0f) b.ellipse(px, py, 4.4f * s * lg, 2.6f * s * lg, rot, c)
        }
        // 꽃: 0.55~0.75에 피고 열매가 맺히면 줄어든다
        for (i in 0 until 7) {
            val birth = r.f(0.55f, 0.75f)
            val a = r.f(0f, 2 * M.PI); val rr = sqrt(r.f()) * 0.9f
            val fg = grow(g, birth, 0.04f) * (1 - grow(g, 0.9f, 0.08f))
            if (fg > 0f) b.circle(ccx + M.cos(a) * rr * rad, ccy + M.sin(a) * rr * rad * 0.85f, 1.6f * s * fg, 0xFFFFFDF2.toInt())
        }
        val nFruit = 3 + r.i(5)
        for (i in 0 until nFruit) {
            val birth = r.f(0.74f, 0.97f)
            val a = r.f(0f, 2 * M.PI); val rr = sqrt(r.f()) * 0.9f
            val fg = grow(g, birth, 0.06f)
            val rot = r.f(-0.4f, 0.4f)
            if (fg > 0f) b.ellipse(ccx + M.cos(a) * rr * rad, ccy + M.sin(a) * rr * rad * 0.85f, 3.4f * s * fg, 2.6f * s * fg, rot, pal.lemon)
        }
    }

    // 라벤더: 부채꼴로 퍼진 가는 줄기, 아래 은녹색 잎, 위 보라 꽃 이삭
    private fun lavender(b: DrawBuilder, x: Float, top: Float, s: Float, g: Float, r: Rng, potW: Float) {
        val n = 9 + r.i(6)
        val sage = listOf(0xFF8FA588.toInt(), 0xFF7A9474.toInt(), 0xFFA3B59B.toInt())
        for (i in 0 until 14) {
            val birth = r.f(0f, 0.35f)
            val lg = grow(g, birth, 0.1f)
            val lx = x + r.f(-0.3f, 0.3f) * potW; val ly = top - r.f(1f, 6f) * s
            val rot = r.f(-0.8f, 0.8f); val col = sage[r.i(3)]
            if (lg > 0f) b.ellipse(lx, ly, 4.2f * s * lg, 1.5f * s * lg, rot, col)
        }
        val purples = listOf(0xFF8B6CC2.toInt(), 0xFF7A5BB0.toInt(), 0xFF9D80D0.toInt())
        for (i in 0 until n) {
            val birth = if (i == 0) 0f else r.f(0f, 0.45f)
            val sg = grow(g, birth, 0.25f)
            val bx = x + (i - n / 2f) / n * potW * 0.6f
            val ang = -M.PI / 2 + (i - n / 2f) / n * 0.9f + r.f(-0.12f, 0.12f)
            val len = r.f(24f, 38f) * s * sg
            val ex = bx + M.cos(ang) * len; val ey = top + M.sin(ang) * len
            val bend = r.f(-1f, 1f) * s
            if (sg > 0f) b.line(1.1f * s, 0xFF6F8A55.toInt(), bx, top, (bx + ex) / 2 + bend, (top + ey) / 2, ex, ey)
            val fBirth = r.f(0.45f, 0.9f)
            val fg = grow(g, fBirth, 0.08f)
            val dots = 6
            for (k in 0 until dots) {
                val t = 1f - k * 0.055f
                val px = M.lerp(bx, ex, t) + r.f(-1.2f, 1.2f) * s
                val py = M.lerp(top, ey, t)
                val col = purples[r.i(3)]
                if (fg > 0f && sg > 0.6f) b.ellipse(px, py, 1.5f * s * fg, 1.9f * s * fg, ang + M.PI / 2, col)
            }
        }
    }
}
