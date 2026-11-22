package app.sn62kr.core.draw

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DrawTest {
    private val out = File(System.getProperty("gallery.out") ?: "build/gallery").apply { mkdirs() }

    @Test
    fun sameInputSameDrawing() {
        val inp = SceneInput(42, Pose.BOOK, 4, listOf(Species.OLIVE to 0.7f, Species.LEMON to 0.5f, Species.LAVENDER to 0.9f))
        assertEquals(SceneArt.build(inp).fingerprint(), SceneArt.build(inp).fingerprint())
        assertNotEquals(SceneArt.build(inp).fingerprint(), SceneArt.build(inp.copy(seed = 43)).fingerprint())
        val a = render(SceneArt.build(inp), 640); val b = render(SceneArt.build(inp), 640)
        assertEquals(a.getRGB(0, 0, 640, 480, null, 0, 640).toList(), b.getRGB(0, 0, 640, 480, null, 0, 640).toList())
        // 기기에서 맞대 볼 지문
        println("FINGERPRINT seed42 = ${SceneArt.build(inp).fingerprint()}")
    }

    @Test
    fun timing() {
        val minutes = IntArray(371) { if (it % 3 == 0) 0 else (it * 37) % 150 }
        repeat(200) { GridArt.build(minutes); PlantArt.draw(DrawBuilder(100f, 100f), 50f, 90f, 1f, it.toLong(), Species.OLIVE, 1f, Palette.DAY) }
        val t0 = System.nanoTime(); repeat(200) { GridArt.build(minutes) }
        val t1 = System.nanoTime(); repeat(200) { val b = DrawBuilder(100f, 100f); PlantArt.draw(b, 50f, 90f, 1f, it.toLong(), Species.OLIVE, 1f, Palette.DAY) }
        val t2 = System.nanoTime()
        println("JVM build: grid ${(t1 - t0) / 200 / 1000}us, olive ${(t2 - t1) / 200 / 1000}us")
    }

    @Test
    fun gallery() {
        // 1) 씨앗 20개 × 종 3 다 자란 모습
        for (sp in Species.values()) {
            val cols = 5; val rows = 4; val cw = 140f; val ch = 150f
            val b = DrawBuilder(cols * cw, rows * ch)
            b.rect(0f, 0f, cols * cw, rows * ch, Palette.DAY.wall)
            for (i in 0 until 20) PlantArt.draw(b, (i % cols) * cw + cw / 2, (i / cols) * ch + ch - 12, 1.6f, 1000L + i, sp, 1f, Palette.DAY)
            save(b.build(), 1400, "10-${sp.name.lowercase()}-씨앗20.png")
        }
        // 2) 씨앗 3개 × 성장 6단계(종마다)
        val stages = floatArrayOf(0.02f, 0.12f, 0.3f, 0.5f, 0.75f, 1f)
        for (sp in Species.values()) {
            val cw = 130f; val ch = 150f
            val b = DrawBuilder(6 * cw, 3 * ch)
            b.rect(0f, 0f, 6 * cw, 3 * ch, Palette.DAY.wall)
            for (row in 0 until 3) for ((k, st) in stages.withIndex())
                PlantArt.draw(b, k * cw + cw / 2, row * ch + ch - 12, 1.5f, 2000L + row, sp, st, Palette.DAY)
            save(b.build(), 1300, "20-${sp.name.lowercase()}-성장6단계.png")
        }
        // 3) 장면: 낮·저녁, 자세 둘, 폴라로이드
        save(SceneArt.build(SceneInput(7, Pose.BOOK, 5, listOf(Species.OLIVE to 0.85f, Species.LEMON to 0.8f, Species.LAVENDER to 0.95f))), 1280, "30-장면-낮-책.png")
        save(SceneArt.build(SceneInput(11, Pose.GUITAR, 3, listOf(Species.OLIVE to 0.35f, Species.LEMON to 0.25f, Species.LAVENDER to 0.5f), dusk = true)), 1280, "31-장면-저녁-기타.png")
        save(SceneArt.build(SceneInput(21, Pose.BOOK, 6, listOf(Species.OLIVE to 1f, Species.LEMON to 1f, Species.LAVENDER to 1f), square = true)), 960, "32-폴라로이드.png")
        // 4) 격자
        val minutes = IntArray(371) { val x = (it * 2654435761L % 1000).toInt(); if (x < 450) 0 else x % 160 }
        save(GridArt.build(minutes), 1272, "40-격자.png")
    }

    private fun save(d: Drawing, widthPx: Int, name: String) = ImageIO.write(render(d, widthPx), "png", File(out, name))

    /** 데스크톱용 렌더러(Java2D). 앱의 Android 렌더러와 같은 규칙으로 칠한다. */
    private fun render(d: Drawing, widthPx: Int): BufferedImage {
        val k = widthPx / d.width
        val img = BufferedImage(widthPx, (d.height * k).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.scale(k.toDouble(), k.toDouble())
        for (op in d.ops) when (op) {
            is Rect -> { g.color = Color(op.color, true); g.fill(java.awt.geom.Rectangle2D.Float(op.x, op.y, op.w, op.h)) }
            is RoundRect -> { g.color = Color(op.color, true); g.fill(RoundRectangle2D.Float(op.x, op.y, op.w, op.h, op.r * 2, op.r * 2)) }
            is VGradient -> { g.paint = GradientPaint(0f, op.y, Color(op.top, true), 0f, op.y + op.h, Color(op.bottom, true)); g.fill(java.awt.geom.Rectangle2D.Float(op.x, op.y, op.w, op.h)) }
            is Ellipse -> {
                g.color = Color(op.color, true)
                val e = Ellipse2D.Float(-op.rx, -op.ry, op.rx * 2, op.ry * 2)
                val t = AffineTransform(); t.translate(op.cx.toDouble(), op.cy.toDouble()); t.rotate(op.rot.toDouble())
                g.fill(t.createTransformedShape(e))
            }
            is Poly -> { g.color = Color(op.color, true); g.fill(path(op.pts, true)) }
            is Line -> {
                g.color = Color(op.color, true)
                g.stroke = BasicStroke(op.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(path(op.pts, false))
            }
        }
        g.dispose()
        return img
    }

    private fun path(p: FloatArray, close: Boolean) = Path2D.Float().apply {
        moveTo(p[0], p[1]); var i = 2
        while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
        if (close) closePath()
    }
}
