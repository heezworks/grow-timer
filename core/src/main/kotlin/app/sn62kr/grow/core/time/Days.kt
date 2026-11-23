package app.sn62kr.grow.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 하루의 경계는 현지 자정이다(design.md 2절). */
object Days {
    fun localDate(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun key(date: LocalDate): String = date.toString()

    fun parse(key: String): LocalDate = LocalDate.parse(key)

    /** 사후 입력의 startUtc: 그날 정오(현지). 시간을 모르니 하루 한가운데에 둔다. */
    fun manualStart(date: LocalDate, zone: ZoneId): Long = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    /** 그날 합에 [addSec]를 더해도 되나(design.md 2절 "하루 합"). */
    fun fitsInDay(existingSec: Int, addSec: Int): Boolean = existingSec.toLong() + addSec <= Rules.DAY_LIMIT_SEC
}
