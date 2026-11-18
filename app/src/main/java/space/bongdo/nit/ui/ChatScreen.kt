package space.bongdo.nit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.UserFile
import java.io.File

@Composable
fun ChatScreen(state: UiState, viewModel: AppViewModel) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var pickerCreates by rememberSaveable { mutableStateOf(true) }
    var themeOpen by rememberSaveable { mutableStateOf(false) }
    var topMenuOpen by rememberSaveable { mutableStateOf(false) }
    var sessionsQuery by rememberSaveable { mutableStateOf("") }
    var dockProgress by remember { mutableFloatStateOf(0f) }
    var dockJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()
    val sessionsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focusRequest by remember { mutableStateOf(0) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val title = remember(state.selected, state.messages) {
        val firstUser = state.messages.firstOrNull { it.type == "user" }
        state.selected?.title?.takeIf { it.isNotBlank() }
            ?: firstUser?.text?.trim()?.lineSequence()?.firstOrNull()?.take(80)
            ?: "Новый чат"
    }
    val directory = state.selected?.location?.directory?.takeIf { it.isNotBlank() }
        ?: state.settings.lastDirectory
    val display = remember(state.messages, state.running, state.details) {
        displayMessages(state.messages, state.running, state.details)
    }
    val questionForm = state.forms.firstOrNull { it.isQuestionForm() }
    val otherForms = state.forms.filterNot { it.isQuestionForm() }
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    var stick by remember { mutableStateOf(true) }

    fun newChat() {
        val directory = state.settings.lastDirectory
        if (directory.isNotBlank()) {
            viewModel.startNewSession(directory)
        } else {
            pickerCreates = true
            pickerOpen = true
            viewModel.loadProjects()
        }
    }

    fun animateDock(target: Float) {
        dockJob?.cancel()
        dockJob = scope.launch {
            animate(
                initialValue = dockProgress,
                targetValue = target,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
            ) { value, _ -> dockProgress = value }
            if (target == 0f) {
                keyboard?.hide()
                focusManager.clearFocus()
                if (stick) {
                    val total = listState.layoutInfo.totalItemsCount
                    if (total > 0) listState.scrollToItem(total - 1, Int.MAX_VALUE)
                }
            }
        }
    }

    fun openDock() {
        keyboard?.hide()
        focusManager.clearFocus()
        viewModel.refreshSessions(sessionsQuery)
        viewModel.loadProjects()
        animateDock(1f)
    }

    fun closeDock() {
        animateDock(0f)
    }

    BackHandler(enabled = dockProgress > 0.5f) { closeDock() }

    LaunchedEffect(sessionsQuery) {
        if (sessionsListState.layoutInfo.totalItemsCount > 0) sessionsListState.scrollToItem(0)
        if (sessionsQuery.isBlank()) {
            viewModel.refreshSessions("")
            return@LaunchedEffect
        }
        if (sessionsQuery.length >= 2) delay(350)
        viewModel.refreshSessions(sessionsQuery)
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val displayVersion = display.lastOrNull()?.content?.sumOf { it.text.length } ?: 0
    LaunchedEffect(display.size, displayVersion, state.running, state.compacting) {
        val total = listState.layoutInfo.totalItemsCount
        if ((display.isNotEmpty() || state.compacting) && stick && total > 0) {
            listState.scrollToItem(total - 1, Int.MAX_VALUE)
        }
    }
    LaunchedEffect(state.selected?.id) {
        stick = true
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.scrollToItem(total - 1, Int.MAX_VALUE)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }
            .distinctUntilChanged()
            .collect { forward ->
                val total = listState.layoutInfo.totalItemsCount
                if (forward && stick && total > 0) {
                    listState.scrollToItem(total - 1, Int.MAX_VALUE)
                }
            }
    }

    var userScrolled by remember { mutableStateOf(false) }
    var userDragged by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        launch {
            listState.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> {
                        userScrolled = true
                        userDragged = true
                        stick = false
                    }
                    else -> Unit
                }
            }
        }
        launch {
            snapshotFlow { listState.isScrollInProgress to atBottom }
                .distinctUntilChanged()
                .collect { (scrolling, bottom) -> if (!scrolling && !bottom) userDragged = false }
        }
        var previousBottom = atBottom
        snapshotFlow { atBottom }
            .distinctUntilChanged()
            .collect { bottom ->
                if (bottom != previousBottom) {
                    if (!bottom) {
                        if (userDragged) focusManager.clearFocus()
                    } else {
                        stick = true
                        if (userDragged) {
                            userDragged = false
                            focusRequest += 1
                        }
                    }
                    previousBottom = bottom
                }
            }
    }
    var anchorId by remember { mutableStateOf<String?>(null) }
    var anchorOffset by remember { mutableStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { (scrolling, index) ->
                if (scrolling) userScrolled = true
                val snapshot = viewModel.state.value
                if (userDragged && index <= 1 && snapshot.previous != null && !snapshot.loadingOlder) {
                    anchorId = snapshot.messages.getOrNull(listState.firstVisibleItemIndex)?.id
                    anchorOffset = listState.firstVisibleItemScrollOffset
                    viewModel.loadOlder()
                }
            }
    }
    LaunchedEffect(state.messages.firstOrNull()?.id) {
        val id = anchorId ?: return@LaunchedEffect
        val index = state.messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            listState.scrollToItem(index, anchorOffset)
            anchorId = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val available = (maxHeight - ime).coerceAtLeast(0.dp)
        val expandedHeight = available * 0.78f

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (display.isEmpty()) {
                    if (state.loadingMessages) ChatSkeleton() else EmptyChat(directory)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = topInset + 56.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (state.loadingOlder) {
                            item("loader") {
                                Text(
                                    "загружаю раньше…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                )
                            }
                        }
                        items(display, key = { it.id }) { message ->
                            val lastAssistant = display.lastOrNull { it.type == "assistant" }?.id
                            MessageItem(
                                message = message,
                                running = state.running && message.id == lastAssistant,
                                directory = directory,
                                stepsLoading = state.loadingSteps,
                                imageModel = viewModel::imageModel,
                                onLoadSteps = viewModel::loadSteps,
                                onOpenHtml = { source, title -> viewModel.openHtml(source, title) },
                                onOpenArtifact = { path, title -> viewModel.openArtifact(path, title) },
                                onOpenFile = { path, title -> viewModel.openFile(path, title) },
                            )
                        }
                        if (state.running && display.lastOrNull()?.type == "user") {
                            item("typing") { TypingLine() }
                        }
                        if (state.compacting && display.lastOrNull()?.type != "compaction") {
                            item("compacting") { CompactionBusyLine() }
                        }
                    }
                    if (!atBottom && display.isNotEmpty() && dockProgress < 0.1f) {
                        FloatingActionButton(
                            onClick = {
                                stick = true
                                scope.launch {
                                    val total = listState.layoutInfo.totalItemsCount
                                    if (total > 0) {
                                        listState.animateScrollToItem(total - 1)
                                        listState.scrollToItem(total - 1, Int.MAX_VALUE)
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp).size(42.dp),
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = "вниз", modifier = Modifier.size(20.dp))
                        }
                    }
                }
                if (dockProgress > 0.01f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = dockProgress * 0.3f))
                            .clickable { closeDock() },
                    )
                }
                if (otherForms.isNotEmpty()) {
                    FormSheet(
                        forms = otherForms,
                        onReply = viewModel::replyForm,
                        onCancel = viewModel::cancelForm,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                MorphDock(
                    progress = dockProgress,
                    expandedHeight = expandedHeight,
                    sessionsListState = sessionsListState,
                    query = sessionsQuery,
                    onQueryChange = { sessionsQuery = it },
                    onProgress = { dockProgress = it },
                    onDragStart = {
                        dockJob?.cancel()
                        keyboard?.hide()
                        focusManager.clearFocus()
                    },
                    onGrab = { dockJob?.cancel() },
                    onSettle = { open -> animateDock(if (open) 1f else 0f) },
                    onToggle = { if (dockProgress > 0.5f) closeDock() else openDock() },
                    onClose = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        closeDock()
                    },
                    collapsed = {
                        if (state.permissions.isNotEmpty()) {
                            PermissionStrip(state.permissions, viewModel::replyPermission)
                        }
                        if (questionForm != null) {
                            QuestionAnswerBar(
                                form = questionForm,
                                onReply = viewModel::replyForm,
                                onCancel = viewModel::cancelForm,
                            )
                        } else {
                            ComposerBody(
                                value = state.draft,
                                onValueChange = viewModel::setDraft,
                                directory = directory,
                                running = state.running,
                                busy = state.busy,
                                attachments = state.attachments,
                                imageModel = viewModel::imageModel,
                                focusRequest = focusRequest,
                                onAttach = { uri, name ->
                                    viewModel.upload(uri) { file -> viewModel.addAttachment(file.copy(name = name ?: file.name)) }
                                },
                                onRemove = viewModel::removeAttachment,
                                onSend = { text, delivery, done ->
                                    stick = true
                                    viewModel.send(text, state.attachments, delivery) { sent -> done(sent) }
                                },
                                onStop = viewModel::stop,
                                onCommand = { command ->
                                    when (command) {
                                        "new" -> newChat()
                                        "dir" -> {
                                            pickerCreates = false
                                            pickerOpen = true
                                            viewModel.loadProjects()
                                        }
                                        "settings" -> settingsOpen = true
                                        "compact" -> viewModel.compactSession()
                                        "revert" -> viewModel.revertLast()
                                        "fork" -> viewModel.forkLastUser()
                                        "fork-here" -> viewModel.forkAt(null)
                                        "retry" -> viewModel.retryLast()
                                        "theme" -> themeOpen = true
                                        "clear" -> viewModel.setDraft("")
                                        "help" -> viewModel.note("Команды: /new /dir /settings /compact /revert /fork /fork-here /retry /theme /clear /help")
                                    }
                                },
                            )
                        }
                    },
                    sessions = {
                        SessionsContent(
                            state = state,
                            query = sessionsQuery,
                            listState = sessionsListState,
                            onSelect = { id ->
                                closeDock()
                                viewModel.openSession(id)
                            },
                            onNew = {
                                closeDock()
                                newChat()
                            },
                        )
                    },
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(topInset)
                    .background(MaterialTheme.colorScheme.background),
            )
            ChatTopBar(
                title = title,
                directory = directory,
                cost = state.selected?.cost ?: 0.0,
                context = contextPercent(state).takeIf { it > 0 }?.let { "ctx $it%" } ?: "",
                contextProgress = contextPercent(state) / 100f,
                connected = state.healthOk && state.streamStatus == StreamStatus.Online,
                connecting = state.streamStatus == StreamStatus.Connecting,
                parent = state.selected?.parentID != null,
                onParent = {
                    closeDock()
                    viewModel.backToParent()
                },
                onNew = {
                    closeDock()
                    newChat()
                },
                onSwipeDown = {
                    closeDock()
                    topMenuOpen = true
                },
            )
            if (!state.healthOk) {
                Banner("Нет связи с сервером. Повторяем…", viewModel::retryConnection, action = "повторить")
            } else {
                state.banner?.let { Banner(it, viewModel::dismissBanner) }
            }
        }
    }

    if (topMenuOpen) {
        ChatMenuSheet(
            hasParent = state.selected?.parentID != null,
            children = state.children,
            runningIds = state.runningIds,
            onDismiss = { topMenuOpen = false },
            onOpenSubagent = { child ->
                topMenuOpen = false
                viewModel.openSubagent(child)
            },
            onAction = { action ->
                topMenuOpen = false
                when (action) {
                    "new" -> {
                        newChat()
                    }
                    "dir" -> {
                        pickerCreates = false
                        pickerOpen = true
                        viewModel.loadProjects()
                    }
                    "settings" -> settingsOpen = true
                    "theme" -> themeOpen = true
                    "compact" -> viewModel.compactSession()
                    "revert" -> viewModel.revertLast()
                    "fork" -> viewModel.forkLastUser()
                    "fork-here" -> viewModel.forkAt(null)
                    "retry" -> viewModel.retryLast()
                    "parent" -> viewModel.backToParent()
                    "chats" -> openDock()
                }
            },
        )
    }

    if (themeOpen) {
        ThemeSheet(
            current = state.settings.theme,
            onDismiss = { themeOpen = false },
            onPick = viewModel::setTheme,
        )
    }
    if (settingsOpen) {
        SettingsSheet(
            state = state,
            onDismiss = { settingsOpen = false },
            onTheme = viewModel::setTheme,
            onFont = viewModel::setFontScale,
            onPickDirectory = {
                settingsOpen = false
                pickerCreates = false
                pickerOpen = true
                viewModel.loadProjects()
            },
            onModel = viewModel::switchModel,
            onAgent = viewModel::switchAgent,
            onOpenTheme = { themeOpen = true },
            onLogout = viewModel::logout,
        )
    }
    if (pickerOpen) {
        DirectorySheet(
            state = state,
            create = pickerCreates,
            onDismiss = { pickerOpen = false },
            onLoad = viewModel::loadFolders,
            onPick = { path ->
                pickerOpen = false
                if (pickerCreates) viewModel.startNewSession(path) else viewModel.setDirectory(path)
            },
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    directory: String,
    cost: Double,
    context: String,
    contextProgress: Float,
    connected: Boolean,
    connecting: Boolean,
    onNew: () -> Unit,
    parent: Boolean,
    onParent: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    var dragY by remember { mutableStateOf(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                        androidx.compose.ui.graphics.Color.Transparent,
                    ),
                ),
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onVerticalDrag = { change, amount -> dragY += amount; change.consume() },
                    onDragEnd = {
                        if (dragY > 56f) onSwipeDown()
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                )
            }
            .padding(bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (parent) {
            IconButton(onClick = onParent) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "к родителю", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (directory.isNotBlank()) {
                    Text(
                        File(directory).name.ifBlank { directory },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (cost > 0 || context.isNotBlank()) {
                    Text(
                        "  ·  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                if (context.isNotBlank()) {
                    Text(context, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(5.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { contextProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.width(34.dp).height(3.dp),
                        color = if (contextProgress > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
                if (cost > 0) {
                    Text(
                        if (context.isNotBlank()) " · " else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        if (cost < 0.01) "<\$0.01" else String.format(java.util.Locale.ROOT, "~\$%.2f", cost),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
        Box(
            Modifier
                .padding(end = 2.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when {
                        connecting -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                        connected -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                ),
        )
        IconButton(onClick = onNew) {
            Icon(Icons.Outlined.Add, contentDescription = "новый чат", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }
}

private fun contextPercent(state: UiState): Int {
    val last = state.messages.lastOrNull { it.type == "assistant" }
    val tokens = last?.tokens ?: state.selected?.tokens ?: return 0
    val used = (tokens.input ?: 0) + (tokens.output ?: 0) + (tokens.cache?.read ?: 0)
    val model = state.models.firstOrNull {
        it.id == state.selected?.model?.id && it.providerID == state.selected?.model?.providerID
    }
    val limit = model?.limit?.context ?: 0
    if (limit <= 0 || used <= 0) return 0
    return ((used * 100) / limit).toInt().coerceIn(0, 100)
}

@Composable
private fun ChatSkeleton() {
    val phase = rememberInfiniteTransition(label = "skeleton")
    val alpha by phase.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 90.dp)) {
        repeat(3) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index == 1) 0.72f else 0.9f)
                    .height(if (index == 1) 54.dp else 38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun EmptyChat(directory: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SparkMark(46)
        Spacer(Modifier.height(16.dp))
        Text("Спроси что угодно", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Код, таблицы и HTML-превью рендерятся в чате.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (directory.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Pill(File(directory).name.ifBlank { directory }, background = MaterialTheme.colorScheme.surface)
        }
    }
}

