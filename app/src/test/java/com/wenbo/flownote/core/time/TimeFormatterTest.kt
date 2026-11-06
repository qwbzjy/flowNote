package com.wenbo.flownote.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimeFormatterTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 11, 6, 10, 55)

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `同年显示月日时分`() {
        val t = millisOf(2026, 11, 6, 10, 55)
        assertEquals("11月6日 10:55", TimeFormatter.format(t, now, zone))
    }

    @Test
    fun `同年不同月`() {
        val t = millisOf(2026, 3, 9, 8, 5)
        assertEquals("3月9日 08:05", TimeFormatter.format(t, now, zone))
    }

    @Test
    fun `跨年显示完整年份`() {
        val t = millisOf(2025, 12, 31, 23, 59)
        assertEquals("2025年12月31日 23:59", TimeFormatter.format(t, now, zone))
    }
}
