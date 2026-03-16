package it.atm.app.util

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char

object DateFormatter {
    private val isoDateTime = LocalDateTime.Formats.ISO
    private val isoDate = LocalDate.Formats.ISO

    private val displayDate = LocalDate.Format {
        dayOfMonth()
        char(' ')
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        year()
    }

    private val displayDateTime = LocalDateTime.Format {
        dayOfMonth()
        char(' ')
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        year()
        chars(", ")
        hour()
        char(':')
        minute()
    }

    fun formatDate(dateStr: String): String {
        return try {
            val dt = isoDateTime.parse(dateStr)
            displayDate.format(dt.date)
        } catch (_: Exception) {
            try {
                val d = isoDate.parse(dateStr)
                displayDate.format(d)
            } catch (_: Exception) { dateStr }
        }
    }

    fun formatDateTime(dateStr: String): String {
        return try {
            val dt = isoDateTime.parse(dateStr)
            displayDateTime.format(dt)
        } catch (_: Exception) {
            try {
                val d = isoDate.parse(dateStr)
                displayDate.format(d)
            } catch (_: Exception) { dateStr }
        }
    }

    fun nowIso(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return isoDateTime.format(now)
    }
}
