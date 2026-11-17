package space.bongdo.nit.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

object EventStream {
    const val UNAUTHORIZED = "nit.unauthorized"
    const val OPEN = "nit.open"
    const val CLOSED = "nit.closed"

    fun open(client: OkHttpClient, url: String): Flow<StreamEvent> = channelFlow {
        var attempt = 0
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        while (true) {
            val closed = CompletableDeferred<Unit>()
            var source: EventSource? = null
            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                    attempt = 0
                    trySend(StreamEvent(type = OPEN))
                }

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    val parsed = runCatching {
                        json.decodeFromString<StreamEvent>(data)
                    }.getOrNull() ?: return
                    trySend(parsed)
                }

                override fun onClosed(eventSource: EventSource) {
                    trySend(StreamEvent(type = CLOSED))
                    closed.complete(Unit)
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    if (response?.code == 401) {
                        trySend(StreamEvent(type = UNAUTHORIZED))
                    } else {
                        trySend(StreamEvent(type = CLOSED))
                    }
                    closed.complete(Unit)
                }
            }
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build()
            source = EventSources.createFactory(client).newEventSource(request, listener)
            try {
                closed.await()
            } finally {
                source.cancel()
            }
            attempt += 1
            delay(minOf(30_000L, 1_000L shl minOf(attempt, 5)))
        }
    }
}
