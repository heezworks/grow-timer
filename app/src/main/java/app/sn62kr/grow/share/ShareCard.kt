package app.sn62kr.grow.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.sn62kr.grow.R
import app.sn62kr.grow.core.draw.GridArt
import app.sn62kr.grow.core.draw.GridPalette
import app.sn62kr.grow.core.draw.Pose
import app.sn62kr.grow.core.draw.SceneArt
import app.sn62kr.grow.core.garden.Garden
import app.sn62kr.grow.core.stats.YearGrid
import app.sn62kr.grow.render.AndroidRenderer
import app.sn62kr.grow.ui.Text
import app.sn62kr.grow.ui.album.Polaroid
import app.sn62kr.grow.ui.sceneInput
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 공유 카드 한 종(D60으로 둘째 종은 뺐다): 폴라로이드 + 그해 격자 + 합계 + 앱 이름. 1080 × 1350(4:5, 인스타그램 세로).
 * 짧은 주소는 도메인을 사면 넣는다(Q21). 그림은 화면과 같은 그리기 목록이다(D45). 늘 낮 그림(어두운 화면에서 만들어도 밝게).
 */
object ShareCard {
    const val W = 1080; const val H = 1350

    fun render(ctx: Context, p: Polaroid, gardenSeed: Long, year: YearGrid): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF3EEE4.toInt())
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFDF8.toInt() }
        // 폴라로이드: 살짝 기울인 흰 판
        c.save(); c.rotate(-1.6f, W / 2f, 560f)
        c.drawRoundRect(RectF(118f, 98f, 970f, 1056f), 6f, 6f, shadow)
        c.drawRoundRect(RectF(110f, 88f, 962f, 1046f), 6f, 6f, white)
        val scene = SceneArt.build(sceneInput(Garden.of(p.totalAtEndSec, gardenSeed), gardenSeed, Pose.of(p.activity?.iconKey), dusk = false, square = true))
        c.save(); c.translate(146f, 124f); AndroidRenderer.draw(c, scene, 780f); c.restore()
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A342C.toInt(); textSize = 46f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val dim = Paint(ink).apply { color = 0xFF7A7166.toInt(); textSize = 32f }
        val caption = ctx.getString(R.string.polaroid_caption, p.activity?.name ?: "", Text.duration(ctx, p.session.durationSec.toLong()).replace("⁠", ""))
        c.drawText(caption, 146f, 966f, ink)
        c.drawText(LocalDate.parse(p.session.localDate).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), 146f, 1012f, dim)
        c.restore()
        // 그해 격자와 합계
        val label = Paint(ink).apply { textSize = 38f }
        c.drawText(ctx.getString(R.string.card_year, year.filledDays, Text.duration(ctx, year.totalSec).replace("⁠", ""), year.year), 110f, 1130f, label)
        val grid = GridArt.build(year, GridPalette.LIGHT)
        c.save(); c.translate(110f, 1156f); AndroidRenderer.draw(c, grid, 860f); c.restore()
        val foot = Paint(dim).apply { textSize = 28f; textAlign = Paint.Align.RIGHT }
        c.drawText(ctx.getString(R.string.app_name), 970f, 1318f, foot)
        return bmp
    }

    private fun file(ctx: Context, bmp: Bitmap): File {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // 지난 카드는 지운다
        return File(dir, "card-${System.currentTimeMillis()}.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    /** 시스템 공유 창. 앱은 어디로 보내는지 모른다. */
    fun share(ctx: Context, bmp: Bitmap) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file(ctx, bmp))
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 사진 폴더에 저장(Android 10 이상, 권한 없이). 9 이하는 공유 창에서 저장한다. */
    fun canSave() = Build.VERSION.SDK_INT >= 29

    fun save(ctx: Context, bmp: Bitmap): Boolean {
        if (!canSave()) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "card-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + ctx.getString(R.string.app_name))
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
    }
}
