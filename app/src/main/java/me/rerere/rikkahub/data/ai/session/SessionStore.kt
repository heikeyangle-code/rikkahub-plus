package me.rerere.rikkahub.data.ai.session

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages saving and loading session snapshots as JSON files.
 *
 * Snapshots include conversation messages, task states, plan mode state,
 * and fork states — allowing full recovery after app restart.
 */
class SessionStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val sessionDir: File
        get() = File(context.filesDir, "sessions").also { it.mkdirs() }

    /**
     * Save a snapshot of the current session state.
     */
    fun saveSnapshot(snapshot: SessionSnapshot) {
        val file = File(sessionDir, "${snapshot.sessionId}.json")
        file.writeText(json.encodeToString(snapshot))
    }

    /**
     * Load a previously saved snapshot.
     * @return null if no snapshot exists or if it can't be parsed
     */
    fun loadSnapshot(sessionId: String): SessionSnapshot? {
        val file = File(sessionDir, "${sessionId}.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<SessionSnapshot>(file.readText())
        } catch (e: Exception) {
            file.delete() // corrupted, clean up
            null
        }
    }

    /**
     * Delete a snapshot for the given session.
     */
    fun deleteSnapshot(sessionId: String) {
        File(sessionDir, "${sessionId}.json").delete()
    }

    /**
     * List all session IDs that have saved snapshots.
     */
    fun listSnapshots(): List<String> {
        return sessionDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    /**
     * Delete all snapshots.
     */
    fun clearAllSnapshots() {
        sessionDir.listFiles()?.forEach { it.delete() }
    }
}
