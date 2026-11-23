package app.sn62kr.grow.core.draw

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** 시험용 렌더러: 그리기 목록 → PNG. 앱의 AndroidRenderer와 같은 뜻으로 칠한다(갤러리용, 픽셀까지 같지는 않다). */
object Java2D {
    fun render(d: Drawing, widthPx: Int): BufferedImage {
        val k = widthPx / d.width
        val img = BufferedImage(widthPx, (d.height * k).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.scale(k.toDouble(), k.toDouble())
        draw(g, d)
        g.dispose()
        return img
    }

    fun draw(g: Graphics2D, d: Drawing) {
        val base = g.transform; val baseClip = g.clip
        for (op in d.ops) when (op) {
            is Rect -> { g.color = col(op.color); g.fill(Rectangle2D.Float(op.x, op.y, op.w, op.h)) }
            is RoundRect -> { g.color = col(op.color); g.fill(RoundRectangle2D.Float(op.x, op.y, op.w, op.h, op.r * 2, op.r * 2)) }
            is RoundRectStroke -> { g.color = col(op.color); g.stroke = BasicStroke(op.width); g.draw(RoundRectangle2D.Float(op.x, op.y, op.w, op.h, op.r * 2, op.r * 2)) }
            is VGradient -> { g.paint = GradientPaint(0f, op.y, col(op.top), 0f, op.y + op.h, col(op.bottom)); g.fill(Rectangle2D.Float(op.x, op.y, op.w, op.h)) }
            is Ellipse -> {
                g.color = col(op.color)
                val t = AffineTransform(base); t.translate(op.cx.toDouble(), op.cy.toDouble()); t.rotate(op.rot.toDouble())
                g.transform = t; g.fill(Ellipse2D.Float(-op.rx, -op.ry, op.rx * 2, op.ry * 2)); g.transform = base
            }
            is Poly -> { g.color = col(op.color); g.fill(path(op.pts, true)) }
            is Line -> { g.color = col(op.color); g.stroke = BasicStroke(op.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(path(op.pts, false)) }
            is Clip -> g.clip(Rectangle2D.Float(op.x, op.y, op.w, op.h))
        }
        g.clip = baseClip
    }

    private fun col(c: Int) = Color(c, true)

    private fun path(p: FloatArray, close: Boolean) = Path2D.Float().apply {
        moveTo(p[0], p[1]); var i = 2
        while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
        if (close) closePath()
    }
}
