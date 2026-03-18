package com.lameck.ifocus.session.usecase

import com.lameck.ifocus.ui.ActiveSession

sealed interface SessionActionResult {
    data object NoChange : SessionActionResult
    data class Updated(val activeSession: ActiveSession?) : SessionActionResult
}

