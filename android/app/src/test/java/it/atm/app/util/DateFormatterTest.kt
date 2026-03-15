package it.atm.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormatterTest {

    @Test
    fun formatDate_isoDateTime() {
        val result = DateFormatter.formatDate("2025-06-15T10:30:00")
        assertEquals(true, result.contains("15"))
        assertEquals(true, result.contains("2025"))
    }

    @Test
    fun formatDate_isoDate() {
        val result = DateFormatter.formatDate("2025-06-15")
        assertEquals(true, result.contains("15"))
        assertEquals(true, result.contains("2025"))
    }

    @Test
    fun formatDate_invalidReturnsOriginal() {
        assertEquals("not-a-date", DateFormatter.formatDate("not-a-date"))
    }

    @Test
    fun formatDateTime_isoDateTime() {
        val result = DateFormatter.formatDateTime("2025-06-15T10:30:00")
        assertEquals(true, result.contains("15"))
        assertEquals(true, result.contains("10:30"))
    }

    @Test
    fun nowIso_returnsNonEmpty() {
        val result = DateFormatter.nowIso()
        assertEquals(true, result.isNotBlank())
        assertEquals(true, result.contains("T"))
    }
}
