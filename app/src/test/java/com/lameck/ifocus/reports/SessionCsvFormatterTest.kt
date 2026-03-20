package com.lameck.ifocus.reports

import com.lameck.ifocus.ui.SessionOutcome
import com.lameck.ifocus.ui.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCsvFormatterTest {

    @Test
    fun `format returns header when no records`() {
        val csv = SessionCsvFormatter.format(emptyList())

        assertEquals("id,taskId,taskTitle,projectName,focusedMinutes,outcome,createdAtEpochMs", csv)
    }

    @Test
    fun `format escapes task title and emits rows`() {
        val csv = SessionCsvFormatter.format(
            listOf(
                SessionRecord(
                    id = "1",
                    taskId = "task-1",
                    taskTitle = "Client \"A\" proposal",
                    projectName = "Client A",
                    focusedMinutes = 25,
                    outcome = SessionOutcome.DONE,
                    createdAtEpochMs = 1234L
                )
            )
        )

        assertEquals(
            "id,taskId,taskTitle,projectName,focusedMinutes,outcome,createdAtEpochMs\n\"1\",\"task-1\",\"Client \"\"A\"\" proposal\",\"Client A\",25,DONE,1234",
            csv
        )
    }
}

