package space.bongdo.nit.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedChat(
    val messages: List<Message> = emptyList(),
    val previous: String? = null,
    val updatedAt: Long = 0,
)

@Serializable
data class CachedBoot(
    val server: String = "",
    val sessions: List<Session> = emptyList(),
    val chats: Map<String, CachedChat> = emptyMap(),
    val lastSession: String = "",
    val savedAt: Long = 0,
)

class ChatCache(context: Context) {
    private val file = File(context.filesDir, FILE)
    private val writeLock = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    suspend fun read(server: String): CachedBoot? = withContext(Dispatchers.IO) {
        if (server.isBlank() || !file.isFile) return@withContext null
        runCatching { json.decodeFromString<CachedBoot>(file.readText()) }
            .getOrNull()
            ?.takeIf { it.server == server && it.sessions.isNotEmpty() }
    }

    suspend fun write(boot: CachedBoot) = writeLock.withLock {
        withContext(Dispatchers.IO) {
            if (boot.server.isBlank()) return@withContext
            var value = trim(boot)
            var text = json.encodeToString(value)
            if (text.length > MAX_BYTES) {
                val oldest = value.sessions.sortedBy { it.time.updated }.map { it.id }
                for (id in oldest) {
                    if (text.length <= MAX_BYTES) break
                    value = value.copy(chats = value.chats - id)
                    text = json.encodeToString(value)
                }
            }
            val temp = File(file.parentFile, "$FILE.tmp")
            runCatching {
                temp.writeText(text)
                if (!temp.renameTo(file)) {
                    file.writeText(text)
                    temp.delete()
                }
            }
        }
    }

    suspend fun clear() = writeLock.withLock {
        withContext(Dispatchers.IO) {
            file.delete()
            File(file.parentFile, "$FILE.tmp").delete()
        }
    }

    companion object {
        const val MAX_CHATS = 24
        const val MAX_MESSAGES = 60
        const val MAX_BYTES = 1_500_000
        private const val FILE = "chats.json"

        fun trim(boot: CachedBoot): CachedBoot {
            val known = boot.sessions.mapTo(mutableSetOf()) { it.id }
            val order = boot.sessions.sortedByDescending { it.time.updated }.map { it.id }
            val keep = order.take(MAX_CHATS).toSet()
            val chats = boot.chats
                .filterKeys { it in known && it in keep }
                .mapValues { (_, chat) -> chat.copy(messages = chat.messages.takeLast(MAX_MESSAGES)) }
            return boot.copy(chats = chats)
        }

        fun lighten(messages: List<Message>): List<Message> = messages.map { message ->
            if (message.type != "assistant") return@map message
            val content = message.content.orEmpty()
            val removed = content.filter { part ->
                part.type == "reasoning" || (part.type == "tool" && part.name != "question")
            }
            val kept = content.filterNot { part ->
                part.type == "reasoning" || (part.type == "tool" && part.name != "question")
            }
            val steps = (message.steps ?: 0).coerceAtLeast(removed.size)
            val tools = (message.tools ?: 0).coerceAtLeast(removed.count { it.type == "tool" })
            val errors = (message.errors ?: 0).coerceAtLeast(
                removed.count { it.type == "tool" && it.state?.status == "error" },
            )
            message.copy(
                content = kept,
                steps = steps.takeIf { it > 0 },
                tools = tools.takeIf { it > 0 },
                errors = errors.takeIf { it > 0 },
                edited = null,
            )
        }
    }
}
