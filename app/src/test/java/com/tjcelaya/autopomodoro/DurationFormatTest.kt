package com.tjcelaya.autopomodoro

import com.tjcelaya.autopomodoro.util.DurationFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class DurationFormatTest {

    @Test
    fun `renders all three units`() {
        val d = Duration.ofDays(3).plusHours(2).plusMinutes(10)
        assertEquals("3d2h10m", DurationFormat.compact(d))
    }

    @Test
    fun `drops a zero hours component`() {
        val d = Duration.ofDays(3).plusMinutes(10)
        assertEquals("3d10m", DurationFormat.compact(d))
    }

    @Test
    fun `drops a zero minutes component`() {
        val d = Duration.ofDays(2).plusHours(5)
        assertEquals("2d5h", DurationFormat.compact(d))
    }

    @Test
    fun `whole days render alone`() {
        assertEquals("1d", DurationFormat.compact(Duration.ofDays(1)))
    }

    @Test
    fun `whole hours render alone`() {
        assertEquals("2h", DurationFormat.compact(Duration.ofHours(2)))
    }

    @Test
    fun `minutes render alone`() {
        assertEquals("45m", DurationFormat.compact(Duration.ofMinutes(45)))
    }

    @Test
    fun `rolls minutes into hours`() {
        assertEquals("1h30m", DurationFormat.compact(Duration.ofMinutes(90)))
    }

    @Test
    fun `rolls hours into days`() {
        assertEquals("1d1h", DurationFormat.compact(Duration.ofHours(25)))
    }

    @Test
    fun `seconds are truncated, not rounded up`() {
        assertEquals("1m", DurationFormat.compact(Duration.ofSeconds(119)))
    }

    @Test
    fun `sub-minute durations collapse to the imminent marker`() {
        assertEquals(DurationFormat.IMMINENT, DurationFormat.compact(Duration.ofSeconds(30)))
    }

    @Test
    fun `zero collapses to the imminent marker`() {
        assertEquals(DurationFormat.IMMINENT, DurationFormat.compact(Duration.ZERO))
    }

    @Test
    fun `negative durations collapse to the imminent marker`() {
        assertEquals(DurationFormat.IMMINENT, DurationFormat.compact(Duration.ofMinutes(-30)))
    }

    @Test
    fun `between measures from one instant to another`() {
        val from = LocalDateTime.of(2026, 1, 1, 16, 30)
        val to = LocalDateTime.of(2026, 1, 4, 9, 0)
        // 2 days, 16 hours, 30 minutes
        assertEquals("2d16h30m", DurationFormat.between(from, to))
    }

    @Test
    fun `between collapses when the target has already passed`() {
        val from = LocalDateTime.of(2026, 1, 4, 9, 0)
        val to = LocalDateTime.of(2026, 1, 1, 16, 30)
        assertEquals(DurationFormat.IMMINENT, DurationFormat.between(from, to))
    }
}
