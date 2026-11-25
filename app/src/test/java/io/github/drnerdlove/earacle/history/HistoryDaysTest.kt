package io.github.drnerdlove.earacle.history

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDaysTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: String, timeMs: Long) = HistoryEntry(id, timeMs, "T$id", "A", false)

    private val now = at(2026, 11, 25, 14)

    @Test
    fun todayYesterdayAndOlderDaysAreNamed() {
        val groups = groupByDay(
            listOf(
                entry("1", at(2026, 11, 25, 13)),
                entry("2", at(2026, 11, 25, 8)),
                entry("3", at(2026, 11, 24, 22)),
                entry("4", at(2026, 11, 23, 9)),
            ),
            now,
            zone,
        )
        assertEquals(listOf(DayKind.TODAY, DayKind.YESTERDAY, DayKind.OTHER), groups.map { it.kind })
        assertEquals(listOf(listOf("1", "2"), listOf("3"), listOf("4")), groups.map { g -> g.entries.map { it.id } })
        assertEquals(LocalDate.of(2026, 11, 23), groups[2].date)
    }

    @Test
    fun theYearIsShownOnlyForOtherYears() {
        val groups = groupByDay(listOf(entry("1", at(2026, 3, 1, 9)), entry("2", at(2025, 12, 31, 9))), now, zone)
        assertEquals(listOf(false, true), groups.map { it.withYear })
    }

    @Test
    fun justBeforeAndAfterMidnightAreDifferentDays() {
        val groups = groupByDay(listOf(entry("1", at(2026, 11, 25, 0, 1)), entry("2", at(2026, 11, 24, 23, 59))), now, zone)
        assertEquals(listOf(DayKind.TODAY, DayKind.YESTERDAY), groups.map { it.kind })
    }

    @Test
    fun theDayFollowsTheZoneNotUtc() {
        // 23:30 UTC on Nov 24 is already 00:30 on Nov 25 in Berlin.
        val groups = groupByDay(listOf(entry("1", at(2026, 11, 25, 0, 30))), now, zone)
        assertEquals(DayKind.TODAY, groups.single().kind)
        assertEquals(DayKind.YESTERDAY, groupByDay(listOf(entry("1", at(2026, 11, 25, 0, 30))), now, ZoneId.of("UTC")).single().kind)
    }

    @Test
    fun todayIsTheDayInTheZoneOfTheNowToo() {
        // Berlin 00:30 on Nov 25 is still Nov 24 in UTC: an entry from Berlin 00:10 is "today" there.
        val justAfterMidnight = at(2026, 11, 25, 0, 30)
        val groups = groupByDay(listOf(entry("1", at(2026, 11, 25, 0, 10))), justAfterMidnight, zone)
        assertEquals(DayKind.TODAY, groups.single().kind)
    }

    @Test
    fun yesterdayAcrossTheNewYear() {
        val newYear = at(2027, 1, 1, 10)
        val groups = groupByDay(listOf(entry("1", at(2026, 12, 31, 20))), newYear, zone)
        assertEquals(DayKind.YESTERDAY, groups.single().kind)
        assertTrue(groups.single().withYear)
    }

    @Test
    fun noEntriesNoGroups() {
        assertTrue(groupByDay(emptyList(), now, zone).isEmpty())
    }

    @Test
    fun aDayIsWrittenWithThePattern() {
        assertEquals("Mon, Nov 23", formatDay(LocalDate.of(2026, 11, 23), "EEE, MMM d", Locale.US))
        assertEquals("Mon, Nov 23, 2026", formatDay(LocalDate.of(2026, 11, 23), "EEE, MMM d, y", Locale.US))
    }

    @Test
    fun anUnusablePatternGivesTheIsoDate() {
        val text = formatDay(LocalDate.of(2026, 11, 23), "Q bad {pattern", Locale.US)
        assertEquals("2026-11-23", text)
        assertFalse(text.isEmpty())
    }

    @Test
    fun dayKindOfNamesTodayYesterdayAndOlder() {
        assertEquals(DayKind.TODAY, dayKindOf(at(2026, 11, 25, 0, 5), now, zone))
        assertEquals(DayKind.YESTERDAY, dayKindOf(at(2026, 11, 24, 23, 59), now, zone))
        assertEquals(DayKind.OTHER, dayKindOf(at(2026, 11, 23, 23, 59), now, zone))
    }
}
