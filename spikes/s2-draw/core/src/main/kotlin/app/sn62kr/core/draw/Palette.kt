package app.sn62kr.core.draw

/** 장면 팔레트(D56 지중해 여름). 낮/저녁 두 벌 — 앱은 시스템 밝기 설정을 따른다(D60). */
data class Palette(
    val skyTop: Int, val skyBottom: Int, val sea: Int, val wall: Int, val floor: Int, val floorLine: Int,
    val skin: Int, val sun: Int, val clay: Int, val olive: Int,
    val lemon: Int = 0xFFF2C230.toInt(), val navy: Int = 0xFF1C2C44.toInt(),
    val white: Int = 0xFFFDFCF8.toInt(), val stripe: Int = 0xFF2F6DB5.toInt(),
    val wood: Int = 0xFF8A6A4A.toInt(), val shade: Int = 0x1A000000,
) {
    companion object {
        val DAY = Palette(
            skyTop = 0xFFBFDDF4.toInt(), skyBottom = 0xFFE6F1FA.toInt(), sea = 0xFF1E5EA6.toInt(),
            wall = 0xFFFBFAF6.toInt(), floor = 0xFFE7DAC6.toInt(), floorLine = 0xFFD8C7AE.toInt(),
            skin = 0xFFD49A70.toInt(), sun = 0xFFF4CB3C.toInt(), clay = 0xFFC4613A.toInt(), olive = 0xFF6C7D44.toInt(),
        )
        val DUSK = Palette(
            skyTop = 0xFF2A3766.toInt(), skyBottom = 0xFFE0936A.toInt(), sea = 0xFF22477E.toInt(),
            wall = 0xFFD8D2C6.toInt(), floor = 0xFF7D6A58.toInt(), floorLine = 0xFF6C5A49.toInt(),
            skin = 0xFFC38A63.toInt(), sun = 0xFFF7A85A.toInt(), clay = 0xFFDA7C55.toInt(), olive = 0xFF9DB070.toInt(),
        )
    }
}

fun argb(a: Int, rgb: Int) = (a shl 24) or (rgb and 0xFFFFFF)
