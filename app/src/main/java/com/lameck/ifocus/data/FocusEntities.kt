package com.lameck.ifocus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_tasks")
data class FocusTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val estimateMinutes: Int,
    val priority: String,
    val status: String,
    val todayMinutesFocused: Int,
    val notes: String,
    val isArchived: Boolean,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "session_records")
data class SessionRecordEntity(
    @PrimaryKey val id: String,
    val taskTitle: String,
    val focusedMinutes: Int,
    val outcome: String,
    val createdAtEpochMs: Long
)

@Entity(tableName = "interruption_counts")
data class InterruptionCountEntity(
    @PrimaryKey val reason: String,
    val count: Int
)

@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val mode: String,  // "FOCUS", "SHORT_BREAK", "LONG_BREAK"
    val startedAtMs: Long,  // SystemClock.elapsedRealtime()
    val scheduledCompletionMs: Long,
    val isPaused: Boolean,
    val pausedAtMs: Long,
    val pausedRemainingSecs: Int
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: String = "singleton",
    val themePreference: String,
    val autoStartBreak: Boolean,
    val autoStartFocus: Boolean
)

