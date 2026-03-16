package it.atm.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormatterTest {

    @Test
    fun formatDate_isoDateTime() {
        assertEquals("15 Jun 2025", DateFormatter.formatDate("2025-06-15T10:30:00"))
    }

    @Test
    fun formatDate_isoDate() {
        assertEquals("15 Jun 2025", DateFormatter.formatDate("2025-06-15"))
    }

    @Test
    fun formatDate_invalidReturnsOriginal() {
        assertEquals("not-a-date", DateFormatter.formatDate("not-a-date"))
    }

    @Test
    fun formatDateTime_isoDateTime() {
        assertEquals("15 Jun 2025, 10:30", DateFormatter.formatDateTime("2025-06-15T10:30:00"))
    }

    @Test
    fun nowIso_returnsNonEmpty() {
        val result = DateFormatter.nowIso()
        assertEquals(true, result.isNotBlank())
        assertEquals(true, result.contains("T"))
    }
}
