package app.sn62kr.spike.draw

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import app.sn62kr.core.draw.DrawBuilder
import app.sn62kr.core.draw.GridArt
import app.sn62kr.core.draw.Palette
import app.sn62kr.core.draw.PlantArt
import app.sn62kr.core.draw.Pose
import app.sn62kr.core.draw.SceneArt
import app.sn62kr.core.draw.SceneInput
import app.sn62kr.core.draw.Species
import kotlinx.coroutines.delay
import kotlin.math.abs

private val INPUT = SceneInput(42, Pose.BOOK, 4, listOf(Species.OLIVE to 0.7f, Species.LEMON to 0.5f, Species.LAVENDER to 0.9f))
private const val JVM_FINGERPRINT = 6328114345093694803L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen() } }
    }
}

private fun median(n: Int, block: () -> Unit): Double {
    repeat(10) { block() }
    val xs = DoubleArray(n) { val t = System.nanoTime(); block(); (System.nanoTime() - t) / 1e6 }
    xs.sort(); return xs[n / 2]
}

@Composable
private fun Screen() {
    val drawing = remember { SceneArt.build(INPUT) }
    val layer = rememberGraphicsLayer()
    var report by remember { mutableStateOf("재는 중…") }

    LaunchedEffect(Unit) {
        delay(1500) // 화면에 한 번 그려진 뒤
        val lines = ArrayList<String>()
        val fp = drawing.fingerprint()
        lines += "지문 기기=$fp, JVM=$JVM_FINGERPRINT → ${if (fp == JVM_FINGERPRINT) "같음 ✅" else "다름 ❌"}"

        val minutes = IntArray(371) { if (it % 3 == 0) 0 else (it * 37) % 150 }
        val gridBmp = Bitmap.createBitmap(1272, 166, Bitmap.Config.ARGB_8888)
        val gridMs = median(50) { val d = GridArt.build(minutes); gridBmp.eraseColor(0); AndroidRenderer.draw(android.graphics.Canvas(gridBmp), d, 1272f) }
        lines += "격자 365칸 만들기+그리기 중앙값 ${"%.2f".format(gridMs)}ms (기준 16ms)"

        val plantBmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        var seed = 0L
        val plantMs = median(50) {
            val b = DrawBuilder(100f, 100f); PlantArt.draw(b, 50f, 95f, 1f, seed++, Species.OLIVE, 1f, Palette.DAY)
            plantBmp.eraseColor(0); AndroidRenderer.draw(android.graphics.Canvas(plantBmp), b.build(), 400f)
        }
        lines += "올리브 한 그루(다 자람) 중앙값 ${"%.2f".format(plantMs)}ms (기준 100ms)"

        val sceneMs = median(30) { AndroidRenderer.toBitmap(SceneArt.build(INPUT), 1080) }
        lines += "장면 전체 1080px 중앙값 ${"%.2f".format(sceneMs)}ms"

        // 화면(하드웨어 가속) 대 비트맵(소프트웨어)
        val screen = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val soft = AndroidRenderer.toBitmap(drawing, screen.width)
        val h = minOf(screen.height, soft.height)
        var diff = 0; var big = 0; val n = screen.width * h
        val a = IntArray(n); val b2 = IntArray(n)
        screen.getPixels(a, 0, screen.width, 0, 0, screen.width, h)
        soft.getPixels(b2, 0, soft.width, 0, 0, soft.width, h)
        for (i in 0 until n) {
            val p = a[i]; val q = b2[i]
            val d = maxOf(abs((p shr 16 and 255) - (q shr 16 and 255)), abs((p shr 8 and 255) - (q shr 8 and 255)), abs((p and 255) - (q and 255)))
            if (d > 8) diff++
            if (d > 64) big++
        }
        lines += "화면 대 비트맵 ${screen.width}×$h: 8 넘게 다른 픽셀 ${"%.3f".format(100.0 * diff / n)}%, 64 넘게 ${"%.3f".format(100.0 * big / n)}%"
        report = lines.joinToString("\n")
        lines.forEach { Log.i("S2", it) }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 40.dp, start = 12.dp, end = 12.dp)) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(drawing.width / drawing.height)
                .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
        ) {
            drawIntoCanvas { AndroidRenderer.draw(it.nativeCanvas, drawing, size.width) }
        }
        Text(report, modifier = Modifier.padding(top = 12.dp))
    }
}
