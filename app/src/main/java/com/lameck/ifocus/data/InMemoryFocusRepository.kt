package com.lameck.ifocus.data

import com.lameck.ifocus.ui.FocusTask
import com.lameck.ifocus.ui.FocusProject
import com.lameck.ifocus.ui.InterruptionReason
import com.lameck.ifocus.ui.ActiveSession
import com.lameck.ifocus.ui.AppSettings
import com.lameck.ifocus.ui.SessionRecord

class InMemoryFocusRepository : FocusRepository {
    private val tasks = mutableListOf<FocusTask>()
    private val sessions = mutableListOf<SessionRecord>()
    private val projects = mutableListOf<FocusProject>()
    private val interruptionCounts = mutableMapOf<InterruptionReason, Int>()
    private var activeSession: ActiveSession? = null
    private var appSettings: AppSettings = AppSettings()

    override suspend fun loadTasks(): List<FocusTask> = tasks.toList()

    override suspend fun loadSessionHistory(limit: Int): List<SessionRecord> = sessions.takeLast(limit)

    override suspend fun loadInterruptionCounts(): Map<InterruptionReason, Int> = interruptionCounts.toMap()

    override suspend fun upsertTask(task: FocusTask) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
        } else {
            tasks.add(task)
        }
    }

    override suspend fun upsertTasks(tasks: List<FocusTask>) {
        this.tasks.clear()
        this.tasks.addAll(tasks)
    }

    override suspend fun insertSession(record: SessionRecord) {
        sessions.add(record)
    }

    override suspend fun upsertInterruptionCounts(counts: Map<InterruptionReason, Int>) {
        interruptionCounts.clear()
        interruptionCounts.putAll(counts)
    }

    override suspend fun loadActiveSession(): ActiveSession? = activeSession

    override suspend fun upsertActiveSession(session: ActiveSession) {
        activeSession = session
    }

    override suspend fun clearActiveSession() {
        activeSession = null
    }

    override suspend fun loadProjects(): List<FocusProject> = projects.toList()

    override suspend fun upsertProject(project: FocusProject) {
        val index = projects.indexOfFirst { it.name == project.name }
        if (index >= 0) {
            projects[index] = project
        } else {
            projects.add(project)
        }
    }

    override suspend fun upsertProjects(projects: List<FocusProject>) {
        this.projects.clear()
        this.projects.addAll(projects)
    }

    override suspend fun deleteProject(name: String) {
        projects.removeAll { it.name == name }
    }

    override suspend fun loadAppSettings(): AppSettings = appSettings

    override suspend fun upsertAppSettings(settings: AppSettings) {
        appSettings = settings
    }
}

