package space.bongdo.nit.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.draftData by preferencesDataStore("nit-drafts")

data class DraftState(val text: String = "", val updatedAt: Long = 0)

class DraftStore(private val context: Context) {
    private val prefix = "draft."

    val keys: Flow<Set<String>> = context.draftData.data.map { prefs ->
        prefs.asMap().keys.mapNotNull { key ->
            key.name.takeIf { it.startsWith(prefix) && !it.endsWith(".updated") }
                ?.removePrefix(prefix)
                ?.takeIf { prefs[stringPreferencesKey(it)]?.isNotBlank() == true }
        }.toSet()
    }

    suspend fun get(key: String): DraftState {
        if (key.isBlank()) return DraftState()
        val prefs = context.draftData.data.first()
        return DraftState(
            text = prefs[stringPreferencesKey(prefix + key)].orEmpty(),
            updatedAt = prefs[longPreferencesKey(prefix + key + ".updated")] ?: 0,
        )
    }

    suspend fun getFiles(key: String): List<UserFile> {
        if (key.isBlank()) return emptyList()
        val raw = context.draftData.data.first()[stringPreferencesKey(prefix + key + ".files")] ?: return emptyList()
        return runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<List<UserFile>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun setFiles(key: String, files: List<UserFile>) {
        if (key.isBlank()) return
        context.draftData.edit { prefs ->
            if (files.isEmpty()) prefs.remove(stringPreferencesKey(prefix + key + ".files"))
            else prefs[stringPreferencesKey(prefix + key + ".files")] =
                Json { ignoreUnknownKeys = true }.encodeToString(files)
        }
    }

    suspend fun set(key: String, text: String, updatedAt: Long = System.currentTimeMillis()) {
        if (key.isBlank()) return
        context.draftData.edit { prefs ->
            if (text.isBlank()) prefs.remove(stringPreferencesKey(prefix + key))
            else prefs[stringPreferencesKey(prefix + key)] = text
            prefs[longPreferencesKey(prefix + key + ".updated")] = updatedAt
        }
    }
}
