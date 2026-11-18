package space.bongdo.nit.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

class NitApi(
    private val jar: PersistentCookieJar,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    },
) {
    var server: ServerAddress? = null
        private set

    private var origin: HttpUrl? = null

    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val streamClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun useServer(address: ServerAddress) {
        server = address
        origin = address.origin.toHttpUrl()
    }

    fun hasSession(): Boolean {
        val url = origin ?: return false
        return jar.hasSession(url)
    }

    fun streamClient(): OkHttpClient = streamClient

    fun eventsUrl(): String = api("/api/events")

    suspend fun login(user: String, password: String, code: String): Unit = withContext(Dispatchers.IO) {
        val address = server ?: throw ApiException("сервер не настроен")
        val body = FormBody.Builder()
            .add("name", user)
            .add("next", address.basePath.ifEmpty { "" } + "/")
            .apply {
                if (password.isNotEmpty()) add("password", password)
                if (code.isNotEmpty()) add("code", code)
            }
            .build()
        val loginClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val request = Request.Builder()
            .url(address.loginUrl)
            .header("Accept", "text/html")
            .post(body)
            .build()
        loginClient.newCall(request).execute().use { response ->
            when {
                response.code == 303 || response.code == 302 || response.code == 307 ->
                    if (!jar.hasSession(address.origin.toHttpUrl())) {
                        throw ApiException("сервер не выдал сессию", response.code)
                    }
                response.code == 401 -> throw ApiException("неверное имя, код или пароль", 401)
                response.code == 429 -> throw ApiException("слишком много попыток, подождите", 429)
                response.isSuccessful -> throw ApiException("сервер не принял вход", response.code)
                else -> throw ApiException("вход не удался (${response.code})", response.code)
            }
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val address = server ?: return@withContext
        runCatching {
            client.newCall(
                Request.Builder()
                    .url(address.origin + "/logout")
                    .post(FormBody.Builder().build())
                    .build(),
            ).execute().close()
        }
        Unit
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        call(Request.Builder().url(api("/api/health")).get().build()).use { true }
    }

    suspend fun sessions(search: String? = null): List<Session> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Session>()
        var cursor: String? = null
        for (page in 0 until 100) {
            val url = httpUrl("/api/sessions").newBuilder().apply {
                if (!search.isNullOrBlank()) addQueryParameter("search", search)
                if (cursor != null) addQueryParameter("cursor", cursor)
            }.build()
            val response = call(Request.Builder().url(url).get().build())
            val parsed = json.decodeFromString<SessionList>(response.body?.string().orEmpty())
            out += parsed.data
            cursor = parsed.cursor?.next
            if (parsed.data.size < 100 || cursor.isNullOrBlank()) break
        }
        out
    }

    suspend fun session(id: String): Session = withContext(Dispatchers.IO) {
        val response = call(Request.Builder().url(api("/api/sessions/$id")).get().build())
        json.decodeFromString<SessionEnvelope>(response.body?.string().orEmpty()).data
    }

    suspend fun messages(
        id: String,
        before: String? = null,
        limit: Int = 60,
        light: Boolean = true,
    ): MessagesPage = withContext(Dispatchers.IO) {
            val url = httpUrl("/api/sessions/$id/messages").newBuilder().apply {
                addQueryParameter("limit", limit.toString())
                if (before != null) addQueryParameter("before", before)
                if (light) addQueryParameter("light", "1")
            }.build()
            val response = call(Request.Builder().url(url).get().build())
            json.decodeFromString<MessagesPage>(response.body?.string().orEmpty())
        }

    suspend fun createSession(directory: String, title: String? = null): Session =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("directory", directory)
                if (!title.isNullOrBlank()) put("title", title)
            }
            val response = call(
                Request.Builder()
                    .url(api("/api/sessions"))
                    .post(body.toString().toRequestBody(JSON))
                    .build(),
            )
            json.decodeFromString<SessionEnvelope>(response.body?.string().orEmpty()).data
        }

    suspend fun prompt(
        id: String,
        text: String,
        files: List<UserFile> = emptyList(),
        delivery: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("text", text)
            if (delivery == "queue" || delivery == "steer") put("delivery", delivery)
            if (files.isNotEmpty()) {
                put(
                    "files",
                    buildJsonArray {
                        files.forEach { file ->
                            add(
                                buildJsonObject {
                                    put("uri", file.uri.orEmpty())
                                    if (!file.name.isNullOrBlank()) put("name", file.name)
                                },
                            )
                        }
                    },
                )
            }
        }
        call(
            Request.Builder()
                .url(api("/api/sessions/$id/prompt"))
                .post(payload.toString().toRequestBody(JSON))
                .build(),
        ).use { response ->
            runCatching {
                json.parseToJsonElement(response.body?.string().orEmpty())
                    .jsonObject["data"]?.jsonObject?.get("id")?.toString()?.trim('"')
            }.getOrNull()
        }
    }

    suspend fun interrupt(id: String) = post("/api/sessions/$id/interrupt")

    suspend fun replyPermission(id: String, requestID: String, decision: String) = post(
        "/api/sessions/$id/permissions/$requestID/reply",
        buildJsonObject { put("decision", decision) },
    )

    suspend fun permissions(id: String): List<PermissionRequest> = withContext(Dispatchers.IO) {
        val response = call(Request.Builder().url(api("/api/sessions/$id/permissions")).get().build())
        parseDataList<PermissionRequest>(response.body?.string().orEmpty())
    }

    suspend fun forms(id: String): List<FormInfo> = withContext(Dispatchers.IO) {
        val response = call(Request.Builder().url(api("/api/sessions/$id/form")).get().build())
        parseDataList<FormInfo>(response.body?.string().orEmpty())
    }

    suspend fun replyForm(id: String, formID: String, answer: JsonObject) = post(
        "/api/sessions/$id/form/$formID/reply",
        buildJsonObject { put("answer", answer) },
    )

    suspend fun cancelForm(id: String, formID: String) = withContext(Dispatchers.IO) {
        call(
            Request.Builder()
                .url(api("/api/sessions/$id/form/$formID"))
                .delete()
                .build(),
        )
        Unit
    }

    suspend fun draft(key: String): DraftResponse = withContext(Dispatchers.IO) {
        val url = httpUrl("/api/ui/drafts").newBuilder().addQueryParameter("key", key).build()
        val response = call(Request.Builder().url(url).get().build())
        json.decodeFromString<DraftResponse>(response.body?.string().orEmpty())
    }

    suspend fun setDraft(key: String, text: String) {
        post(
            "/api/ui/drafts",
            buildJsonObject {
                put("key", key)
                put("text", text)
            },
        )
    }

    suspend fun uiState(): RemoteUiState = withContext(Dispatchers.IO) {
        val response = call(Request.Builder().url(api("/api/ui")).get().build())
        runCatching {
            json.decodeFromString<RemoteUiState>(response.body?.string().orEmpty())
        }.getOrDefault(RemoteUiState())
    }

    suspend fun hiddenSessions(): Set<String> = uiState().hidden.toSet()

    suspend fun projects(): List<Project> = withContext(Dispatchers.IO) {
        val response = call(Request.Builder().url(api("/api/projects")).get().build())
        parseListOrData<Project>(response.body?.string().orEmpty())
    }

    suspend fun folders(path: String?): FolderListing = withContext(Dispatchers.IO) {
        val url = httpUrl("/api/fs").newBuilder().apply {
            if (!path.isNullOrBlank()) addQueryParameter("path", path)
        }.build()
        val response = call(Request.Builder().url(url).get().build())
        json.decodeFromString<FolderListing>(response.body?.string().orEmpty())
    }

    suspend fun models(directory: String?): List<Model> = withContext(Dispatchers.IO) {
        val url = httpUrl("/api/models").newBuilder().apply {
            if (!directory.isNullOrBlank()) addQueryParameter("dir", directory)
        }.build()
        val response = call(Request.Builder().url(url).get().build())
        parseDataList<Model>(response.body?.string().orEmpty())
    }

    suspend fun agents(): List<Agent> = withContext(Dispatchers.IO) {
        val response = call(Request.Builder().url(api("/api/agents")).get().build())
        parseDataList<Agent>(response.body?.string().orEmpty())
    }

    suspend fun children(id: String): List<Session> = withContext(Dispatchers.IO) {
        val response = call(Request.Builder().url(api("/api/sessions/$id/children")).get().build())
        json.decodeFromString<SessionList>(response.body?.string().orEmpty()).data
    }

    suspend fun compact(id: String) = post("/api/sessions/$id/compact")

    suspend fun revert(id: String, messageID: String) = post(
        "/api/sessions/$id/revert",
        buildJsonObject { put("messageID", messageID) },
    )

    suspend fun fork(id: String, before: String? = null): Session = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            if (!before.isNullOrBlank()) put("before", before)
        }
        val response = call(
            Request.Builder()
                .url(api("/api/sessions/$id/fork"))
                .post(body.toString().toRequestBody(JSON))
                .build(),
        )
        json.decodeFromString<SessionEnvelope>(response.body?.string().orEmpty()).data
    }

    suspend fun artifact(sessionID: String, path: String): String = withContext(Dispatchers.IO) {
        val url = httpUrl("/api/artifact").newBuilder()
            .addQueryParameter("session", sessionID)
            .addQueryParameter("path", path)
            .build()
        val response = call(Request.Builder().url(url).get().build())
        json.decodeFromString<ArtifactResponse>(response.body?.string().orEmpty()).html
    }

    suspend fun viewBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        val url = httpUrl("/api/view").newBuilder().addQueryParameter("path", path).build()
        call(Request.Builder().url(url).get().build()).use { it.body?.bytes() ?: ByteArray(0) }
    }

    fun mediaUrl(path: String): String {
        val endpoint = if (path.startsWith("/tmp/nit-gui-")) "/api/file" else "/api/view"
        return httpUrl(endpoint).newBuilder().addQueryParameter("path", path).build().toString()
    }

    fun cookieHeader(url: String): String = runCatching {
        jar.loadForRequest(url.toHttpUrl()).joinToString("; ") { "${it.name}=${it.value}" }
    }.getOrDefault("")

    suspend fun uploadedBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        val url = httpUrl("/api/file").newBuilder().addQueryParameter("path", path).build()
        call(Request.Builder().url(url).get().build()).use { it.body?.bytes() ?: ByteArray(0) }
    }

    suspend fun switchModel(id: String, model: ModelRef) = post(
        "/api/sessions/$id/model",
        buildJsonObject { put("model", json.encodeToJsonElement(model)) },
    )

    suspend fun switchAgent(id: String, agent: String) = post(
        "/api/sessions/$id/agent",
        buildJsonObject { put("agent", agent) },
    )

    suspend fun upload(file: File, name: String, directory: String, mime: String): UploadedFile =
        withContext(Dispatchers.IO) {
            val url = httpUrl("/api/upload").newBuilder()
                .addQueryParameter("name", name)
                .addQueryParameter("dir", directory)
                .build()
            val response = call(
                Request.Builder()
                    .url(url)
                    .post(file.asRequestBody(mime.toMediaType()))
                    .build(),
            )
            json.decodeFromString<UploadedFile>(response.body?.string().orEmpty())
        }

    private suspend fun post(path: String, body: JsonElement = JsonObject(emptyMap())): Unit =
        withContext(Dispatchers.IO) {
            call(
                Request.Builder()
                    .url(api(path))
                    .post(body.toString().toRequestBody(JSON))
                    .build(),
            ).use { }
            Unit
        }

    private fun call(request: Request): Response {
        val response = client.newCall(request).execute()
        if (response.code == 401) {
            response.close()
            throw UnauthorizedException()
        }
        if (!response.isSuccessful) {
            val message = errorText(response)
            throw ApiException(message, response.code)
        }
        return response
    }

    private fun errorText(response: Response): String {
        val text = runCatching { response.body?.string().orEmpty() }.getOrElse { "" }
        return runCatching {
            val body = json.decodeFromString<ApiErrorBody>(text)
            body.error ?: body.message
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "ошибка ${response.code}"
    }

    private inline fun <reified T> parseDataList(body: String): List<T> {
        val root = json.parseToJsonElement(body)
        val element: JsonElement = if (root is JsonObject && root["data"] != null) root["data"]!! else root
        return json.decodeFromJsonElement(element)
    }

    private inline fun <reified T> parseListOrData(body: String): List<T> = parseDataList(body)

    private fun httpUrl(path: String): HttpUrl {
        val address = server ?: throw ApiException("сервер не настроен")
        return (address.apiRoot + path).toHttpUrl()
    }

    private fun api(path: String): String = httpUrl(path).toString()

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}

@kotlinx.serialization.Serializable
private data class SessionEnvelope(val data: Session)

@kotlinx.serialization.Serializable
private data class ArtifactResponse(val path: String = "", val html: String = "")
