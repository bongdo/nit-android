package space.bongdo.nit.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("nit-settings")

data class AppSettings(
    val server: String = "https://bongdo.space/nit",
    val theme: String = "system",
    val fontScale: Float = 1f,
    val lastDirectory: String = "",
    val lastSession: String = "",
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val server = stringPreferencesKey("server")
        val theme = stringPreferencesKey("theme")
        val fontScale = floatPreferencesKey("fontScale")
        val lastDirectory = stringPreferencesKey("lastDirectory")
        val lastSession = stringPreferencesKey("lastSession")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            server = prefs[Keys.server] ?: "https://bongdo.space/nit",
            theme = prefs[Keys.theme] ?: "system",
            fontScale = prefs[Keys.fontScale] ?: 1f,
            lastDirectory = prefs[Keys.lastDirectory] ?: "",
            lastSession = prefs[Keys.lastSession] ?: "",
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                server = prefs[Keys.server] ?: "https://bongdo.space/nit",
                theme = prefs[Keys.theme] ?: "system",
                fontScale = prefs[Keys.fontScale] ?: 1f,
                lastDirectory = prefs[Keys.lastDirectory] ?: "",
                lastSession = prefs[Keys.lastSession] ?: "",
            )
            val next = transform(current)
            prefs[Keys.server] = next.server
            prefs[Keys.theme] = next.theme
            prefs[Keys.fontScale] = next.fontScale
            prefs[Keys.lastDirectory] = next.lastDirectory
            prefs[Keys.lastSession] = next.lastSession
        }
    }
}
