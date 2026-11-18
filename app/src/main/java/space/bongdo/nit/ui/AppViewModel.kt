package space.bongdo.nit.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import coil.request.ImageRequest
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import space.bongdo.nit.data.Agent
import space.bongdo.nit.data.ApiException
import space.bongdo.nit.data.AppSettings
import space.bongdo.nit.data.DraftStore
import space.bongdo.nit.data.EventStream
import space.bongdo.nit.data.FolderListing
import space.bongdo.nit.data.FormInfo
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.MessageTime
import space.bongdo.nit.data.Model
import space.bongdo.nit.data.ModelRef
import space.bongdo.nit.data.NitApi
import space.bongdo.nit.data.PermissionRequest
import space.bongdo.nit.data.Project
import space.bongdo.nit.data.SecureStore
import space.bongdo.nit.data.ServerAddress
import space.bongdo.nit.data.Session
import space.bongdo.nit.data.SettingsStore
import space.bongdo.nit.data.StreamEvent
import space.bongdo.nit.data.StreamReducer
import space.bongdo.nit.data.UnauthorizedException
import space.bongdo.nit.data.UserFile
import space.bongdo.nit.NotificationHelper
import space.bongdo.nit.PreviewActivity
import space.bongdo.nit.data.parseServer
import space.bongdo.nit.ui.PreviewKind
import space.bongdo.nit.ui.fileKind
import space.bongdo.nit.ui.mimeFor
import space.bongdo.nit.ui.resolvePath
import java.io.File

enum class Phase { Loading, Login, Ready }

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
    val connected: Boolean = false,
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
    private var pendingSession: String? = null

    init {
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
            val ok = runCatching { api.health() }.getOrElse { false }
            if (!ok) {
                _state.update { it.copy(phase = Phase.Login, loginError = "сессия истекла, войдите снова") }
                return@launch
            }
            enterApp()
        }
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
        streamJob?.cancel()
        streamJob = null
        viewModelScope.launch {
            runCatching { api.logout() }
            secureStore.cookies.clear()
            draftJob?.cancel()
            _state.value = UiState(phase = Phase.Login, settings = _state.value.settings, drafts = _state.value.drafts)
        }
    }

    private suspend fun enterApp() {
        _state.update { it.copy(phase = Phase.Ready, busy = false, loginError = null) }
        startStream()
        viewModelScope.launch {
            runCatching { api.uiState() }.onSuccess { ui ->
                _state.update { it.copy(hidden = ui.hidden.toSet(), status = ui.status) }
            }
        }
        refreshSessions()
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

    fun startStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            EventStream.open(api.streamClient(), api.eventsUrl()).collect { event ->
                onEvent(event)
            }
        }
    }

    private fun onEvent(event: StreamEvent) {
        if (event.type == EventStream.CLOSED) {
            _state.update { it.copy(connected = false) }
            return
        }
        if (event.type == EventStream.OPEN) {
            _state.update { it.copy(connected = true) }
            val selected = _state.value.selected?.id
            if (selected != null) {
                viewModelScope.launch { reloadMessages(selected) }
            }
            return
        }
        if (event.type == EventStream.UNAUTHORIZED) {
            streamJob?.cancel()
            streamJob = null
            _state.update {
                it.copy(
                    phase = Phase.Login,
                    loginError = "сессия истекла, войдите снова",
                    connected = false,
                )
            }
            return
        }
        val sessionID = StreamReducer.sessionID(event)
        if (sessionID.isBlank()) {
            if (event.type == "server.connected") _state.update { it.copy(connected = true) }
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
        when (event.type) {
            "session.execution.started" -> {
                notifiedWaiting.remove(sessionID)
                notifiedDone.remove(sessionID)
                notifications.cancelWaiting(sessionID)
                _state.update {
                    it.copy(
                        runningIds = it.runningIds + sessionID,
                        status = it.status - sessionID,
                    )
                }
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
            "session.idle",
            "session.execution.succeeded",
            "session.execution.failed",
            "session.execution.interrupted",
            -> {
                _state.update {
                    val status = when {
                        event.type == "session.execution.failed" -> "failed"
                        event.type == "session.execution.interrupted" -> null
                        it.status[sessionID] == "failed" -> "failed"
                        else -> "done"
                    }
                    it.copy(
                        runningIds = it.runningIds - sessionID,
                        status = if (status == null) it.status else it.status + (sessionID to status),
                        compacting = it.compacting && sessionID != it.selected?.id,
                    )
                }
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
                )
            }
        }
        if (event.type == "session.created" || event.type == "session.deleted" ||
            event.type == "session.renamed" || event.type == "session.moved"
        ) {
            scheduleSessionsRefresh()
        }
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
            _state.update { it.copy(search = search, searching = search.isNotBlank()) }
            runCatching { api.sessions(search.ifBlank { null }) }
                .onSuccess { sessions ->
                    _state.update { current ->
                        val selected = current.selected?.let { item ->
                            sessions.firstOrNull { it.id == item.id } ?: item
                        }
                        current.copy(
                            sessions = sessions,
                            selected = selected,
                            searching = false,
                            runningIds = sessions
                                .filter { session ->
                                    val busy = session.time.busy
                                    val idle = session.time.idle
                                    busy != null && (idle == null || busy > idle)
                                }
                                .map { session -> session.id }
                                .toSet() + current.runningIds.filter { id -> sessions.none { it.id == id } },
                        )
                    }
                }
                .onFailure { error ->
                    if (error is UnauthorizedException) {
                        sessionExpired()
                    } else {
                        _state.update { it.copy(searching = false, banner = error.message) }
                    }
                }
            runCatching { api.uiState() }.onSuccess { ui ->
                _state.update { it.copy(hidden = ui.hidden.toSet(), status = ui.status) }
            }
        }
    }

    fun openSessionFromNotification(id: String) {
        if (_state.value.phase == Phase.Ready) openSession(id) else pendingSession = id
    }

    fun openSession(id: String) {
        viewModelScope.launch {
            val session = runCatching { api.session(id) }.getOrElse { error ->
                _state.update { it.copy(banner = error.message) }
                _state.value.sessions.firstOrNull { it.id == id }
            } ?: return@launch
            _state.update {
                it.copy(
                    selected = session,
                    messages = emptyList(),
                    previous = null,
                    children = emptyList(),
                    compacting = false,
                    loadingMessages = true,
                )
            }
            switchDraft(id)
            settingsStore.update { it.copy(lastSession = id) }
            reloadMessages(id)
            loadPermissionsAndForms(id)
            launch { runCatching { api.children(id) }.onSuccess { children -> _state.update { it.copy(children = children) } } }
            launch { runCatching { api.models(session.location.directory) }.onSuccess { models -> _state.update { it.copy(models = models) } } }
            launch { runCatching { api.agents() }.onSuccess { agents -> _state.update { it.copy(agents = agents) } } }
        }
    }

    private suspend fun reloadMessages(id: String) {
        runCatching { api.messages(id, limit = 80) }
            .onSuccess { page ->
                _state.update {
                    it.copy(
                        messages = page.data,
                        previous = page.previous,
                        running = it.runningIds.contains(id),
                        loadingMessages = false,
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(loadingMessages = false) }
                if (error is UnauthorizedException) sessionExpired()
                else _state.update { it.copy(banner = error.message) }
            }
    }

    fun loadOlder() {
        val selected = _state.value.selected ?: return
        val before = _state.value.previous ?: return
        if (_state.value.loadingOlder) return
        viewModelScope.launch {
            _state.update { it.copy(loadingOlder = true) }
            runCatching { api.messages(selected.id, before = before, limit = 80) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            messages = page.data + it.messages,
                            previous = page.previous,
                            loadingOlder = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loadingOlder = false, banner = error.message) }
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
            try {
                var session = _state.value.selected
                if (session == null) {
                    val directory = _state.value.settings.lastDirectory
                    if (directory.isBlank()) {
                        _state.update { it.copy(busy = false, banner = "сначала выберите папку") }
                        onResult(false)
                        return@launch
                    }
                    session = api.createSession(directory)
                    _state.update {
                        it.copy(
                            selected = session,
                            sessions = listOf(session) + it.sessions.filter { existing -> existing.id != session.id },
                        )
                    }
                    settingsStore.update { it.copy(lastSession = session.id) }
                }
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
                    )
                }
                val admitted = api.prompt(session.id, body, attachments, delivery)
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
                    _state.update { current ->
                        current.copy(
                            busy = false,
                            running = false,
                            messages = if (failed == null) current.messages else current.messages.filterNot { it.id == failed },
                            banner = error.message,
                        )
                    }
                }
                onResult(false)
            }
        }
    }

    fun stop() {
        val id = _state.value.selected?.id ?: return
        viewModelScope.launch {
            runCatching { api.interrupt(id) }
                .onFailure { error -> _state.update { current -> current.copy(banner = error.message) } }
            _state.update { it.copy(running = false, runningIds = it.runningIds - id) }
        }
    }

    fun startNewSession(directory: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { api.createSession(directory) }
                .onSuccess { session ->
                    val newKey = "new:$directory"
                    val stored = draftStore.get(newKey).text
                    val moved = if (_state.value.draftKey == newKey && _state.value.draft.isNotBlank()) _state.value.draft else stored
                    val movedFiles = if (_state.value.draftKey == newKey) _state.value.attachments else draftStore.getFiles(newKey)
                    draftJob?.cancel()
                    draftStore.set(session.id, moved)
                    draftStore.setFiles(session.id, movedFiles)
                    draftStore.set(newKey, "")
                    draftStore.setFiles(newKey, emptyList())
                    settingsStore.update { it.copy(lastDirectory = directory, lastSession = session.id) }
                    _state.update {
                        it.copy(
                            selected = session,
                            messages = emptyList(),
                            previous = null,
                            busy = false,
                            children = emptyList(),
                            draftKey = session.id,
                            draft = moved,
                            attachments = movedFiles,
                            sessions = listOf(session) + it.sessions.filter { existing -> existing.id != session.id },
                        )
                    }
                    if (moved.isNotBlank()) runCatching { api.setDraft(session.id, moved) }
                    loadPermissionsAndForms(session.id)
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, banner = error.message) }
                }
        }
    }

    fun setDirectory(directory: String) {
        viewModelScope.launch {
            settingsStore.update { it.copy(lastDirectory = directory) }
            _state.update { it.copy(settings = it.settings.copy(lastDirectory = directory)) }
            if (_state.value.selected == null) switchDraft("new:$directory")
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
        val session = _state.value.selected ?: return
        viewModelScope.launch {
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
        val session = _state.value.selected ?: return
        viewModelScope.launch {
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
        streamJob?.cancel()
        streamJob = null
        _state.update {
            it.copy(phase = Phase.Login, loginError = "сессия истекла, войдите снова", connected = false)
        }
    }
}
