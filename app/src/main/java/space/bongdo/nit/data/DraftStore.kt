package space.bongdo.nit.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.draftData by preferencesDataStore("nit-drafts")

class DraftStore(private val context: Context) {
    private val prefix = "draft."

    val keys: Flow<Set<String>> = context.draftData.data.map { prefs ->
        prefs.asMap().keys.mapNotNull { key ->
            key.name.takeIf { it.startsWith(prefix) && it.length > prefix.length }?.removePrefix(prefix)
        }.toSet()
    }

    suspend fun get(key: String): String {
        if (key.isBlank()) return ""
        return context.draftData.data.first()[stringPreferencesKey(prefix + key)] ?: ""
    }

    suspend fun set(key: String, text: String) {
        if (key.isBlank()) return
        context.draftData.edit { prefs ->
            if (text.isBlank()) prefs.remove(stringPreferencesKey(prefix + key))
            else prefs[stringPreferencesKey(prefix + key)] = text
        }
    }
}
