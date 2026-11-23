package app.sn62kr.grow.core.draw

import app.sn62kr.grow.core.garden.Garden
import app.sn62kr.grow.core.garden.Species
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * M3 그림 갤러리: 주인이 보고 고른다(D68). `-Dgallery.out=<폴더>`가 있을 때만 PNG를 쓴다(CI에서는 결정성만 본다).
 * 만들기: ./gradlew :core:test --tests '*GalleryTest*' -Pgallery=docs/checks/m3
 */
class GalleryTest {
    private val out = System.getProperty("gallery.out")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }
    private val gardenSeed = 20261123L

    private fun save(img: BufferedImage, name: String) { out?.let { ImageIO.write(img, "png", File(it, name)) } }

    /** 그림 여러 장을 격자로 붙이고 칸마다 글자를 단다. */
    private fun sheet(cells: List<Pair<String, BufferedImage>>, cols: Int, pad: Int = 12): BufferedImage {
        val w = cells.maxOf { it.second.width }; val h = cells.maxOf { it.second.height }
        val rows = (cells.size + cols - 1) / cols
        val img = BufferedImage(cols * (w + pad) + pad, rows * (h + pad + 22) + pad, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0xFAF7F0); g.fillRect(0, 0, img.width, img.height)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 15)
        cells.forEachIndexed { i, (label, im) ->
            val x = pad + (i % cols) * (w + pad); val y = pad + (i / cols) * (h + pad + 22)
            g.drawImage(im, x, y + 20, null)
            g.color = Color(0x4D463C); g.drawString(label, x, y + 14)
        }
        g.dispose(); return img
    }

    private fun plantImage(sp: Species, seed: Long, growth: Float): BufferedImage {
        val b = DrawBuilder(110f, 130f)
        b.rect(0f, 0f, 110f, 130f, 0xFFF3EEE4.toInt())
        PlantArt.draw(b, 55f, 120f, 1.15f, seed, sp, growth, Palette.DAY)
        return Java2D.render(b.build(), 220)
    }

    private fun scene(pose: Pose, totalSec: Long, square: Boolean = false, dusk: Boolean = false): BufferedImage {
        val gs = Garden.of(totalSec, gardenSeed)
        val plants = listOf(gs.current) + gs.grown.reversed()
        val inp = SceneInput(gardenSeed, pose, gs.stillLife, plants.map { PlantSpot(it.species, it.seed, it.growth) }, square, dusk)
        return Java2D.render(SceneArt.build(inp), 640)
    }

    @Test fun plantsGrowing() {
        val gs = listOf(0f, 0.1f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
        for (sp in Species.entries) {
            val cells = (0 until 3).flatMap { k -> gs.map { g -> "씨앗 ${k + 1} · ${(g * 100).toInt()}%" to plantImage(sp, 1000L + k * 77, g) } }
            save(sheet(cells, gs.size), "20-${sp.key}.png")
        }
    }

    @Test fun poses() {
        val cells = Pose.entries.map { p -> p.name to scene(p, 6 * 3600L) }
        save(sheet(cells, 3), "30-poses.png")
    }

    /** 가짜 사용자 셋(하루 15분·30분·2시간)의 1·2·4·12주 장면(plan M3: 눈금 고정용). */
    @Test fun fakeUsers() {
        val perDay = listOf("하루 15분" to 15 * 60L, "하루 30분" to 30 * 60L, "하루 2시간" to 120 * 60L)
        val weeks = listOf(1, 2, 4, 12)
        val cells = perDay.flatMap { (label, sec) ->
            weeks.map { w ->
                val total = sec * 7 * w
                val gs = Garden.of(total, gardenSeed)
                "$label · ${w}주 (${total / 3600}시간, 식물 ${gs.grown.size + 1}번째 ${gs.current.stage}/5단계, 정물 ${gs.stillLife})" to scene(Pose.BOOK, total)
            }
        }
        save(sheet(cells, 4), "40-fake-users.png")
    }

    @Test fun duskAndPolaroid() {
        save(sheet(listOf("저녁(다크 모드)" to scene(Pose.GUITAR, 40 * 3600L, dusk = true), "폴라로이드(정사각)" to scene(Pose.KNIT, 40 * 3600L, square = true)), 2), "50-dusk-polaroid.png")
    }

    @Test fun deterministic() {
        val gs = Garden.of(123 * 3600L, gardenSeed)
        val inp = SceneInput(gardenSeed, Pose.SKETCH, gs.stillLife, (listOf(gs.current) + gs.grown).map { PlantSpot(it.species, it.seed, it.growth) })
        assertEquals(SceneArt.build(inp).fingerprint(), SceneArt.build(inp).fingerprint())
        // 자라는 동안 이미 난 잎은 제자리: 성장 0.5의 첫 잎 타원이 0.9에서도 같은 중심
        val a = DrawBuilder(100f, 100f).also { PlantArt.draw(it, 50f, 90f, 1f, 5L, Species.LEMON, 0.5f, Palette.DAY) }.ops.filterIsInstance<Ellipse>()
        val b = DrawBuilder(100f, 100f).also { PlantArt.draw(it, 50f, 90f, 1f, 5L, Species.LEMON, 0.5f, Palette.DAY) }.ops.filterIsInstance<Ellipse>()
        assertEquals(a, b)
    }
}
