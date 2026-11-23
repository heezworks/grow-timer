package app.sn62kr.grow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.sn62kr.grow.core.draw.DrawBuilder
import app.sn62kr.grow.core.draw.Drawing
import androidx.compose.ui.draw.clipToBounds
import app.sn62kr.grow.core.draw.Palette
import app.sn62kr.grow.core.draw.PlantArt
import app.sn62kr.grow.core.draw.Pose
import app.sn62kr.grow.core.draw.SceneArt
import app.sn62kr.grow.core.draw.SceneInput
import app.sn62kr.grow.core.draw.PlantSpot
import app.sn62kr.grow.core.garden.GardenState
import app.sn62kr.grow.core.garden.PlantState
import app.sn62kr.grow.render.AndroidRenderer

/** 정원 상태 → 장면 입력. 지금 식물이 첫째, 다 자란 것은 최근 것부터. */
fun sceneInput(gs: GardenState, seed: Long, pose: Pose, dusk: Boolean, square: Boolean = false) = SceneInput(
    seed, pose, gs.stillLife,
    (listOf(gs.current) + gs.grown.reversed()).map { PlantSpot(it.species, it.seed, it.growth) },
    square, dusk,
)

/**
 * 테라스 장면(D57). 그림은 저장하지 않고 입력으로 매번 그린다(D43).
 * [cropTop]: 위(하늘)를 이만큼(장면 단위) 잘라 낮게 보인다 — 홈은 시작 버튼이 첫 화면에 보이게 자른다.
 */
@Composable
fun SceneView(gs: GardenState, seed: Long, pose: Pose, description: String, modifier: Modifier = Modifier, square: Boolean = false, cropTop: Float = 0f) {
    val dusk = isSystemInDarkTheme()
    SceneCanvas(remember(gs, seed, pose, dusk, square) { SceneArt.build(sceneInput(gs, seed, pose, dusk, square)) }, description, modifier, cropTop)
}

@Composable
fun SceneCanvas(drawing: Drawing, description: String, modifier: Modifier = Modifier, cropTop: Float = 0f) {
    Canvas(modifier.aspectRatio(drawing.width / (drawing.height - cropTop)).clipToBounds().semantics { contentDescription = description }) {
        drawIntoCanvas {
            val k = size.width / drawing.width
            it.nativeCanvas.save(); it.nativeCanvas.translate(0f, -cropTop * k)
            AndroidRenderer.draw(it.nativeCanvas, drawing, size.width)
            it.nativeCanvas.restore()
        }
    }
}

/** 화분 하나만 크게(식물 화면). */
@Composable
fun PlantView(p: PlantState, description: String, modifier: Modifier = Modifier) {
    val dusk = isSystemInDarkTheme()
    val drawing = remember(p, dusk) {
        DrawBuilder(110f, 130f).also { PlantArt.draw(it, 55f, 122f, 1.2f, p.seed, p.species, p.growth, if (dusk) Palette.DUSK else Palette.DAY) }.build()
    }
    Canvas(modifier.aspectRatio(110f / 130f).semantics { contentDescription = description }) {
        drawIntoCanvas { AndroidRenderer.draw(it.nativeCanvas, drawing, size.width) }
    }
}
