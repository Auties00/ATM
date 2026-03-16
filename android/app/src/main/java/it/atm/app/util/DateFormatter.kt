package it.atm.app.util

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char

object DateFormatter {
    private val isoDateTime = LocalDateTime.Formats.ISO
    private val isoDate = LocalDate.Formats.ISO
    private val systemTimeZone = TimeZone.currentSystemDefault()

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

    fun formatDate(dateStr: String): String =
        runCatching { displayDate.format(isoDateTime.parse(dateStr).date) }
            .recoverCatching { displayDate.format(isoDate.parse(dateStr)) }
            .getOrDefault(dateStr)

    fun formatDateTime(dateStr: String): String =
        runCatching { displayDateTime.format(isoDateTime.parse(dateStr)) }
            .recoverCatching { displayDate.format(isoDate.parse(dateStr)) }
            .getOrDefault(dateStr)

    fun nowIso(): String =
        isoDateTime.format(Clock.System.now().toLocalDateTime(systemTimeZone))
}
