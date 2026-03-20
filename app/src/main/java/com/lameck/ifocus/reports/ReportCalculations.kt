package com.lameck.ifocus.reports

import com.lameck.ifocus.ui.SessionRecord
import com.lameck.ifocus.ui.SessionOutcome
import java.util.Calendar

data class ReportSummary(
    val totalMinutes: Int,
    val completedSessions: Int,
    val interruptedSessions: Int,
    val averageUninterruptedFocusMinutes: Int
)

fun computeSummary(records: List<SessionRecord>): ReportSummary {
    val completed = records.filter { it.outcome == SessionOutcome.DONE }
    return ReportSummary(
        totalMinutes = records.sumOf { it.focusedMinutes },
        completedSessions = completed.size,
        interruptedSessions = records.count { it.outcome != SessionOutcome.DONE },
        averageUninterruptedFocusMinutes = if (completed.isEmpty()) 0 else completed.sumOf { it.focusedMinutes } / completed.size
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

fun breakdownByProject(records: List<SessionRecord>): List<Pair<String, Int>> {
    return records
        .groupBy { it.projectName?.takeIf(String::isNotBlank) ?: "General" }
        .mapValues { (_, sessions) -> sessions.sumOf { it.focusedMinutes } }
        .toList()
        .sortedByDescending { it.second }
}

fun currentStreakDays(
    records: List<SessionRecord>,
    dailyGoalMinutes: Int,
    nowEpochMs: Long = System.currentTimeMillis()
): Int {
    if (dailyGoalMinutes <= 0) return 0
    val byDay = doneMinutesByDay(records)
    var streak = 0
    val cursor = Calendar.getInstance().apply { timeInMillis = startOfDay(nowEpochMs) }
    while (true) {
        val key = dayKey(cursor.timeInMillis)
        val minutes = byDay[key] ?: 0
        if (minutes >= dailyGoalMinutes) {
            streak += 1
            cursor.add(Calendar.DATE, -1)
        } else {
            break
        }
    }
    return streak
}

fun bestStreakDays(records: List<SessionRecord>, dailyGoalMinutes: Int): Int {
    if (dailyGoalMinutes <= 0) return 0
    val byDay = doneMinutesByDay(records)
    if (byDay.isEmpty()) return 0
    val days = byDay.keys.sorted()
    var best = 0
    var run = 0
    var previous: Long? = null
    for (day in days) {
        val metGoal = (byDay[day] ?: 0) >= dailyGoalMinutes
        if (!metGoal) {
            run = 0
            previous = day
            continue
        }

        run = if (previous != null && day == previous + MILLIS_PER_DAY) run + 1 else 1
        best = maxOf(best, run)
        previous = day
    }
    return best
}

private fun doneMinutesByDay(records: List<SessionRecord>): Map<Long, Int> {
    return records
        .asSequence()
        .filter { it.outcome == SessionOutcome.DONE }
        .groupBy { dayKey(it.createdAtEpochMs) }
        .mapValues { (_, sessions) -> sessions.sumOf { it.focusedMinutes } }
}

private fun dayKey(epochMs: Long): Long = startOfDay(epochMs)

private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

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

