package space.bongdo.nit.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
)

class SecureStore(context: Context) {
    private val prefs = create(context)

    private fun create(context: Context): android.content.SharedPreferences {
        fun open() = EncryptedSharedPreferences.create(
            context,
            "nit-secrets",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return runCatching { open() }.getOrElse {
            context.deleteSharedPreferences("nit-secrets")
            open()
        }
    }

    val cookies: PersistentCookieJar = PersistentCookieJar(
        read = { prefs.getString("cookies", null) },
        write = { value ->
            prefs.edit().apply {
                if (value == null) remove("cookies") else putString("cookies", value)
            }.apply()
        },
    )
}

class PersistentCookieJar(
    private val read: () -> String?,
    private val write: (String?) -> Unit,
) : CookieJar {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var cache: MutableList<Cookie>? = null

    private fun readCookies(): MutableList<Cookie> {
        cache?.let { return it }
        val raw = read() ?: return mutableListOf<Cookie>().also { cache = it }
        val records = runCatching {
            json.decodeFromString<List<StoredCookie>>(raw)
        }.getOrElse { emptyList() }
        return records.mapNotNull { record ->
            runCatching {
                Cookie.Builder()
                    .name(record.name)
                    .value(record.value)
                    .domain(record.domain)
                    .path(record.path)
                    .expiresAt(record.expiresAt)
                    .apply {
                        if (record.secure) secure()
                        if (record.httpOnly) httpOnly()
                        if (record.hostOnly) hostOnlyDomain(record.domain)
                    }
                    .build()
            }.getOrNull()
        }.toMutableList().also { cache = it }
    }

    private fun writeCookies(cookies: List<Cookie>) {
        cache = cookies.toMutableList()
        val records = cookies.map {
            StoredCookie(
                name = it.name,
                value = it.value,
                domain = it.domain,
                path = it.path,
                expiresAt = it.expiresAt,
                secure = it.secure,
                httpOnly = it.httpOnly,
                hostOnly = it.hostOnly,
            )
        }
        write(json.encodeToString<List<StoredCookie>>(records))
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        val all = readCookies()
        val live = all.filter { it.expiresAt > now && it.matches(url) }
        if (live.size != all.size) writeCookies(live)
        live
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val current = readCookies().filter { it.expiresAt > now }.toMutableList()
        for (cookie in cookies) {
            current.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > now) current.add(cookie)
        }
        writeCookies(current)
    }

    fun hasSession(url: HttpUrl): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        readCookies().any {
            it.expiresAt > now &&
                it.matches(url) &&
                (it.name == "nit_gate" || it.name == "gate_session")
        }
    }

    fun clear() = synchronized(lock) {
        cache = mutableListOf()
        write(null)
    }
}
