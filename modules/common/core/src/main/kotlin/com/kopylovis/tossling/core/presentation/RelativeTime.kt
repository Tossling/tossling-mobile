package com.kopylovis.tossling.core.presentation

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun relativeTime(time: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - time) / MINUTE
    return when {
        minutes < 1 -> tr("now", "сейчас")
        minutes < 60 -> tr("$minutes min", "$minutes мин")
        isSameDay(time, now) -> tr("${minutes / 60} h", "${minutes / 60} ч")
        isSameDay(time, now - DAY) -> tr("yesterday", "вчера")
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(time))
    }
}

fun contactTime(time: Long, now: Long = System.currentTimeMillis()): String {
    val clock = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
    return when {
        isSameDay(time, now) -> tr("today, $clock", "сегодня, $clock")
        isSameDay(time, now - DAY) -> tr("yesterday, $clock", "вчера, $clock")
        else -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
    }
}

fun seenAgo(time: Long, now: Long = System.currentTimeMillis()): String {
    if (time <= 0) return tr("not seen yet", "ещё не на связи")
    val minutes = (now - time) / MINUTE
    return when {
        minutes < 1 -> tr("seen just now", "был только что")
        minutes < 60 -> tr("seen $minutes min ago", "был $minutes мин назад")
        minutes < 24 * 60 -> tr("seen ${minutes / 60} h ago", "был ${minutes / 60} ч назад")
        isSameDay(time, now - DAY) -> tr("seen yesterday", "был вчера")
        else -> tr("seen ${shortDate(time)}", "был ${shortDate(time)}")
    }
}

fun dayLabel(time: Long, now: Long = System.currentTimeMillis()): String = when {
    isSameDay(time, now) -> tr("Today", "Сегодня")
    isSameDay(time, now - DAY) -> tr("Yesterday", "Вчера")
    else -> SimpleDateFormat(if (isSameYear(time, now)) "d MMMM" else "d MMMM yyyy", Locale.getDefault()).format(Date(time))
}

fun clockTime(time: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))

fun exactTime(time: Long): String = SimpleDateFormat("d MMMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(time))

fun longDate(time: Long): String = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(time))

private fun shortDate(time: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time))

private fun isSameYear(a: Long, b: Long): Boolean =
    Calendar.getInstance().apply { timeInMillis = a }.get(Calendar.YEAR) == Calendar.getInstance().apply { timeInMillis = b }.get(Calendar.YEAR)

private fun isSameDay(a: Long, b: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = a }
    val second = Calendar.getInstance().apply { timeInMillis = b }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private const val MINUTE = 60_000L
private const val DAY = 24 * 60 * MINUTE
