package com.example.planningbgt.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {
    private val displayLocale: Locale = Locale.forLanguageTag("es-CO")

    fun combineDateAndTime(
        utcDateMillis: Long,
        hour: Int,
        minute: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return LocalDateTime.of(date, LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", displayLocale)
            .format(Instant.ofEpochMilli(millis).atZone(zone))
}