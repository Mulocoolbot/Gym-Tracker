package com.mulocoolbot.gymtracker.util

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun todayLocalDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

fun formatTwoDigits(value: Int): String = value.coerceAtLeast(0).toString().padStart(2, '0')

fun parseDecimalInput(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

fun formatDecimal1(value: Double): String {
    if (!value.isFinite()) return "0.0"
    val clamped = value.coerceIn(-999_999_999_999.0, 999_999_999_999.0)
    val scaled = (clamped * 10.0).roundToLong()
    val sign = if (scaled < 0L) "-" else ""
    val absScaled = abs(scaled)
    val whole = absScaled / 10L
    val tenths = absScaled % 10L
    return "$sign$whole.$tenths"
}

fun formatCompactNumber(value: Double): String {
    if (!value.isFinite()) return "0"
    val clamped = value.coerceIn(-999_999_999_999.0, 999_999_999_999.0)
    val scaled = (clamped * 10.0).roundToLong()
    val absScaled = abs(scaled)
    return if (absScaled % 10L == 0L) {
        val sign = if (scaled < 0L) "-" else ""
        "$sign${absScaled / 10L}"
    } else {
        formatDecimal1(clamped)
    }
}

fun formatHeaderDate(date: LocalDate): String {
    val dayName = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
    }
    return "$dayName, ${shortMonthName(date.month)} ${date.dayOfMonth}"
}

fun formatShortMonthDay(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${shortMonthName(date.month)} ${date.dayOfMonth}"
}

fun parseIsoDateToEpochMillis(input: String): Long {
    return runCatching {
        LocalDate.parse(input.trim())
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    }.getOrDefault(0L)
}

fun epochMillisToIsoDateUtc(millis: Long): String {
    return Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.UTC)
        .date
        .toString()
}

private fun shortMonthName(month: Month): String = when (month) {
    Month.JANUARY -> "Jan"
    Month.FEBRUARY -> "Feb"
    Month.MARCH -> "Mar"
    Month.APRIL -> "Apr"
    Month.MAY -> "May"
    Month.JUNE -> "Jun"
    Month.JULY -> "Jul"
    Month.AUGUST -> "Aug"
    Month.SEPTEMBER -> "Sep"
    Month.OCTOBER -> "Oct"
    Month.NOVEMBER -> "Nov"
    Month.DECEMBER -> "Dec"
}
