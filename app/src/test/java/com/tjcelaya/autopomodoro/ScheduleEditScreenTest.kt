package com.tjcelaya.autopomodoro

import com.tjcelaya.autopomodoro.ui.screens.parseCooldownMinutesInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleEditScreenTest {

    @Test
    fun `empty string maps to null`() {
        assertNull(parseCooldownMinutesInput(""))
    }

    @Test
    fun `whitespace-only string maps to null`() {
        assertNull(parseCooldownMinutesInput("   "))
    }

    @Test
    fun `zero maps to null`() {
        assertNull(parseCooldownMinutesInput("0"))
    }

    @Test
    fun `negative numbers map to null`() {
        assertNull(parseCooldownMinutesInput("-5"))
    }

    @Test
    fun `non-numeric text maps to null`() {
        assertNull(parseCooldownMinutesInput("abc"))
    }

    @Test
    fun `mixed alphanumeric text maps to null`() {
        assertNull(parseCooldownMinutesInput("5m"))
    }

    @Test
    fun `a value too large for Int maps to null instead of throwing`() {
        assertNull(parseCooldownMinutesInput("99999999999999999999"))
    }

    @Test
    fun `a valid positive value parses through`() {
        assertEquals(15, parseCooldownMinutesInput("15"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(30, parseCooldownMinutesInput("  30  "))
    }

    @Test
    fun `the smallest valid value, one, parses through`() {
        assertEquals(1, parseCooldownMinutesInput("1"))
    }

    @Test
    fun `Int MAX_VALUE parses through without overflowing`() {
        assertEquals(Int.MAX_VALUE, parseCooldownMinutesInput(Int.MAX_VALUE.toString()))
    }
}
