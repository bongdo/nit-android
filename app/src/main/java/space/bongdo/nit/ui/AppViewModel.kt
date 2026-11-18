package space.bongdo.nit.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.request.ImageRequest
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import space.bongdo.nit.data.Agent
import space.bongdo.nit.data.ApiException
import space.bongdo.nit.data.AppSettings
import space.bongdo.nit.data.CachedBoot
import space.bongdo.nit.data.CachedChat
import space.bongdo.nit.data.ChatCache
import space.bongdo.nit.data.DraftStore
import space.bongdo.nit.data.EventStream
import space.bongdo.nit.data.FolderListing
import space.bongdo.nit.data.FormInfo
import space.bongdo.nit.data.MAX_RETRY_ATTEMPTS
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.MessageMerge
import space.bongdo.nit.data.MessageTime
import space.bongdo.nit.data.Model
import space.bongdo.nit.data.ModelRef
import space.bongdo.nit.data.NitApi
import space.bongdo.nit.data.PermissionRequest
import space.bongdo.nit.data.Project
import space.bongdo.nit.data.RetryPlan
import space.bongdo.nit.data.SecureStore
import space.bongdo.nit.data.ServerAddress
import space.bongdo.nit.data.Session
import space.bongdo.nit.data.SettingsStore
import space.bongdo.nit.data.StreamEvent
import space.bongdo.nit.data.StreamReducer
import space.bongdo.nit.data.Tokens
import space.bongdo.nit.data.UnauthorizedException
import space.bongdo.nit.data.UserFile
import space.bongdo.nit.data.errorOf
import space.bongdo.nit.data.mergeRunning
import space.bongdo.nit.data.retryDelay
import space.bongdo.nit.data.retryPlan
import space.bongdo.nit.data.running
import space.bongdo.nit.NotificationHelper
import space.bongdo.nit.PreviewActivity
import space.bongdo.nit.data.parseServer
import space.bongdo.nit.ui.PreviewKind
import space.bongdo.nit.ui.fileKind
import space.bongdo.nit.ui.mimeFor
import space.bongdo.nit.ui.resolvePath
import java.io.File

enum class Phase { Loading, Login, Ready }

enum class StreamStatus { Connecting, Online, Offline }

data class UiState(
    val phase: Phase = Phase.Loading,
    val settings: AppSettings = AppSettings(),
    val sessions: List<Session> = emptyList(),
    val selected: Session? = null,
    val messages: List<Message> = emptyList(),
    val previous: String? = null,
    val loadingOlder: Boolean = false,
    val running: Boolean = false,
    val runningIds: Set<String> = emptySet(),
    val streamStatus: StreamStatus = StreamStatus.Connecting,
    val healthOk: Boolean = true,
    val busy: Boolean = false,
    val banner: String? = null,
    val loginError: String? = null,
    val permissions: List<PermissionRequest> = emptyList(),
    val forms: List<FormInfo> = emptyList(),
    val projects: List<Project> = emptyList(),
    val folders: FolderListing? = null,
    val models: List<Model> = emptyList(),
    val agents: List<Agent> = emptyList(),
    val search: String = "",
    val searching: Boolean = false,
    val hidden: Set<String> = emptySet(),
    val children: List<Session> = emptyList(),
    val waitingIds: Set<String> = emptySet(),
    val status: Map<String, String> = emptyMap(),
    val compacting: Boolean = false,
    val loadingMessages: Boolean = false,
    val draft: String = "",
    val draftKey: String = "",
    val drafts: Set<String> = emptySet(),
    val attachments: List<UserFile> = emptyList(),
    val preferredModel: ModelRef? = null,
    val preferredAgent: String? = null,
    val details: Map<String, Message> = emptyMap(),
    val loadingSteps: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val secureStore = SecureStore(application)
    private val draftStore = DraftStore(application)
    private val api = NitApi(secureStore.cookies)
    private val notifications = NotificationHelper(application)
    private val notifiedWaiting = mutableSetOf<String>()
    private val notifiedDone = mutableSetOf<String>()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var currentServer: ServerAddress? = null
    private var refreshJob: Job? = null
    private var draftJob: Job? = null
    private var healthJob: Job? = null
    private var watchJob: Job? = null
    private var pendingSession: String? = null
    private var firstStreamOpen = true
    private var firstPrompt: Pair<String, String>? = null
    private var messagesToken = 0
    private val runningEventsAt = mutableMapOf<String, Long>()
    private val retryAttempts = mutableMapOf<String, Int>()
    private val retryJobs = mutableMapOf<String, Job>()
    private val chatCache = ChatCache(application)
    private val transcripts = mutableMapOf<String, CachedChat>()
    private var cachedSessions = emptyList<Session>()
    private var cacheJob: Job? = null
    private var prefetchJob: Job? = null

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (_state.value.phase != Phase.Ready) return
            if (_state.value.healthOk) viewModelScope.launch { pollWaiting() }
            else retryConnection()
        }

        override fun onStop(owner: LifecycleOwner) {
            cacheJob?.cancel()
            viewModelScope.launch { writeCache() }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        viewModelScope.launch {
            runCatching {
                val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                getApplication<Application>().cacheDir.listFiles()
                    ?.filter { it.name.startsWith("preview-") && it.lastModified() < cutoff }
                    ?.forEach { it.delete() }
            }
        }
        viewModelScope.launch {
            draftStore.keys.collect { keys -> _state.update { it.copy(drafts = keys) } }
        }
        viewModelScope.launch {
            val settings = settingsStore.flow.first()
            _state.update { it.copy(settings = settings) }
            currentServer = parseServer(settings.server)
            if (currentServer == null) {
                _state.update { it.copy(phase = Phase.Login, loginError = "проверьте адрес сервера") }
                return@launch
            }
            api.useServer(currentServer!!)
            if (!api.hasSession()) {
                _state.update { it.copy(phase = Phase.Login) }
                return@launch
            }
            val cached = chatCache.read(currentServer!!.display)
            if (cached != null) {
                transcripts.putAll(cached.chats)
                cachedSessions = cached.sessions
                val id = cached.lastSession.ifBlank { settings.lastSession }
                val session = cached.sessions.firstOrNull { it.id == id }
                val chat = cached.chats[id]
                if (session != null) {
                    _state.update {
                        it.copy(
                            phase = Phase.Ready,
                            sessions = cached.sessions,
                            selected = session,
                            messages = chat?.messages.orEmpty(),
                            previous = chat?.previous,
                            loadingMessages = false,
                            settings = it.settings.copy(lastSession = id),
                        )
                    }
                }
            }
            val online = try {
                api.health()
                true
            } catch (error: UnauthorizedException) {
                _state.update { it.copy(phase = Phase.Login, loginError = "сессия истекла, войдите снова") }
                return@launch
            } catch (_: Exception) {
                false
            }
            enterApp(initialHealthOk = online)
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onCleared()
    }

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    fun note(message: String) = _state.update { it.copy(banner = message) }

    fun openHtml(source: String, title: String) {
        viewModelScope.launch {
            runCatching {
                val file = cacheFile(title, "html")
                file.writeText(source)
                launchPreview(title, PreviewKind.HTML, file, "text/html")
            }.onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    fun openArtifact(path: String, title: String) {
        val session = _state.value.selected?.id ?: return
        viewModelScope.launch {
            runCatching {
                val html = api.artifact(session, path)
                val file = cacheFile(title.ifBlank { path }, "html")
                file.writeText(html)
                launchPreview(title.ifBlank { path }, PreviewKind.HTML, file, "text/html")
            }.onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    fun openFile(rawPath: String, title: String) {
        viewModelScope.launch {
            runCatching {
                val path = normalizePreviewPath(rawPath)
                val name = title.ifBlank { path.substringAfterLast('/') }
                val bytes = if (path.startsWith("/tmp/nit-gui-")) api.uploadedBytes(path) else api.viewBytes(path)
                val kind = fileKind(name, mimeFor(name))
                val file = cacheFile(name, "")
                file.writeBytes(bytes)
                launchPreview(name, kind, file, mimeFor(name))
            }.onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    private fun normalizePreviewPath(raw: String): String {
        val value = raw.trim()
        if (value.contains("/api/file?") || value.contains("/api/view?")) {
            return runCatching {
                val uri = java.net.URI(value)
                val query = uri.query ?: uri.rawQuery
                query?.split('&')
                    ?.firstOrNull { it.startsWith("path=") }
                    ?.substringAfter('=')
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            }.getOrNull() ?: value
        }
        if (value.startsWith("file://")) return java.net.URI(value).path
        if (value.startsWith("file:")) return value.removePrefix("file:")
        return resolvePath(value, _state.value.selected?.location?.directory)
    }

    private fun cacheFile(name: String, ext: String): File {
        val safe = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "preview" }
        val suffix = ext.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
        return File(getApplication<Application>().cacheDir, "preview-${System.nanoTime()}-$safe$suffix")
    }

    private fun launchPreview(title: String, kind: String, file: File, mime: String?) {
        PreviewActivity.open(getApplication(), title, kind, file, mime, _state.value.settings.theme)
    }

    fun login(user: String, password: String, code: String, server: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, loginError = null) }
            try {
                val parsed = parseServer(server) ?: throw ApiException("проверьте адрес сервера")
                if (parsed.display != currentServer?.display) {
                    transcripts.clear()
                    cachedSessions = emptyList()
                }
                currentServer = parsed
                api.useServer(parsed)
                settingsStore.update { it.copy(server = parsed.display) }
                api.login(user.trim(), password, code.trim())
                _state.update { it.copy(settings = it.settings.copy(server = parsed.display)) }
                enterApp()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        loginError = error.message ?: "не удалось войти",
                    )
                }
            }
        }
    }

    fun logout() {
        stopLoops()
        transcripts.clear()
        cachedSessions = emptyList()
        viewModelScope.launch {
            chatCache.clear()
            settingsStore.update { it.copy(pendingCreate = "") }
            runCatching { api.logout() }
            secureStore.cookies.clear()
            draftJob?.cancel()
            _state.value = UiState(phase = Phase.Login, settings = _state.value.settings, drafts = _state.value.drafts)
        }
    }

    private fun stopLoops() {
        streamJob?.cancel()
        streamJob = null
        healthJob?.cancel()
        healthJob = null
        watchJob?.cancel()
        watchJob = null
        cacheJob?.cancel()
        cacheJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        retryJobs.values.forEach { it.cancel() }
        retryJobs.clear()
        retryAttempts.clear()
        firstStreamOpen = true
        firstPrompt = null
    }

    private suspend fun enterApp(initialHealthOk: Boolean = true) {
        _state.update {
            it.copy(
                phase = Phase.Ready,
                busy = false,
                loginError = null,
                healthOk = initialHealthOk,
                streamStatus = StreamStatus.Connecting,
            )
        }
        firstStreamOpen = true
        startStream()
        startHealthMonitor()
        startWatch()
        viewModelScope.launch { loadUiState() }
        refreshSessions()
        if (initialHealthOk) viewModelScope.launch { cleanupPendingCreate() }
        val pending = pendingSession
        if (pending != null) {
            pendingSession = null
            runCatching { openSession(pending) }
            return
        }
        val settings = _state.value.settings
        if (settings.lastSession.isNotBlank()) {
            runCatching { openSession(settings.lastSession) }
        } else {
            switchDraft("new:${settings.lastDirectory}")
        }
    }

    private suspend fun pollWaiting() {
        val sessions = _state.value.sessions
        if (sessions.isEmpty()) return
        val candidates = sessions.filter { session ->
            session.id != _state.value.selected?.id &&
                ((session.time.busy ?: 0) > (session.time.idle ?: 0) ||
                    session.id in _state.value.runningIds)
        }.take(20)
        for (session in candidates) {
            val waiting = runCatching {
                api.permissions(session.id).isNotEmpty() || api.forms(session.id).isNotEmpty()
            }.getOrDefault(false)
            _state.update {
                it.copy(waitingIds = if (waiting) it.waitingIds + session.id else it.waitingIds - session.id)
            }
            if (waiting) notifyWaiting(session.id)
        }
    }

    private suspend fun writeCache() {
        val server = currentServer?.display ?: return
        val sessions = cachedSessions.ifEmpty { _state.value.sessions }
        if (sessions.isEmpty()) return
        chatCache.write(
            CachedBoot(
                server = server,
                sessions = sessions,
                chats = transcripts.toMap(),
                lastSession = _state.value.selected?.id ?: _state.value.settings.lastSession,
                savedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun sessionUpdatedAt(id: String?): Long =
        _state.value.sessions.firstOrNull { it.id == id }?.time?.updated
            ?: _state.value.selected?.takeIf { it.id == id }?.time?.updated
            ?: 0

    private fun rememberTranscript(
        id: String?,
        messages: List<Message>,
        previous: String?,
        updatedAt: Long = sessionUpdatedAt(id),
    ) {
        if (id.isNullOrBlank() || messages.isEmpty()) return
        transcripts[id] = CachedChat(ChatCache.lighten(messages), previous, updatedAt)
        scheduleCacheWrite()
    }

    private fun scheduleCacheWrite() {
        if (cacheJob?.isActive == true) return
        cacheJob = viewModelScope.launch {
            delay(1_200)
            writeCache()
        }
    }

    private fun prefetchTranscripts(sessions: List<Session>) {
        if (prefetchJob?.isActive == true || !_state.value.healthOk) return
        prefetchJob = viewModelScope.launch {
            delay(800)
            val selected = _state.value.selected?.id
            val queue = sessions
                .asSequence()
                .filter { it.id != selected }
                .filter { !it.running() }
                .sortedByDescending { it.time.updated }
                .filter { transcripts[it.id]?.updatedAt != it.time.updated }
                .take(PREFETCH_LIMIT)
                .toList()
            for (session in queue) {
                if (!_state.value.healthOk || _state.value.selected?.id == session.id) continue
                runCatching { api.messages(session.id, limit = TAIL_LIMIT) }
                    .onSuccess { page ->
                        if (page.data.isEmpty()) return@onSuccess
                        rememberTranscript(session.id, page.data, page.previous, session.time.updated)
                    }
            }
        }
    }

    private suspend fun cleanupPendingCreate() {
        val id = settingsStore.flow.first().pendingCreate
        if (id.isBlank()) return
        var deleted = false
        val outcome = runCatching {
            val session = api.session(id)
            if (!session.running() && api.messages(id, limit = 1).data.isEmpty()) {
                api.deleteSession(id)
                deleted = true
            }
        }
        val status = (outcome.exceptionOrNull() as? ApiException)?.status ?: 0
        if (outcome.isSuccess || status == 404) {
            settingsStore.update { settings ->
                settings.copy(
                    pendingCreate = "",
                    lastSession = if (deleted && settings.lastSession == id) "" else settings.lastSession,
                )
            }
            if (deleted) {
                transcripts.remove(id)
                if (firstPrompt?.first == id) firstPrompt = null
                _state.update { state ->
                    if (state.selected?.id != id) return@update state
                    state.copy(
                        selected = null,
                        messages = emptyList(),
                        previous = null,
                        permissions = emptyList(),
                        forms = emptyList(),
                        children = emptyList(),
                        running = false,
                        runningIds = state.runningIds - id,
                        waitingIds = state.waitingIds - id,
                        status = state.status - id,
                        loadingMessages = false,
                        details = emptyMap(),
                        loadingSteps = false,
                    )
                }
            }
            refreshSessions()
        }
    }

    fun loadSteps(ids: List<String>) {
        val session = _state.value.selected?.id ?: return
        if (_state.value.loadingSteps) return
        val known = _state.value.messages
        val needed = ids.distinct().filter { id ->
            id !in _state.value.details &&
                !id.startsWith("local-") &&
                (known.firstOrNull { it.id == id }?.steps ?: 0) > 0
        }
        if (needed.isEmpty()) return
        _state.update { it.copy(loadingSteps = true) }
        viewModelScope.launch {
            val loaded = coroutineScope {
                needed.map { id ->
                    async { id to runCatching { api.message(session, id) }.getOrNull() }
                }.awaitAll()
            }
            if (_state.value.selected?.id != session) return@launch
            val found = loaded.mapNotNull { (id, message) -> message?.let { id to it } }.toMap()
            _state.update {
                it.copy(
                    details = it.details + found,
                    loadingSteps = false,
                    banner = if (found.size < needed.size && it.healthOk) "не все шаги загрузились" else it.banner,
                )
            }
        }
    }

    private suspend fun loadUiState() {
        val ui = runCatching { api.uiState() }.getOrNull() ?: return
        _state.update {
            it.copy(
                hidden = ui.hidden.toSet(),
                status = (ui.status + it.status).filterKeys { id -> id !in it.runningIds },
                preferredModel = ui.model ?: it.preferredModel,
                preferredAgent = ui.agent ?: it.preferredAgent,
            )
        }
    }

    private fun startHealthMonitor() {
        healthJob?.cancel()
        healthJob = viewModelScope.launch {
            var failures = 0
            while (isActive && _state.value.phase == Phase.Ready) {
                val ok = try {
                    api.health()
                    true
                } catch (_: UnauthorizedException) {
                    sessionExpired()
                    return@launch
                } catch (_: Exception) {
                    false
                }
                failures = if (ok) 0 else failures + 1
                if (ok || failures >= 2) {
                    val was = _state.value.healthOk
                    _state.update { it.copy(healthOk = ok) }
                    if (ok && !was) catchUp()
                }
                delay(20_000)
            }
        }
    }

    private fun startWatch() {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            while (isActive && _state.value.phase == Phase.Ready) {
                delay(5_000)
                val current = _state.value
                val selected = current.selected?.id
                val ids = (current.runningIds + current.waitingIds).filter { it != selected }
                if (ids.isEmpty()) continue
                val waiting = coroutineScope {
                    ids.map { id ->
                        async {
                            id to runCatching {
                                api.permissions(id).isNotEmpty() || api.forms(id).isNotEmpty()
                            }.getOrNull()
                        }
                    }.awaitAll()
                }
                _state.update { state ->
                    var next = state.waitingIds
                    for ((id, value) in waiting) {
                        if (value == null) continue
                        next = if (value) next + id else next - id
                    }
                    if (next == state.waitingIds) state else state.copy(waitingIds = next)
                }
            }
        }
    }

    private fun catchUp() {
        _state.update { it.copy(banner = null) }
        refreshSessions()
        viewModelScope.launch { cleanupPendingCreate() }
        val id = _state.value.selected?.id ?: return
        viewModelScope.launch {
            reloadMessages(id)
            loadPermissionsAndForms(id)
        }
    }

    fun retryConnection() {
        _state.update { it.copy(healthOk = true) }
        viewModelScope.launch {
            val ok = runCatching { api.health() }.getOrElse { error ->
                if (error is UnauthorizedException) sessionExpired()
                false
            }
            _state.update { it.copy(healthOk = ok) }
            if (ok) catchUp()
        }
    }

    fun startStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            EventStream.open(api.streamClient(), api.eventsUrl()).collect { event ->
                onEvent(event)
            }
        }
    }

    private fun onEvent(event: StreamEvent) {
        when (event.type) {
            EventStream.CONNECTING -> {
                _state.update { it.copy(streamStatus = StreamStatus.Connecting) }
                return
            }
            EventStream.CLOSED -> {
                _state.update { it.copy(streamStatus = StreamStatus.Offline) }
                return
            }
            EventStream.OPEN -> {
                _state.update { it.copy(streamStatus = StreamStatus.Online) }
                if (firstStreamOpen) {
                    firstStreamOpen = false
                    return
                }
                catchUp()
                return
            }
            EventStream.UNAUTHORIZED -> {
                sessionExpired()
                return
            }
        }
        val sessionID = StreamReducer.sessionID(event)
        if (sessionID.isBlank()) {
            if (event.type == "server.connected") {
                _state.update { it.copy(streamStatus = StreamStatus.Online) }
            }
            return
        }
        if (event.type.startsWith("permission.") || event.type.startsWith("form.")) {
            _state.update { it.copy(waitingIds = it.waitingIds + sessionID) }
            notifyWaiting(sessionID)
            if (sessionID == _state.value.selected?.id) {
                viewModelScope.launch { loadPermissionsAndForms(sessionID) }
            } else {
                viewModelScope.launch {
                    val waiting = runCatching {
                        api.permissions(sessionID).isNotEmpty() || api.forms(sessionID).isNotEmpty()
                    }.getOrDefault(true)
                    if (!waiting) notifiedWaiting.remove(sessionID)
                    _state.update {
                        it.copy(waitingIds = if (waiting) it.waitingIds + sessionID else it.waitingIds - sessionID)
                    }
                }
            }
        }
        if (event.type == "session.execution.started") {
            runningEventsAt[sessionID] = System.currentTimeMillis()
            clearRetry(sessionID)
        }
        if (event.type == "session.execution.succeeded") clearRetry(sessionID)
        if (event.type == "session.execution.interrupted") clearRetry(sessionID)
        if (event.type in TERMINAL) runningEventsAt[sessionID] = System.currentTimeMillis()
        if (event.type == "session.execution.failed" && scheduleRetry(sessionID, event)) return
        when (event.type) {
            "session.execution.started" -> {
                notifiedWaiting.remove(sessionID)
                notifiedDone.remove(sessionID)
                notifications.cancelWaiting(sessionID)
                _state.update {
                    it.copy(
                        runningIds = it.runningIds + sessionID,
                        status = it.status - sessionID,
                        details = if (sessionID == it.selected?.id) emptyMap() else it.details,
                        loadingSteps = if (sessionID == it.selected?.id) false else it.loadingSteps,
                    )
                }
                viewModelScope.launch { runCatching { api.setSessionStatus(sessionID, "") } }
            }
            "session.compaction.started" -> {
                if (sessionID == _state.value.selected?.id) _state.update { it.copy(compacting = true) }
            }
            "session.compaction.ended", "session.compaction.failed" -> {
                if (sessionID == _state.value.selected?.id) {
                    _state.update { it.copy(compacting = false) }
                    viewModelScope.launch { reloadMessages(sessionID) }
                    if (event.type == "session.compaction.failed") {
                        _state.update { it.copy(banner = "не удалось сжать контекст") }
                    }
                }
                scheduleSessionsRefresh()
            }
            "session.usage.updated" -> {
                val usage = usageOf(event)
                _state.update { current ->
                    val session = current.selected
                    current.copy(
                        selected = if (session?.id == sessionID) {
                            session.copy(
                                cost = usage.first ?: session.cost,
                                tokens = usage.second ?: session.tokens,
                            )
                        } else {
                            session
                        },
                        sessions = current.sessions.map { item ->
                            if (item.id != sessionID) item
                            else item.copy(
                                cost = usage.first ?: item.cost,
                                tokens = usage.second ?: item.tokens,
                            )
                        },
                    )
                }
            }
            "session.idle",
            "session.execution.succeeded",
            "session.execution.failed",
            "session.execution.interrupted",
            -> {
                var posted: String? = null
                _state.update {
                    val status = when {
                        event.type == "session.execution.failed" -> "failed"
                        event.type == "session.execution.interrupted" -> null
                        it.status[sessionID] == "failed" -> "failed"
                        else -> "done"
                    }
                    posted = status
                    it.copy(
                        runningIds = it.runningIds - sessionID,
                        status = if (status == null) it.status else it.status + (sessionID to status),
                        compacting = it.compacting && sessionID != it.selected?.id,
                        details = if (sessionID == it.selected?.id) emptyMap() else it.details,
                        loadingSteps = if (sessionID == it.selected?.id) false else it.loadingSteps,
                    )
                }
                posted?.let { value -> viewModelScope.launch { runCatching { api.setSessionStatus(sessionID, value) } } }
                notifiedWaiting.remove(sessionID)
                notifyDone(sessionID)
                if (sessionID == _state.value.selected?.id) {
                    viewModelScope.launch {
                        reloadMessages(sessionID)
                        loadPermissionsAndForms(sessionID)
                        runCatching { api.children(sessionID) }.onSuccess { children ->
                            _state.update { it.copy(children = children) }
                        }
                    }
                }
                scheduleSessionsRefresh()
            }
            else -> {
                if (sessionID == _state.value.selected?.id && StreamReducer.isMessageEvent(event.type)) {
                    _state.update {
                        it.copy(
                            messages = StreamReducer.apply(it.messages, event),
                            running = true,
                        )
                    }
                }
            }
        }
        if (event.type == "session.deleted" && sessionID == _state.value.selected?.id) {
            _state.update {
                it.copy(
                    selected = null,
                    messages = emptyList(),
                    previous = null,
                    permissions = emptyList(),
                    forms = emptyList(),
                    running = false,
                    runningIds = it.runningIds - sessionID,
                    waitingIds = it.waitingIds - sessionID,
                    status = it.status - sessionID,
                    details = emptyMap(),
                    loadingSteps = false,
                )
            }
        }
        if (event.type in STRUCTURAL) scheduleSessionsRefresh()
    }

    private fun scheduleRetry(sessionID: String, event: StreamEvent): Boolean {
        val assistantID = (event.data as? JsonObject)
            ?.get("assistantMessageID")
            ?.let { (it as? JsonPrimitive)?.contentOrNull }
        val attempt = (retryAttempts[sessionID] ?: 0) + 1
        val known = if (sessionID == _state.value.selected?.id) _state.value.messages else emptyList()
        val plan = retryPlan(known, errorOf(event.data), attempt, assistantID) ?: run {
            clearRetry(sessionID)
            return false
        }
        _state.update {
            it.copy(
                runningIds = it.runningIds - sessionID,
                status = it.status - sessionID,
                compacting = it.compacting && sessionID != it.selected?.id,
            )
        }
        runRetry(sessionID, plan, attempt)
        scheduleSessionsRefresh()
        return true
    }

    private fun runRetry(sessionID: String, plan: RetryPlan, attempt: Int) {
        if (attempt > MAX_RETRY_ATTEMPTS) {
            clearRetry(sessionID)
            _state.update {
                it.copy(
                    runningIds = it.runningIds - sessionID,
                    status = it.status + (sessionID to "failed"),
                    banner = "Сервер всё ещё перегружен после $MAX_RETRY_ATTEMPTS попыток",
                )
            }
            scheduleSessionsRefresh()
            return
        }
        val wait = retryDelay(attempt, plan.retryAfterMs)
        retryAttempts[sessionID] = attempt
        _state.update {
            it.copy(banner = "Сервер перегружен — повтор через ${(wait + 999) / 1000} с…")
        }
        retryJobs[sessionID]?.cancel()
        retryJobs[sessionID] = viewModelScope.launch {
            delay(wait)
            retryJobs.remove(sessionID)
            try {
                api.prompt(sessionID, plan.text, plan.files, null, plan.messageID)
                retryAttempts.remove(sessionID)
                _state.update { it.copy(banner = null) }
            } catch (error: Exception) {
                if (error is UnauthorizedException) {
                    sessionExpired()
                    return@launch
                }
                runRetry(sessionID, plan, attempt + 1)
            }
        }
    }

    private fun clearRetry(sessionID: String) {
        retryJobs.remove(sessionID)?.cancel()
        retryAttempts.remove(sessionID)
    }

    private fun usageOf(event: StreamEvent): Pair<Double?, Tokens?> {
        val data = event.data as? JsonObject ?: return null to null
        val cost = (data["cost"] as? JsonPrimitive)?.doubleOrNull
        val tokens = data["tokens"]?.let {
            runCatching { json.decodeFromJsonElement(Tokens.serializer(), it) }.getOrNull()
        }
        return cost to tokens
    }

    private fun scheduleSessionsRefresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            delay(500)
            refreshSessions()
        }
    }

    fun refreshSessions(search: String = _state.value.search) {
        viewModelScope.launch {
            val requestedAt = System.currentTimeMillis()
            _state.update { it.copy(search = search, searching = search.isNotBlank()) }
            runCatching { api.sessions(search.ifBlank { null }) }
                .onSuccess { sessions ->
                    _state.update { current ->
                        val selected = current.selected?.let { item ->
                            sessions.firstOrNull { it.id == item.id } ?: item
                        }
                        if (search.isBlank()) cachedSessions = sessions
                        current.copy(
                            sessions = sessions,
                            selected = selected,
                            searching = false,
                            runningIds = mergeRunning(
                                current.runningIds,
                                sessions,
                                runningEventsAt,
                                requestedAt,
                            ),
                        )
                    }
                    scheduleCacheWrite()
                    prefetchTranscripts(sessions)
                }
                .onFailure { error ->
                    if (error is UnauthorizedException) {
                        sessionExpired()
                    } else {
                        _state.update {
                            it.copy(
                                searching = false,
                                banner = if (it.healthOk) error.message else it.banner,
                            )
                        }
                    }
                }
            loadUiState()
        }
    }

    fun openSessionFromNotification(id: String) {
        if (_state.value.phase == Phase.Ready) openSession(id) else pendingSession = id
    }

    fun openSession(id: String) {
        val current = _state.value
        rememberTranscript(current.selected?.id, current.messages, current.previous)
        val cached = transcripts[id]
        val known = current.sessions.firstOrNull { it.id == id }
            ?: cachedSessions.firstOrNull { it.id == id }

        suspend fun select(session: Session) {
            _state.update { state ->
                val same = state.selected?.id == id
                state.copy(
                    selected = session,
                    messages = cached?.messages ?: if (same) state.messages else emptyList(),
                    previous = if (cached != null) cached.previous else if (same) state.previous else null,
                    children = emptyList(),
                    compacting = false,
                    loadingMessages = cached == null && !same,
                    details = emptyMap(),
                    loadingSteps = false,
                )
            }
            val directory = session.location.directory.ifBlank { _state.value.settings.lastDirectory }
            _state.update { it.copy(settings = it.settings.copy(lastSession = id, lastDirectory = directory)) }
            switchDraft(id)
        }

        viewModelScope.launch {
            if (known != null) select(known)
            settingsStore.update { it.copy(lastSession = id) }
            val session = runCatching { api.session(id) }.getOrElse { error ->
                if (error !is UnauthorizedException && _state.value.healthOk) {
                    _state.update { it.copy(banner = error.message) }
                }
                _state.value.sessions.firstOrNull { it.id == id }
                    ?: cachedSessions.firstOrNull { it.id == id }
            } ?: return@launch
            select(session)
            settingsStore.update {
                it.copy(
                    lastSession = id,
                    lastDirectory = session.location.directory.ifBlank { it.lastDirectory },
                )
            }
            val fresh = cached == null || cached.updatedAt != session.time.updated || session.running()
            if (fresh) reloadMessages(id) else _state.update { it.copy(loadingMessages = false) }
            loadPermissionsAndForms(id)
            launch { runCatching { api.children(id) }.onSuccess { children -> _state.update { it.copy(children = children) } } }
            launch { runCatching { api.models(session.location.directory) }.onSuccess { models -> _state.update { it.copy(models = models) } } }
            launch { runCatching { api.agents() }.onSuccess { agents -> _state.update { it.copy(agents = agents) } } }
        }
    }

    private suspend fun reloadMessages(id: String) {
        val token = ++messagesToken
        val before = _state.value.messages.associateBy { it.id }
        runCatching { api.messages(id, limit = TAIL_LIMIT) }
            .onSuccess { page ->
                if (token != messagesToken || _state.value.selected?.id != id) return@onSuccess
                _state.update {
                    it.copy(
                        messages = MessageMerge.snapshot(page.data, it.messages, before),
                        previous = page.previous,
                        running = it.runningIds.contains(id),
                        loadingMessages = false,
                    )
                }
                rememberTranscript(id, _state.value.messages, page.previous)
            }
            .onFailure { error ->
                if (token != messagesToken) return@onFailure
                _state.update { it.copy(loadingMessages = false) }
                if (error is UnauthorizedException) sessionExpired()
                else if (_state.value.healthOk) _state.update { it.copy(banner = error.message) }
            }
    }

    fun loadOlder() {
        val selected = _state.value.selected ?: return
        val before = _state.value.previous ?: return
        if (_state.value.loadingOlder) return
        viewModelScope.launch {
            _state.update { it.copy(loadingOlder = true) }
            runCatching { api.messages(selected.id, before = before, limit = TAIL_LIMIT) }
                .onSuccess { page ->
                    if (_state.value.selected?.id != selected.id) {
                        _state.update { it.copy(loadingOlder = false) }
                        return@onSuccess
                    }
                    _state.update { current ->
                        val next = MessageMerge.prepend(current.messages, page.data)
                        val cursor = if (
                            page.data.isEmpty() ||
                            next.size == current.messages.size ||
                            page.previous == before
                        ) {
                            null
                        } else {
                            page.previous
                        }
                        current.copy(messages = next, previous = cursor, loadingOlder = false)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loadingOlder = false) }
                    if (error !is UnauthorizedException) _state.update { it.copy(banner = error.message) }
                    else sessionExpired()
                }
        }
    }

    fun send(
        text: String,
        attachments: List<UserFile>,
        delivery: String? = null,
        onResult: (Boolean) -> Unit = {},
    ) {
        val body = text.trim()
        if (body.isEmpty() && attachments.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val sentKey = _state.value.draftKey
            _state.update { it.copy(busy = true, banner = null) }
            var localID: String? = null
            var created = false
            var activeSession: String? = null
            try {
                var session = _state.value.selected
                if (session == null) {
                    val directory = _state.value.settings.lastDirectory
                    if (directory.isBlank()) {
                        _state.update { it.copy(busy = false, banner = "сначала выберите папку") }
                        onResult(false)
                        return@launch
                    }
                    session = api.createSession(
                        directory,
                        agent = _state.value.preferredAgent,
                        model = _state.value.preferredModel,
                    )
                    created = true
                    settingsStore.update { it.copy(pendingCreate = session.id) }
                    _state.update {
                        it.copy(
                            selected = session,
                            sessions = listOf(session) + it.sessions.filter { existing -> existing.id != session.id },
                        )
                    }
                    settingsStore.update { it.copy(lastSession = session.id) }
                }
                activeSession = session.id
                localID = "local-${System.currentTimeMillis()}"
                val optimistic = Message(
                    id = localID!!,
                    type = "user",
                    time = MessageTime(created = System.currentTimeMillis()),
                    text = body,
                    files = attachments.ifEmpty { null },
                    delivery = delivery,
                )
                _state.update {
                    it.copy(
                        messages = it.messages + optimistic,
                        running = true,
                        runningIds = it.runningIds + session.id,
                        details = emptyMap(),
                        loadingSteps = false,
                    )
                }
                runningEventsAt[session.id] = System.currentTimeMillis()
                val admitted = api.prompt(session.id, body, attachments, delivery)
                if (created) {
                    firstPrompt = session.id to body
                    settingsStore.update { it.copy(pendingCreate = "") }
                }
                if (!admitted.isNullOrBlank()) {
                    _state.update { current ->
                        current.copy(messages = current.messages.map { if (it.id == localID) it.copy(id = admitted) else it })
                    }
                }
                _state.update { it.copy(busy = false) }
                draftJob?.cancel()
                draftStore.set(sentKey, "")
                draftStore.setFiles(sentKey, emptyList())
                runCatching { api.setDraft(sentKey, "") }
                if (_state.value.draftKey == sentKey && _state.value.attachments == attachments) {
                    _state.update { it.copy(attachments = emptyList()) }
                }
                if (_state.value.draftKey == sentKey) {
                    if (_state.value.draft.trim() == body) {
                        _state.update { it.copy(draft = "") }
                    } else {
                        draftStore.set(sentKey, _state.value.draft)
                    }
                }
                if (_state.value.draftKey != session.id && _state.value.selected?.id == session.id) {
                    switchDraft(session.id)
                }
                onResult(true)
            } catch (error: Exception) {
                val failed = localID
                if (error is UnauthorizedException) {
                    sessionExpired()
                } else {
                    val sid = activeSession
                    _state.update { current ->
                        current.copy(
                            busy = false,
                            running = false,
                            runningIds = if (sid == null) current.runningIds else current.runningIds - sid,
                            messages = if (failed == null) current.messages else current.messages.filterNot { it.id == failed },
                            banner = error.message,
                        )
                    }
                    if (created && sid != null) cleanupPendingCreate()
                }
                onResult(false)
            }
        }
    }

    fun stop() {
        val id = _state.value.selected?.id ?: return
        viewModelScope.launch {
            var interrupted = false
            runCatching { api.interrupt(id) }
                .onSuccess { interrupted = true }
                .onFailure { error -> _state.update { current -> current.copy(banner = error.message) } }
            clearRetry(id)
            _state.update { it.copy(running = false, runningIds = it.runningIds - id) }
            val first = firstPrompt
            if (!interrupted || first == null || first.first != id) return@launch
            firstPrompt = null
            val answered = _state.value.messages.any { it.type == "assistant" }
            val mine = _state.value.messages.count { it.type == "user" }
            if (answered || mine > 1) return@launch
            runCatching { api.deleteSession(id) }
            transcripts.remove(id)
            settingsStore.update { it.copy(pendingCreate = "") }
            val directory = _state.value.settings.lastDirectory
            val key = "new:$directory"
            draftJob?.cancel()
            draftStore.set(key, first.second)
            draftStore.setFiles(key, emptyList())
            runCatching { api.setDraft(key, first.second) }
            settingsStore.update { it.copy(lastSession = "") }
            _state.update {
            it.copy(
                selected = null,
                messages = emptyList(),
                previous = null,
                permissions = emptyList(),
                forms = emptyList(),
                children = emptyList(),
                compacting = false,
                loadingMessages = false,
                details = emptyMap(),
                loadingSteps = false,
                runningIds = it.runningIds - id,
                    waitingIds = it.waitingIds - id,
                    status = it.status - id,
                    sessions = it.sessions.filterNot { session -> session.id == id },
                )
            }
            switchDraft(key)
        }
    }

    fun startNewSession(directory: String) {
        viewModelScope.launch {
            settingsStore.update { it.copy(lastDirectory = directory, lastSession = "") }
            _state.update {
                it.copy(
                    settings = it.settings.copy(lastDirectory = directory),
                    selected = null,
                    messages = emptyList(),
                    previous = null,
                    running = false,
                    children = emptyList(),
                    permissions = emptyList(),
                    forms = emptyList(),
                    compacting = false,
                    loadingMessages = false,
                    busy = false,
                    details = emptyMap(),
                    loadingSteps = false,
                )
            }
            switchDraft("new:$directory")
        }
    }

    fun setDirectory(directory: String) {
        viewModelScope.launch {
            settingsStore.update { it.copy(lastDirectory = directory, lastSession = "") }
            _state.update {
                it.copy(
                    settings = it.settings.copy(lastDirectory = directory),
                    selected = null,
                    messages = emptyList(),
                    previous = null,
                    running = false,
                    children = emptyList(),
                    permissions = emptyList(),
                    forms = emptyList(),
                    compacting = false,
                    loadingMessages = false,
                    details = emptyMap(),
                    loadingSteps = false,
                )
            }
            switchDraft("new:$directory")
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingsStore.update { it.copy(theme = theme) }
            _state.update { it.copy(settings = it.settings.copy(theme = theme)) }
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            settingsStore.update { it.copy(fontScale = scale) }
            _state.update { it.copy(settings = it.settings.copy(fontScale = scale)) }
        }
    }

    fun compactSession() {
        val id = _state.value.selected?.id ?: return
        _state.update { it.copy(compacting = true) }
        viewModelScope.launch {
            runCatching { api.compact(id) }
                .onSuccess { refreshSessions() }
                .onFailure { error ->
                    _state.update { it.copy(compacting = false, banner = error.message) }
                }
        }
    }

    fun revertLast() {
        val lastUser = _state.value.messages.lastOrNull { it.type == "user" } ?: return
        if (_state.value.running) {
            _state.update { it.copy(banner = "Сначала остановите ход") }
            return
        }
        val id = _state.value.selected?.id ?: return
        viewModelScope.launch {
            runCatching { api.revert(id, lastUser.id) }
                .onSuccess {
                    _state.update { current ->
                        val index = current.messages.indexOfFirst { it.id == lastUser.id }
                        current.copy(messages = if (index >= 0) current.messages.take(index) else current.messages)
                    }
                    setDraft(lastUser.text.orEmpty())
                    reloadMessages(id)
                }
                .onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    fun forkAt(before: String? = null) {
        val id = _state.value.selected?.id ?: return
        viewModelScope.launch {
            runCatching { api.fork(id, before) }
                .onSuccess { forked ->
                    _state.update {
                        it.copy(
                            selected = forked,
                            messages = emptyList(),
                            previous = null,
                            children = emptyList(),
                            sessions = listOf(forked) + it.sessions.filter { existing -> existing.id != forked.id },
                        )
                    }
                    switchDraft(forked.id)
                    reloadMessages(forked.id)
                    loadPermissionsAndForms(forked.id)
                }
                .onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    fun forkLastUser() {
        val lastUser = _state.value.messages.lastOrNull { it.type == "user" } ?: return
        forkAt(lastUser.id)
    }

    fun retryLast() {
        val lastUser = _state.value.messages.lastOrNull { it.type == "user" } ?: return
        if (_state.value.running) {
            _state.update { it.copy(banner = "Сначала остановите ход") }
            return
        }
        send(lastUser.text.orEmpty(), emptyList())
    }

    fun openSubagent(session: Session) {
        openSession(session.id)
    }

    fun backToParent() {
        val parent = _state.value.selected?.parentID ?: return
        openSession(parent)
    }

    fun addAttachment(file: UserFile) {
        val key = _state.value.draftKey
        val next = _state.value.attachments + file
        _state.update { it.copy(attachments = next) }
        if (key.isNotBlank()) viewModelScope.launch { draftStore.setFiles(key, next) }
    }

    fun removeAttachment(file: UserFile) {
        val key = _state.value.draftKey
        val next = _state.value.attachments.filterNot { it == file }
        _state.update { it.copy(attachments = next) }
        if (key.isNotBlank()) viewModelScope.launch { draftStore.setFiles(key, next) }
    }

    fun setDraft(text: String) {
        val key = _state.value.draftKey
        _state.update { it.copy(draft = text) }
        if (key.isBlank()) return
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(350)
            draftStore.set(key, text)
            runCatching { api.setDraft(key, text) }
        }
    }

    private fun switchDraft(key: String) {
        val oldKey = _state.value.draftKey
        val oldText = _state.value.draft
        val oldFiles = _state.value.attachments
        if (oldKey == key) return
        draftJob?.cancel()
        _state.update { it.copy(draftKey = key, draft = "", attachments = emptyList()) }
        viewModelScope.launch {
            if (oldKey.isNotBlank()) {
                draftStore.set(oldKey, oldText)
                draftStore.setFiles(oldKey, oldFiles)
            }
            val files = draftStore.getFiles(key)
            if (_state.value.draftKey == key) _state.update { it.copy(attachments = files) }
            val local = draftStore.get(key)
            var text = local.text
            val remote = runCatching { api.draft(key) }.getOrNull()
            if (remote != null && remote.updatedAt >= local.updatedAt) {
                text = if (remote.cleared) "" else remote.text
                draftStore.set(key, text, remote.updatedAt)
            } else if (local.text.isNotEmpty() && local.updatedAt > (remote?.updatedAt ?: 0)) {
                runCatching { api.setDraft(key, local.text) }
            }
            if (_state.value.draftKey == key && _state.value.draft.isEmpty()) {
                _state.update { it.copy(draft = text) }
            }
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            runCatching { api.projects() }
                .onSuccess { projects -> _state.update { it.copy(projects = projects.sortedBy { project -> project.canonical }) } }
                .onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    fun loadFolders(path: String?) {
        viewModelScope.launch {
            runCatching { api.folders(path) }
                .onSuccess { folders -> _state.update { it.copy(folders = folders) } }
                .onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    private suspend fun loadPermissionsAndForms(id: String) {
        var waiting = false
        runCatching { api.permissions(id) }.onSuccess { permissions ->
            waiting = waiting || permissions.isNotEmpty()
            _state.update { it.copy(permissions = permissions) }
        }
        runCatching { api.forms(id) }.onSuccess { forms ->
            waiting = waiting || forms.isNotEmpty()
            _state.update { it.copy(forms = forms) }
        }
        if (!waiting) {
            notifiedWaiting.remove(id)
            notifications.cancelWaiting(id)
        }
        _state.update {
            it.copy(waitingIds = if (waiting) it.waitingIds + id else it.waitingIds - id)
        }
    }

    fun replyPermission(request: PermissionRequest, decision: String) {
        viewModelScope.launch {
            runCatching { api.replyPermission(request.sessionID.ifBlank { _state.value.selected?.id.orEmpty() }, request.id, decision) }
                .onSuccess {
                    notifiedWaiting.remove(request.sessionID)
                    notifications.cancelWaiting(request.sessionID)
                    _state.update {
                        val rest = it.permissions.filterNot { item -> item.id == request.id }
                        val sid = request.sessionID.ifBlank { it.selected?.id.orEmpty() }
                        it.copy(
                            permissions = rest,
                            waitingIds = if (rest.isEmpty() && it.forms.isEmpty()) it.waitingIds - sid else it.waitingIds,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    fun replyForm(form: FormInfo, answer: JsonObject) {
        val id = _state.value.selected?.id ?: return
        viewModelScope.launch {
            runCatching { api.replyForm(id, form.id, answer) }
                .onSuccess {
                    notifiedWaiting.remove(id)
                    notifications.cancelWaiting(id)
                    _state.update {
                        val rest = it.forms.filterNot { item -> item.id == form.id }
                        it.copy(
                            forms = rest,
                            waitingIds = if (rest.isEmpty() && it.permissions.isEmpty()) it.waitingIds - id else it.waitingIds,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    fun cancelForm(form: FormInfo) {
        val id = _state.value.selected?.id ?: return
        viewModelScope.launch {
            runCatching { api.cancelForm(id, form.id) }
                .onSuccess {
                    notifiedWaiting.remove(id)
                    notifications.cancelWaiting(id)
                    _state.update {
                        val rest = it.forms.filterNot { item -> item.id == form.id }
                        it.copy(
                            forms = rest,
                            waitingIds = if (rest.isEmpty() && it.permissions.isEmpty()) it.waitingIds - id else it.waitingIds,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    fun switchModel(model: ModelRef) {
        _state.update { it.copy(preferredModel = model) }
        viewModelScope.launch {
            runCatching { api.setPreferredModel(model) }
            val session = _state.value.selected ?: return@launch
            runCatching { api.switchModel(session.id, model) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            selected = current.selected?.copy(model = model),
                            sessions = current.sessions.map { if (it.id == session.id) it.copy(model = model) else it },
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    fun switchAgent(agent: String) {
        _state.update { it.copy(preferredAgent = agent) }
        viewModelScope.launch {
            runCatching { api.setPreferredAgent(agent) }
            val session = _state.value.selected ?: return@launch
            runCatching { api.switchAgent(session.id, agent) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            selected = current.selected?.copy(agent = agent),
                            sessions = current.sessions.map { if (it.id == session.id) it.copy(agent = agent) else it },
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    fun upload(uri: Uri, onDone: (UserFile) -> Unit) {
        val selected = _state.value.selected
        val directory = selected?.location?.directory?.ifBlank { null }
            ?: _state.value.settings.lastDirectory.ifBlank { null }
        if (directory == null) {
            _state.update { it.copy(banner = "сначала выберите папку") }
            return
        }
        viewModelScope.launch {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val meta = fileMeta(resolver, uri)
                val temp = File.createTempFile("nit-upload-", ".bin", getApplication<Application>().cacheDir)
                try {
                    resolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw ApiException("не удалось прочитать файл")
                    api.upload(temp, meta.first, directory, meta.second) to meta.second
                } finally {
                    temp.delete()
                }
            }
                .onSuccess { (uploaded, mime) ->
                    onDone(
                        UserFile(
                            uri = "file://${uploaded.path}",
                            name = uploaded.name,
                            mime = mime,
                            bytes = uploaded.size,
                        ),
                    )
                }
                .onFailure { error -> _state.update { it.copy(banner = error.message) } }
        }
    }

    private fun fileMeta(resolver: ContentResolver, uri: Uri): Pair<String, String> {
        var name = "file"
        var mime = resolver.getType(uri) ?: "application/octet-stream"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        }
        if (mime.isBlank()) mime = "application/octet-stream"
        return name to mime
    }

    fun imageModel(file: UserFile): Any? {
        val name = file.name.orEmpty()
        val image = file.mime?.startsWith("image/") == true ||
            name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
        if (!image) return null
        val raw = file.uri ?: return null
        val url = api.mediaUrl(normalizePreviewPath(raw))
        val model = ImageRequest.Builder(getApplication()).data(url).crossfade(true)
        val cookie = api.cookieHeader(url)
        if (cookie.isNotBlank()) model.addHeader("Cookie", cookie)
        return model.build()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private companion object {
        const val TAIL_LIMIT = 50
        const val PREFETCH_LIMIT = 12
        val TERMINAL = setOf(
            "session.idle",
            "session.execution.succeeded",
            "session.execution.failed",
            "session.execution.interrupted",
        )
        val STRUCTURAL = setOf(
            "session.created",
            "session.renamed",
            "session.deleted",
            "session.archived",
            "session.unarchived",
            "session.moved",
            "session.forked",
            "session.metadata.updated",
            "session.model.selected",
            "session.agent.selected",
        )
    }

    private fun sessionTitle(id: String): String =
        (_state.value.sessions.firstOrNull { it.id == id } ?: _state.value.children.firstOrNull { it.id == id })
            ?.title?.takeIf { it.isNotBlank() } ?: "Чат"

    private fun notifyWaiting(id: String) {
        if (notifications.isForeground()) return
        if (!notifiedWaiting.add(id)) return
        notifications.waiting(sessionTitle(id), "Агент ждёт ответа", id)
    }

    private fun notifyDone(id: String) {
        if (notifications.isForeground()) return
        if (!notifiedDone.add(id)) return
        notifications.done(sessionTitle(id), "Ход завершён", id)
    }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "svg")

    private fun sessionExpired() {
        stopLoops()
        _state.update {
            it.copy(
                phase = Phase.Login,
                loginError = "сессия истекла, войдите снова",
                streamStatus = StreamStatus.Offline,
                healthOk = false,
            )
        }
    }
}
