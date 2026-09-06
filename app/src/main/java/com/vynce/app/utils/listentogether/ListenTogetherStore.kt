package com.vynce.app.utils.listentogether

import android.content.Context
import android.os.Build

object ListenTogetherStore {
    private const val PREFS = "listen_together_session"
    private const val KEY_CODE = "code"
    private const val KEY_PARTICIPANT_ID = "participant_id"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_IS_HOST = "is_host"

    fun defaultName(): String =
        Build.MODEL
            ?.takeIf { it.isNotBlank() }
            ?.let { "Vynce on $it" }
            ?: "Vynce Listener"

    fun save(
        context: Context,
        session: ListenTogetherSession,
        displayName: String,
        isHost: Boolean,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE, session.code)
            .putString(KEY_PARTICIPANT_ID, session.participantId)
            .putString(KEY_DISPLAY_NAME, displayName.ifBlank { defaultName() })
            .putBoolean(KEY_IS_HOST, isHost)
            .apply()
    }

    fun load(context: Context): SavedListenTogetherSession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_CODE, null)?.takeIf { it.isNotBlank() } ?: return null
        val participantId = prefs.getString(KEY_PARTICIPANT_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        return SavedListenTogetherSession(
            code = code,
            participantId = participantId,
            displayName = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: defaultName(),
            isHost = prefs.getBoolean(KEY_IS_HOST, false),
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

data class SavedListenTogetherSession(
    val code: String,
    val participantId: String,
    val displayName: String,
    val isHost: Boolean,
)
