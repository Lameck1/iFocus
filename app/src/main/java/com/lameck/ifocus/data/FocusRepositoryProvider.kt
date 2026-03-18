package com.lameck.ifocus.data

import android.content.Context

object FocusRepositoryProvider {
    @Volatile
    private var repository: FocusRepository? = null

    fun get(context: Context): FocusRepository {
        return repository ?: synchronized(this) {
            repository ?: RoomFocusRepository(
                dao = FocusDatabase.getInstance(context).focusDao()
            ).also { created ->
                repository = created
            }
        }
    }
}

