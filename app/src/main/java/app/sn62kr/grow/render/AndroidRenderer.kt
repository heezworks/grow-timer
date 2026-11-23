package app.sn62kr.grow.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import app.sn62kr.grow.core.draw.Drawing
import app.sn62kr.grow.core.draw.Rect
import app.sn62kr.grow.core.draw.RoundRect
import app.sn62kr.grow.core.draw.RoundRectStroke

/** core의 그리기 목록 → android.graphics.Canvas. 화면(Compose)·위젯 비트맵·카드가 이 하나를 쓴다(D45). */
object AndroidRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rf = RectF()

    /** [d]를 너비 [widthPx]로 늘려 그린다. 메인 스레드에서만 부른다(Paint를 같이 쓴다). */
    fun draw(c: Canvas, d: Drawing, widthPx: Float) {
        val k = widthPx / d.width
        c.save(); c.scale(k, k)
        for (op in d.ops) when (op) {
            is Rect -> { fill.color = op.color; c.drawRect(op.x, op.y, op.x + op.w, op.y + op.h, fill) }
            is RoundRect -> { fill.color = op.color; rf.set(op.x, op.y, op.x + op.w, op.y + op.h); c.drawRoundRect(rf, op.r, op.r, fill) }
            is RoundRectStroke -> {
                stroke.color = op.color; stroke.strokeWidth = op.width
                rf.set(op.x, op.y, op.x + op.w, op.y + op.h); c.drawRoundRect(rf, op.r, op.r, stroke)
            }
        }
        c.restore()
    }

    fun toBitmap(d: Drawing, widthPx: Int): Bitmap {
        val h = (d.height * widthPx / d.width).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { draw(Canvas(it), d, widthPx.toFloat()) }
    }
}
