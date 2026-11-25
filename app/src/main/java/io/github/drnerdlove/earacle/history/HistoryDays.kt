package io.github.drnerdlove.earacle.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How a day is named above its entries. */
enum class DayKind { TODAY, YESTERDAY, OTHER }

/** The entries of one calendar day, in the order given. [withYear] is true for a day in another year than today's. */
data class DayGroup(
    val date: LocalDate,
    val kind: DayKind,
    val withYear: Boolean,
    val entries: List<HistoryEntry>,
)

/** Whether [timeMs] falls on today, yesterday or an earlier day of [zone], relative to [nowMs]. */
fun dayKindOf(timeMs: Long, nowMs: Long, zone: ZoneId): DayKind {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return when (Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate()) {
        today -> DayKind.TODAY
        today.minusDays(1) -> DayKind.YESTERDAY
        else -> DayKind.OTHER
    }
}

/**
 * Groups [entries] (newest first, as given) by calendar day in [zone], for the headings of the history page. Days
 * follow each other as the entries do; "today" and "yesterday" are relative to [nowMs].
 */
fun groupByDay(entries: List<HistoryEntry>, nowMs: Long, zone: ZoneId): List<DayGroup> {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val groups = ArrayList<DayGroup>()
    for (entry in entries) {
        val date = Instant.ofEpochMilli(entry.timeMs).atZone(zone).toLocalDate()
        val last = groups.lastOrNull()
        if (last != null && last.date == date) {
            groups[groups.lastIndex] = last.copy(entries = last.entries + entry)
        } else {
            val kind = when (date) {
                today -> DayKind.TODAY
                today.minusDays(1) -> DayKind.YESTERDAY
                else -> DayKind.OTHER
            }
            groups += DayGroup(date, kind, withYear = date.year != today.year, entries = listOf(entry))
        }
    }
    return groups
}

/**
 * [date] written with [pattern] (a `DateTimeFormatter` pattern, chosen for the phone's language) in [locale]. A
 * pattern the formatter cannot use gives the plain ISO date instead of a crash.
 */
fun formatDay(date: LocalDate, pattern: String, locale: Locale): String = try {
    DateTimeFormatter.ofPattern(pattern, locale).format(date)
} catch (e: IllegalArgumentException) {
    date.toString()
} catch (e: java.time.DateTimeException) {
    date.toString()
}
