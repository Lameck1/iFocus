package com.lameck.ifocus.reports

import com.lameck.ifocus.ui.SessionRecord

object SessionCsvFormatter {

    fun format(records: List<SessionRecord>): String {
        val header = "id,taskId,taskTitle,projectName,focusedMinutes,outcome,createdAtEpochMs"
        if (records.isEmpty()) return header

        val rows = records.joinToString(separator = "\n") { record ->
            listOf(
                escape(record.id),
                escape(record.taskId.orEmpty()),
                escape(record.taskTitle),
                escape(record.projectName.orEmpty()),
                record.focusedMinutes.toString(),
                record.outcome.name,
                record.createdAtEpochMs.toString()
            ).joinToString(separator = ",")
        }
        return "$header\n$rows"
    }

    private fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}

