package app.sn62kr.grow.core.time

/** design.md 2절의 규칙 숫자. 한곳에 모아 둔다. */
object Rules {
    /** 한 세션 최대 길이. 넘으면 실제로 보낸 시간을 묻는다. */
    const val MAX_SESSION_MS = 8L * 3600 * 1000

    /** 이보다 짧으면 저장할지 묻는다. */
    const val SHORT_SESSION_MS = 60L * 1000

    /** 멈춘 채 이만큼 지나면 "멈춘 타이머를 끝낼까요?" */
    const val STALE_PAUSE_MS = 12L * 3600 * 1000

    /** 하루에 손으로 더할 수 있는 합의 한도. */
    const val DAY_LIMIT_SEC = 24 * 3600
}
