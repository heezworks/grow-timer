package app.sn62kr.spike.draw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import app.sn62kr.core.draw.Drawing
import app.sn62kr.core.draw.Ellipse
import app.sn62kr.core.draw.Line
import app.sn62kr.core.draw.Poly
import app.sn62kr.core.draw.Rect
import app.sn62kr.core.draw.RoundRect
import app.sn62kr.core.draw.VGradient

/** core의 그리기 목록 → android.graphics.Canvas. 화면(Compose)·위젯 비트맵·카드가 이 하나를 쓴다(D45). */
object AndroidRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rf = RectF()

    fun draw(c: Canvas, d: Drawing, widthPx: Float) {
        val k = widthPx / d.width
        c.save(); c.scale(k, k)
        c.clipRect(0f, 0f, d.width, d.height) // 장면 밖으로 나가는 선(바닥 줄)을 자른다 — Compose Canvas는 기본으로 자르지 않는다
        for (op in d.ops) when (op) {
            is Rect -> { fill.shader = null; fill.color = op.color; c.drawRect(op.x, op.y, op.x + op.w, op.y + op.h, fill) }
            is RoundRect -> { fill.shader = null; fill.color = op.color; rf.set(op.x, op.y, op.x + op.w, op.y + op.h); c.drawRoundRect(rf, op.r, op.r, fill) }
            is VGradient -> {
                fill.shader = LinearGradient(0f, op.y, 0f, op.y + op.h, op.top, op.bottom, Shader.TileMode.CLAMP)
                c.drawRect(op.x, op.y, op.x + op.w, op.y + op.h, fill); fill.shader = null
            }
            is Ellipse -> {
                fill.shader = null; fill.color = op.color
                c.save(); c.translate(op.cx, op.cy); c.rotate(Math.toDegrees(op.rot.toDouble()).toFloat())
                rf.set(-op.rx, -op.ry, op.rx, op.ry); c.drawOval(rf, fill); c.restore()
            }
            is Poly -> { fill.shader = null; fill.color = op.color; build(op.pts, true); c.drawPath(path, fill) }
            is Line -> { stroke.color = op.color; stroke.strokeWidth = op.width; build(op.pts, false); c.drawPath(path, stroke) }
        }
        c.restore()
    }

    fun toBitmap(d: Drawing, widthPx: Int): Bitmap {
        val h = (d.height * widthPx / d.width).toInt()
        return Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { draw(Canvas(it), d, widthPx.toFloat()) }
    }

    private fun build(p: FloatArray, close: Boolean) {
        path.rewind(); path.moveTo(p[0], p[1]); var i = 2
        while (i < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 }
        if (close) path.close()
    }
}
