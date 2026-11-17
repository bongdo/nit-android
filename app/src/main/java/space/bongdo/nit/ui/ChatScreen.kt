package space.bongdo.nit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.UserFile
import java.io.File

@Composable
fun ChatScreen(state: UiState, viewModel: AppViewModel) {
    var sessionsOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var pickerCreates by rememberSaveable { mutableStateOf(true) }
    var themeOpen by rememberSaveable { mutableStateOf(false) }
    var subagentsOpen by rememberSaveable { mutableStateOf(false) }
    var attachments by remember { mutableStateOf<List<UserFile>>(emptyList()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val title = remember(state.selected, state.messages) {
        val firstUser = state.messages.firstOrNull { it.type == "user" }
        state.selected?.title?.takeIf { it.isNotBlank() }
            ?: firstUser?.text?.trim()?.lineSequence()?.firstOrNull()?.take(80)
            ?: "Новый чат"
    }
    val directory = state.selected?.location?.directory?.takeIf { it.isNotBlank() }
        ?: state.settings.lastDirectory
    val questionForm = state.forms.firstOrNull { it.isQuestionForm() }
    val otherForms = state.forms.filterNot { it.isQuestionForm() }
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= listState.layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(state.messages.size, state.running) {
        if (state.messages.isNotEmpty() && atBottom) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    var userScrolled by remember { mutableStateOf(false) }
    var anchorId by remember { mutableStateOf<String?>(null) }
    var anchorOffset by remember { mutableStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { (scrolling, index) ->
                if (scrolling) userScrolled = true
                val snapshot = viewModel.state.value
                if (userScrolled && index <= 1 && snapshot.previous != null && !snapshot.loadingOlder) {
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ChatTopBar(
                title = title,
                directory = directory,
                cost = state.selected?.cost ?: 0.0,
                context = contextLabel(state),
                connected = state.connected,
                onMenu = {
                    sessionsOpen = true
                    viewModel.refreshSessions("")
                    viewModel.loadProjects()
                },
                onMore = { settingsOpen = true },
                onTheme = { themeOpen = true },
                parent = state.selected?.parentID != null,
                children = state.children.size,
                runningChildren = state.children.count { it.id in state.runningIds },
                onParent = viewModel::backToParent,
                onSubagents = { subagentsOpen = true },
                onNew = {
                    pickerCreates = true
                    pickerOpen = true
                    viewModel.loadProjects()
                },
            )
            state.banner?.let { Banner(it, viewModel::dismissBanner) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    EmptyChat(directory)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
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
                        items(state.messages, key = { it.id }) { message ->
                            val lastAssistant = state.messages.lastOrNull { it.type == "assistant" }?.id
                            MessageItem(
                                message = message,
                                running = state.running && message.id == lastAssistant,
                                onOpenHtml = { source, title -> viewModel.openHtml(source, title) },
                                onOpenArtifact = { path, title -> viewModel.openArtifact(path, title) },
                                onOpenFile = { path, title -> viewModel.openFile(path, title) },
                            )
                        }
                    }
                    if (!atBottom && state.messages.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch { listState.animateScrollToItem(state.messages.lastIndex) }
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
                if (otherForms.isNotEmpty()) {
                    FormSheet(
                        forms = otherForms,
                        onReply = viewModel::replyForm,
                        onCancel = viewModel::cancelForm,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
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
            Composer(
                value = state.draft,
                onValueChange = viewModel::setDraft,
                directory = directory,
                running = state.running,
                busy = state.busy,
                attachments = attachments,
                onAttach = { uri, name ->
                    viewModel.upload(uri) { file ->
                        attachments = attachments + file.copy(name = name ?: file.name)
                    }
                },
                onRemove = { file -> attachments = attachments.filterNot { it == file } },
                onSend = { text, done ->
                    viewModel.send(text, attachments) { sent ->
                        if (sent) attachments = emptyList()
                        done(sent)
                    }
                },
                onStop = viewModel::stop,
                onCommand = { command ->
                    when (command) {
                        "new" -> {
                            pickerCreates = true
                            pickerOpen = true
                            viewModel.loadProjects()
                        }
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
        }
    }

    if (sessionsOpen) {
        SessionsSheet(
            state = state,
            onDismiss = { sessionsOpen = false },
            onSelect = { id ->
                sessionsOpen = false
                viewModel.openSession(id)
            },
            onSearch = viewModel::refreshSessions,
            onNew = {
                sessionsOpen = false
                pickerCreates = true
                pickerOpen = true
                viewModel.loadProjects()
            },
        )
    }
    if (subagentsOpen) {
        SubagentSheet(
            children = state.children,
            runningIds = state.runningIds,
            onDismiss = { subagentsOpen = false },
            onOpen = { child ->
                subagentsOpen = false
                viewModel.openSubagent(child)
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
    connected: Boolean,
    onMenu: () -> Unit,
    onMore: () -> Unit,
    onNew: () -> Unit,
    onTheme: () -> Unit,
    parent: Boolean,
    children: Int,
    runningChildren: Int,
    onParent: () -> Unit,
    onSubagents: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (parent) {
            IconButton(onClick = onParent) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "к родителю", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            IconButton(onClick = onMenu) {
                Icon(Icons.Outlined.Menu, contentDescription = "чаты", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .background(if (connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline),
        )
        if (children > 0) {
            Box(contentAlignment = Alignment.Center) {
                IconButton(onClick = onSubagents) {
                    Icon(Icons.Outlined.AccountTree, contentDescription = "субагенты", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (runningChildren > 0) "$children·$runningChildren" else "$children",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp),
                )
            }
        }
        IconButton(onClick = onNew) {
            Icon(Icons.Outlined.Add, contentDescription = "новый чат", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "настройки", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun contextLabel(state: UiState): String {
    val last = state.messages.lastOrNull { it.type == "assistant" }
    val tokens = last?.tokens ?: state.selected?.tokens ?: return ""
    val used = (tokens.input ?: 0) + (tokens.cache?.read ?: 0)
    val model = state.models.firstOrNull {
        it.id == state.selected?.model?.id && it.providerID == state.selected?.model?.providerID
    }
    val limit = model?.limit?.context ?: 0
    if (limit <= 0 || used <= 0) return ""
    val percent = ((used * 100) / limit).coerceIn(0, 100)
    return "ctx $percent%"
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

