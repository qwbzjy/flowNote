package com.wenbo.flownote.core.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 笔记时间显示：同年「11月6日 10:55」，跨年「2025年11月6日 10:55」。
 */
object TimeFormatter {

    private val sameYearFormat = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
    private val crossYearFormat = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)

    fun format(epochMillis: Long, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
        return if (time.year == now.year) {
            time.format(sameYearFormat)
        } else {
            time.format(crossYearFormat)
        }
    }
}
