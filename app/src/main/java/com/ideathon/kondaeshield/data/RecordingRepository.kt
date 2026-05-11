package com.ideathon.kondaeshield.data

import android.content.Context
import com.ideathon.kondaeshield.BuildConfig
import org.json.JSONArray
import java.io.File

class RecordingRepository(context: Context) {
    private val appContext = context.applicationContext
    private val sessionsDir = File(appContext.filesDir, "sessions")
    private val sessionsFile = File(sessionsDir, "sessions.json")
    private val debugSamplesMarkerFile = File(sessionsDir, "debug-samples-seeded.flag")

    @Synchronized
    fun getSessions(): List<RecordingSession> {
        return seedDebugSampleSessionsIfNeeded(loadSessionsFromDisk())
            .sortedByDescending { it.createdAtEpochMillis }
    }

    private fun loadSessionsFromDisk(): List<RecordingSession> {
        if (!sessionsFile.exists()) return emptyList()

        val content = sessionsFile.readText()
        if (content.isBlank()) return emptyList()

        val array = JSONArray(content)
        return buildList {
            for (index in 0 until array.length()) {
                add(RecordingSession.fromJson(array.getJSONObject(index)))
            }
        }
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

    @Synchronized
    fun deleteSession(sessionId: String): Boolean {
        val current = getSessions()
        val session = current.firstOrNull { it.id == sessionId } ?: return false
        val updated = current.filterNot { it.id == sessionId }

        deleteRecordingFile(session)
        save(updated)

        return true
    }

    private fun deleteRecordingFile(session: RecordingSession) {
        val path = session.audioFilePath.takeIf { it.isNotBlank() } ?: return
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return
        val filesRoot = runCatching { appContext.filesDir.canonicalFile }.getOrNull() ?: return

        if (!target.path.startsWith(filesRoot.path + File.separator)) return
        if (target.exists()) {
            target.delete()
        }
    }

    private fun save(sessions: List<RecordingSession>) {
        sessionsDir.mkdirs()
        val array = JSONArray()
        sessions.forEach { array.put(it.toJson()) }
        sessionsFile.writeText(array.toString(2))
    }

    private fun seedDebugSampleSessionsIfNeeded(sessions: List<RecordingSession>): List<RecordingSession> {
        if (!BuildConfig.DEBUG || debugSamplesAlreadySeeded()) return sessions

        val sampleFileNames = runCatching {
            appContext.assets.list(DEBUG_SAMPLE_TRANSCRIPTS_DIR)
                .orEmpty()
                .filter { it.endsWith(".txt") }
                .sorted()
        }.getOrDefault(emptyList())

        if (sampleFileNames.isEmpty()) return sessions

        val existingIds = sessions.map { it.id }.toSet()
        val now = System.currentTimeMillis()
        val sampleSessions = sampleFileNames.mapIndexedNotNull { index, fileName ->
            val transcript = runCatching { readDebugSampleTranscript(fileName) }
                .getOrDefault("")
                .takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            val id = "debug-sample-${fileName.substringBeforeLast(".")}"

            if (id in existingIds) return@mapIndexedNotNull null

            RecordingSession(
                id = id,
                createdAtEpochMillis = now - (index * DEBUG_SAMPLE_TIME_GAP_MILLIS),
                audioFilePath = "",
                state = ProcessingState.COMPLETE,
                transcript = transcript,
            )
        }

        sessionsDir.mkdirs()
        debugSamplesMarkerFile.writeText(DEBUG_SAMPLE_SET_MARKER)

        if (sampleSessions.isEmpty()) return sessions

        val updated = sessions.plus(sampleSessions)
        save(updated)
        return updated
    }

    private fun debugSamplesAlreadySeeded(): Boolean =
        debugSamplesMarkerFile.exists() &&
            runCatching { debugSamplesMarkerFile.readText().trim() }
                .getOrDefault("") == DEBUG_SAMPLE_SET_MARKER

    private fun readDebugSampleTranscript(fileName: String): String =
        appContext.assets.open("$DEBUG_SAMPLE_TRANSCRIPTS_DIR/$fileName")
            .bufferedReader()
            .use { it.readText().trim() }

    private companion object {
        private const val DEBUG_SAMPLE_TRANSCRIPTS_DIR = "sample_transcripts"
        private const val DEBUG_SAMPLE_SET_MARKER = "sampleSetVersion=2"
        private const val DEBUG_SAMPLE_TIME_GAP_MILLIS = 60L * 60L * 1000L
    }
}
