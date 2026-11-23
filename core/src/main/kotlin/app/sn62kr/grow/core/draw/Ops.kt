package app.sn62kr.grow.core.draw

// 그리기 목록(D45). core는 "무엇을 어디에"만 정하고, 칠하는 일은 플랫폼 렌더러가 한다.
// 그래서 화면 = 위젯 비트맵 = 공유 카드가 같은 그림이 된다. 색은 0xAARRGGBB.
// 스파이크 S2에서 JVM과 폰의 결과가 같음을 보였다(지문 일치).

sealed interface Op

data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int) : Op

data class RoundRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val color: Int) : Op

/** 둥근 사각형 테두리(오늘 칸 표시). */
data class RoundRectStroke(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val width: Float, val color: Int) : Op

/** 위에서 아래로 두 색 그러데이션. */
data class VGradient(val x: Float, val y: Float, val w: Float, val h: Float, val top: Int, val bottom: Int) : Op

/** 기울어진 타원(rot은 라디안). */
data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val rot: Float, val color: Int) : Op

/** 점을 이은 다각형(채움). */
data class Poly(val pts: FloatArray, val color: Int) : Op {
    override fun equals(other: Any?) = other is Poly && color == other.color && pts.contentEquals(other.pts)
    override fun hashCode() = 31 * color + pts.contentHashCode()
}

/** 점을 이은 선(둥근 끝). */
data class Line(val pts: FloatArray, val width: Float, val color: Int) : Op {
    override fun equals(other: Any?) = other is Line && color == other.color && width == other.width && pts.contentEquals(other.pts)
    override fun hashCode() = (31 * color + width.hashCode()) * 31 + pts.contentHashCode()
}

/** 그 뒤의 그리기를 이 사각형 안으로 자른다(장면 밖으로 나가는 바닥 줄). 렌더러는 목록 끝에서 되돌린다. */
data class Clip(val x: Float, val y: Float, val w: Float, val h: Float) : Op

class Drawing(val width: Float, val height: Float, val ops: List<Op>) {
    /** 결정성 확인용 지문: 같은 입력이면 어디서 만들어도 같아야 한다. */
    fun fingerprint(): Long {
        var h = 1125899906842597L
        for (op in ops) h = 31 * h + op.hashCode()
        return h
    }
}

class DrawBuilder(val width: Float, val height: Float) {
    val ops = ArrayList<Op>(1024)
    fun build() = Drawing(width, height, ops)

    fun rect(x: Float, y: Float, w: Float, h: Float, c: Int) { ops += Rect(x, y, w, h, c) }
    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, c: Int) { ops += RoundRect(x, y, w, h, r, c) }
    fun gradient(x: Float, y: Float, w: Float, h: Float, top: Int, bottom: Int) { ops += VGradient(x, y, w, h, top, bottom) }
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float, c: Int) {
        if (rx > 0.01f && ry > 0.01f) ops += Ellipse(cx, cy, rx, ry, rot, c)
    }
    fun circle(cx: Float, cy: Float, r: Float, c: Int) = ellipse(cx, cy, r, r, 0f, c)
    fun poly(c: Int, vararg p: Float) { ops += Poly(p, c) }
    fun line(w: Float, c: Int, vararg p: Float) { if (w > 0.01f) ops += Line(p, w, c) }
    fun clip(x: Float, y: Float, w: Float, h: Float) { ops += Clip(x, y, w, h) }

    /** 2차 베지어를 n개의 점으로 펴서 선으로. */
    fun quad(w: Float, c: Int, x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, n: Int = 12) {
        if (w <= 0.01f) return
        val p = FloatArray((n + 1) * 2)
        for (i in 0..n) {
            val t = i / n.toFloat(); val u = 1 - t
            p[i * 2] = u * u * x0 + 2 * u * t * cx + t * t * x1
            p[i * 2 + 1] = u * u * y0 + 2 * u * t * cy + t * t * y1
        }
        ops += Line(p, w, c)
    }

    /** 2차 베지어 위의 점 하나(t = 0~1). */
    fun quadAt(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, t: Float): Pair<Float, Float> {
        val u = 1 - t
        return (u * u * x0 + 2 * u * t * cx + t * t * x1) to (u * u * y0 + 2 * u * t * cy + t * t * y1)
    }

    /** 타원 호의 부채꼴(파라솔 조각). */
    fun wedge(cx: Float, cy: Float, ox: Float, oy: Float, rx: Float, ry: Float, a0: Float, a1: Float, c: Int, n: Int = 8) {
        val p = FloatArray((n + 2) * 2)
        p[0] = cx; p[1] = cy
        for (i in 0..n) {
            val a = a0 + (a1 - a0) * i / n
            p[(i + 1) * 2] = ox + rx * M.cos(a)
            p[(i + 1) * 2 + 1] = oy + ry * M.sin(a)
        }
        ops += Poly(p, c)
    }

    /** 중심 (cx, cy)에서 각 rot만큼 돌린 사각형. */
    fun rotRect(cx: Float, cy: Float, w: Float, h: Float, rot: Float, c: Int) {
        val co = M.cos(rot); val si = M.sin(rot)
        val p = FloatArray(8)
        val xs = floatArrayOf(-w / 2, w / 2, w / 2, -w / 2); val ys = floatArrayOf(-h / 2, -h / 2, h / 2, h / 2)
        for (i in 0..3) { p[i * 2] = cx + xs[i] * co - ys[i] * si; p[i * 2 + 1] = cy + xs[i] * si + ys[i] * co }
        ops += Poly(p, c)
    }
}

/** 기기마다 같은 결과를 내려고 StrictMath만 쓴다(Math는 기기·JIT마다 끝자리가 다를 수 있다). */
object M {
    const val PI = 3.1415927f
    fun sin(a: Float) = StrictMath.sin(a.toDouble()).toFloat()
    fun cos(a: Float) = StrictMath.cos(a.toDouble()).toFloat()
    fun sqrt(a: Float) = StrictMath.sqrt(a.toDouble()).toFloat()
    fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun easeOut(t: Float): Float { val u = 1 - clamp01(t); return 1 - u * u * u }

    /** 두 색을 t로 섞는다(알파 포함). */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, s: Int) = (c ushr s) and 0xFF
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * clamp01(t)).toInt() and 0xFF
        return (m(24) shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }
}

/** SplitMix64. 시스템 난수를 쓰지 않는다(design.md 4절). */
class Rng(seed: Long) {
    private var s = seed
    fun nextLong(): Long {
        s += -0x61c8864680b583ebL
        var z = s
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
    /** [0, 1) */
    fun f(): Float = ((nextLong() ushr 40).toFloat()) / (1 shl 24).toFloat()
    fun f(a: Float, b: Float) = a + (b - a) * f()
    fun i(n: Int) = ((nextLong() ushr 33) % n).toInt()
    fun <T> pick(xs: List<T>) = xs[i(xs.size)]
    fun fork(salt: Long) = Rng(nextLong() xor salt)

    /** 0 until n을 섞은 순서(Fisher–Yates). */
    fun shuffled(n: Int): IntArray {
        val a = IntArray(n) { it }
        for (k in n - 1 downTo 1) { val j = i(k + 1); val t = a[k]; a[k] = a[j]; a[j] = t }
        return a
    }
}
