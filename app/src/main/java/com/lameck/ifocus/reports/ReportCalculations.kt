package com.lameck.ifocus.reports

import com.lameck.ifocus.ui.SessionRecord
import com.lameck.ifocus.ui.SessionOutcome
import java.util.Calendar

data class ReportSummary(
    val totalMinutes: Int,
    val completedSessions: Int,
    val interruptedSessions: Int
)

fun computeSummary(records: List<SessionRecord>): ReportSummary {
    return ReportSummary(
        totalMinutes = records.sumOf { it.focusedMinutes },
        completedSessions = records.count { it.outcome == SessionOutcome.DONE },
        interruptedSessions = records.count { it.outcome != SessionOutcome.DONE }
    )
}

fun recordsForToday(records: List<SessionRecord>, nowEpochMs: Long = System.currentTimeMillis()): List<SessionRecord> {
    val start = startOfDay(nowEpochMs)
    return records.filter { it.createdAtEpochMs >= start }
}

fun recordsForCurrentWeek(records: List<SessionRecord>, nowEpochMs: Long = System.currentTimeMillis()): List<SessionRecord> {
    val start = startOfWeek(nowEpochMs)
    return records.filter { it.createdAtEpochMs >= start }
}

fun breakdownByTask(records: List<SessionRecord>): List<Pair<String, Int>> {
    return records
        .groupBy { it.taskTitle }
        .mapValues { (_, sessions) -> sessions.sumOf { it.focusedMinutes } }
        .toList()
        .sortedByDescending { it.second }
}

private fun startOfDay(nowEpochMs: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = nowEpochMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun startOfWeek(nowEpochMs: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = nowEpochMs
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

