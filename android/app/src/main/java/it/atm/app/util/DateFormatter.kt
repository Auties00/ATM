package it.atm.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    private val isoDateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val displayDateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val syncFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    fun formatDate(dateStr: String): String {
        return try {
            isoDateTimeFormat.parse(dateStr)?.let { displayDateFormat.format(it) } ?: dateStr
        } catch (_: Exception) {
            try {
                isoDateFormat.parse(dateStr)?.let { displayDateFormat.format(it) } ?: dateStr
            } catch (_: Exception) { dateStr }
        }
    }

    fun formatDateTime(dateStr: String): String {
        return try {
            isoDateTimeFormat.parse(dateStr)?.let { displayDateTimeFormat.format(it) } ?: dateStr
        } catch (_: Exception) {
            try {
                isoDateFormat.parse(dateStr)?.let { displayDateFormat.format(it) } ?: dateStr
            } catch (_: Exception) { dateStr }
        }
    }

    fun nowIso(): String = syncFormat.format(Date())
}
