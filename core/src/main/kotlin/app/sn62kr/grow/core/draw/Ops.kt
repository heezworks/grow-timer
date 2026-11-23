package app.sn62kr.grow.core.draw

// 그리기 목록(D45). core는 "무엇을 어디에"만 정하고, 칠하는 일은 플랫폼 렌더러가 한다.
// 그래서 화면 = 위젯 비트맵 = 공유 카드가 같은 그림이 된다. 색은 0xAARRGGBB.
// 스파이크 S2에서 JVM과 폰의 결과가 같음을 보였다(지문 일치).

sealed interface Op

data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int) : Op

data class RoundRect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val color: Int) : Op

/** 둥근 사각형 테두리(오늘 칸 표시). */
data class RoundRectStroke(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val width: Float, val color: Int) : Op

class Drawing(val width: Float, val height: Float, val ops: List<Op>) {
    /** 결정성 확인용 지문: 같은 입력이면 어디서 만들어도 같아야 한다. */
    fun fingerprint(): Long {
        var h = 1125899906842597L
        for (op in ops) h = 31 * h + op.hashCode()
        return h
    }
}
