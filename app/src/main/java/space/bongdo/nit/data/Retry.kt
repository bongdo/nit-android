package space.bongdo.nit.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

const val MAX_RETRY_ATTEMPTS = 5

data class RetryPlan(
    val messageID: String,
    val text: String,
    val files: List<UserFile>,
    val retryAfterMs: Long?,
)

private val retryJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

fun isRetryableFailure(error: MessageError?): Boolean {
    if (error == null) return false
    val text = error.message.orEmpty().lowercase()
    if (text.contains("overloaded") || text.contains("retry shortly")) return true
    if (text.contains("rate limit") || text.contains("temporarily unavailable")) return true
    return error.status == 429 || error.status == 503 || error.status == 529
}

fun retryDelay(attempt: Int, retryAfterMs: Long? = null): Long {
    val backoff = minOf(30_000L, 2_000L shl minOf(maxOf(attempt - 1, 0), 4))
    val requested = retryAfterMs?.takeIf { it > 0 } ?: 0L
    return minOf(30_000L, maxOf(backoff, requested))
}

fun retryPlan(
    messages: List<Message>,
    error: MessageError?,
    attempt: Int,
    assistantMessageID: String?,
): RetryPlan? {
    if (!isRetryableFailure(error)) return null
    if (attempt > MAX_RETRY_ATTEMPTS) return null
    val failedAt = assistantMessageID
        ?.let { id -> messages.indexOfFirst { it.id == id } }
        ?: -1
    val turn = if (failedAt >= 0) messages.take(failedAt) else messages
    val user = turn.lastOrNull { it.type == "user" } ?: return null
    if (user.id.startsWith("local-")) return null
    return RetryPlan(
        messageID = user.id,
        text = user.text.orEmpty(),
        files = user.files.orEmpty(),
        retryAfterMs = error?.retryAfterMs,
    )
}

fun errorOf(data: JsonElement?): MessageError? {
    val error = runCatching { data?.jsonObject?.get("error")?.jsonObject }.getOrNull() ?: return null
    return runCatching { retryJson.decodeFromJsonElement(MessageError.serializer(), error) }.getOrNull()
}
