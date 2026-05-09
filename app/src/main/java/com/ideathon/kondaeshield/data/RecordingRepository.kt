package com.ideathon.kondaeshield.data

import android.content.Context
import org.json.JSONArray
import java.io.File

class RecordingRepository(context: Context) {
    private val appContext = context.applicationContext
    private val sessionsDir = File(appContext.filesDir, "sessions")
    private val sessionsFile = File(sessionsDir, "sessions.json")

    @Synchronized
    fun getSessions(): List<RecordingSession> {
        if (!sessionsFile.exists()) return emptyList()

        val content = sessionsFile.readText()
        if (content.isBlank()) return emptyList()

        val array = JSONArray(content)
        return buildList {
            for (index in 0 until array.length()) {
                add(RecordingSession.fromJson(array.getJSONObject(index)))
            }
        }.sortedByDescending { it.createdAtEpochMillis }
    }

    @Synchronized
    fun addSession(session: RecordingSession) {
        val updated = getSessions()
            .filterNot { it.id == session.id }
            .plus(session)
            .sortedByDescending { it.createdAtEpochMillis }
        save(updated)
    }

    @Synchronized
    fun updateSession(session: RecordingSession) {
        val updated = getSessions()
            .map { if (it.id == session.id) session else it }
            .ifEmpty { listOf(session) }
            .sortedByDescending { it.createdAtEpochMillis }
        save(updated)
    }

    @Synchronized
    fun updateSession(sessionId: String, transform: (RecordingSession) -> RecordingSession) {
        val current = getSessions()
        val updated = current.map { session ->
            if (session.id == sessionId) transform(session) else session
        }
        save(updated)
    }

    private fun save(sessions: List<RecordingSession>) {
        sessionsDir.mkdirs()
        val array = JSONArray()
        sessions.forEach { array.put(it.toJson()) }
        sessionsFile.writeText(array.toString(2))
    }
}
