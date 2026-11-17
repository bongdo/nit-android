package space.bongdo.nit.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import space.bongdo.nit.data.Agent
import space.bongdo.nit.data.Model
import space.bongdo.nit.data.ModelRef
import space.bongdo.nit.data.Session
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsSheet(
    state: UiState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onSearch: (String) -> Unit,
    onNew: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var allDirectories by rememberSaveable { mutableStateOf(true) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val selectedDirectory = state.selected?.location?.directory ?: state.settings.lastDirectory
    val scoped = remember(state.sessions, state.hidden, query, allDirectories, selectedDirectory) {
        state.sessions.filter { session ->
            session.parentID == null && session.id !in state.hidden &&
                (allDirectories || selectedDirectory.isBlank() || session.location.directory == selectedDirectory) &&
                (query.isBlank() ||
                    session.title.orEmpty().contains(query, ignoreCase = true) ||
                    session.location.directory.contains(query, ignoreCase = true) ||
                    session.id.contains(query, ignoreCase = true))
        }
    }
    val running = scoped.filter { state.runningIds.contains(it.id) }
    val rest = scoped.filterNot { state.runningIds.contains(it.id) }
    val groups = remember(rest) { rest.groupBy { it.location.directory }.toList().sortedBy { it.first } }
    LaunchedEffect(query) {
        if (query.length < 2) return@LaunchedEffect
        delay(350)
        onSearch(query)
    }
    val directoryCount = scoped.map { it.location.directory }.distinct().size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 780.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Все чаты", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$directoryCount ${plural(directoryCount, "папка", "папки", "папок")} · ${scoped.size} ${plural(scoped.size, "чат", "чата", "чатов")}" +
                            if (running.isNotEmpty()) " · ${running.size} работает" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "закрыть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (selectedDirectory.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                    ScopeTab("Все", allDirectories) { allDirectories = true }
                    Spacer(Modifier.width(6.dp))
                    ScopeTab(File(selectedDirectory).name.ifBlank { selectedDirectory }, !allDirectories) { allDirectories = false }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; if (it.isBlank()) onSearch("") },
                placeholder = { Text("Поиск по названию, папке или id", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(17.dp)) },
                trailingIcon = if (query.isNotBlank()) {
                    { Icon(Icons.Outlined.Close, contentDescription = "очистить", modifier = Modifier.size(16.dp).clickableNoRipple { query = ""; onSearch("") }) }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onNew)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Новый чат", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (selectedDirectory.isBlank()) "выбрать папку" else File(selectedDirectory).name.ifBlank { selectedDirectory },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                if (running.isNotEmpty()) {
                    item("running-head") { GroupHeader("Работают", running.size, Icons.Outlined.PlayArrow) }
                    items(running, key = { "run-${it.id}" }) { session ->
                        SessionRow(
                            session = session,
                            running = true,
                            selected = state.selected?.id == session.id,
                            draft = state.drafts.contains(session.id),
                            waiting = state.waitingIds.contains(session.id),
                            onClick = { onSelect(session.id) },
                        )
                    }
                }
                groups.forEach { (directory, sessions) ->
                    item("head-$directory") {
                        GroupHeader(File(directory).name.ifBlank { directory }, sessions.size, Icons.Outlined.Folder)
                    }
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            running = false,
                            selected = state.selected?.id == session.id,
                            draft = state.drafts.contains(session.id),
                            waiting = state.waitingIds.contains(session.id),
                            onClick = { onSelect(session.id) },
                        )
                    }
                }
                if (scoped.isEmpty()) {
                    item("empty") {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(if (state.searching) "Ищу…" else "Пусто", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Начните новый чат или измените фильтр.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item("bottom-space") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ScopeTab(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickableNoRipple(onClick)
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun GroupHeader(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("  $count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun SessionRow(session: Session, running: Boolean, selected: Boolean, draft: Boolean, waiting: Boolean, onClick: () -> Unit) {
    val alpha = if (running) {
        val pulse = rememberInfiniteTransition(label = "running")
        val value by pulse.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "running-alpha",
        )
        value
    } else {
        1f
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (running) 7.dp else 4.dp)
                .clip(CircleShape)
                .background(
                    if (running) MaterialTheme.colorScheme.tertiary.copy(alpha = alpha)
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
        )
        if (waiting) {
            Spacer(Modifier.width(3.dp))
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.title?.takeIf { it.isNotBlank() } ?: session.id,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = session.agent?.takeIf { it.isNotBlank() }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (draft) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "есть черновик",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp).padding(end = 4.dp),
            )
        }
        Text(
            shortTime(session.time.updated.takeIf { it > 0 } ?: session.time.created),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: UiState,
    onDismiss: () -> Unit,
    onTheme: (String) -> Unit,
    onFont: (Float) -> Unit,
    onPickDirectory: () -> Unit,
    onModel: (ModelRef) -> Unit,
    onAgent: (String) -> Unit,
    onOpenTheme: () -> Unit,
    onLogout: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var modelsOpen by remember { mutableStateOf(false) }
    var agentsOpen by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 30.dp),
        ) {
            Text("Настройки", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            SettingsRow(Icons.Outlined.Folder, "Папка по умолчанию", state.settings.lastDirectory.ifBlank { "не выбрана" }) {
                onPickDirectory()
            }
            SettingsRow(
                Icons.Outlined.Memory,
                "Модель",
                state.selected?.model?.let { modelLabel(it) } ?: "по умолчанию",
            ) { modelsOpen = !modelsOpen }
            if (modelsOpen) {
                state.models.forEach { model ->
                    ChoiceRow(
                        title = model.name.ifBlank { model.id },
                        subtitle = model.providerID + if (model.status.isNotBlank()) " · ${model.status}" else "",
                        selected = state.selected?.model?.id == model.id && state.selected?.model?.providerID == model.providerID,
                    ) {
                        onModel(ModelRef(id = model.id, providerID = model.providerID))
                        modelsOpen = false
                    }
                    if (state.selected?.model?.id == model.id && model.variants.isNotEmpty()) {
                        Column(Modifier.padding(start = 34.dp)) {
                            ChoiceRow(
                                title = "Default",
                                subtitle = "вариант модели",
                                selected = state.selected?.model?.variant == null,
                            ) {
                                onModel(ModelRef(id = model.id, providerID = model.providerID))
                            }
                            model.variants.forEach { variant ->
                                ChoiceRow(
                                    title = variant.id,
                                    subtitle = "",
                                    selected = state.selected?.model?.variant == variant.id,
                                ) {
                                    onModel(ModelRef(id = model.id, providerID = model.providerID, variant = variant.id))
                                }
                            }
                        }
                    }
                }
            }
            SettingsRow(
                Icons.Outlined.SmartToy,
                "Агент",
                state.selected?.agent ?: "по умолчанию",
            ) { agentsOpen = !agentsOpen }
            if (agentsOpen) {
                state.agents.forEach { agent: Agent ->
                    ChoiceRow(
                        title = agent.name,
                        subtitle = agent.description.orEmpty(),
                        selected = state.selected?.agent == agent.id,
                    ) {
                        onAgent(agent.id.ifBlank { agent.name })
                        agentsOpen = false
                    }
                }
            }
            SettingsRow(Icons.Outlined.Palette, "Тема", themeLabel(state.settings.theme)) {
                onOpenTheme()
            }
            var font by remember(state.settings.fontScale) { mutableStateOf(state.settings.fontScale) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Размер текста", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = font,
                        onValueChange = { font = it },
                        onValueChangeFinished = { onFont(font) },
                        valueRange = 0.85f..1.35f,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(Icons.Outlined.Logout, "Выйти", state.settings.server) { onLogout() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(170.dp),
        )
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(start = 30.dp, top = 7.dp, bottom = 7.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorySheet(
    state: UiState,
    create: Boolean,
    onDismiss: () -> Unit,
    onLoad: (String?) -> Unit,
    onPick: (String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var browse by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }
    var pathError by remember { mutableStateOf<String?>(null) }

    fun openPath(path: String) {
        val value = path.trim()
        if (!value.startsWith("/")) {
            pathError = "Нужен абсолютный путь, например /home/ubuntu/Projects"
            return
        }
        pathError = null
        browse = true
        onLoad(value)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (create) "Где начать чат" else "Папка по умолчанию", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Тот же каталог, что в nit-gui на компьютере",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "закрыть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it; pathError = null },
                    placeholder = { Text("/абсолютный/путь", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(9.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { openPath(pathInput) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { openPath(pathInput) }) { Text("Открыть") }
            }
            pathError?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))

            if (!browse) {
                if (state.projects.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Загружаю проекты…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        browse = true
                        onLoad(state.settings.lastDirectory.ifBlank { null })
                    }) { Text("Другая папка") }
                } else {
                    GroupHeader("Проекты", state.projects.size, Icons.Outlined.FolderOpen)
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(state.projects, key = { it.id }) { project ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { onPick(project.canonical) }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(File(project.canonical).name.ifBlank { project.canonical }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(project.canonical, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        browse = true
                        onLoad(state.settings.lastDirectory.ifBlank { null })
                    }) { Text("Другая папка") }
                }
            } else {
                val listing = state.folders
                if (listing == null) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Загружаю папку…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { openPath("/") }) { Text("Начать с /") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { browse = false }) { Text("Проекты") }
                        IconButton(onClick = { onLoad(listing.parent.ifBlank { null }) }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "назад", modifier = Modifier.size(18.dp))
                        }
                        Text(
                            listing.path,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(listing.dirs, key = { it }) { dir ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onLoad(File(listing.path, dir).path) }
                                    .padding(horizontal = 4.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(9.dp))
                                Text(File(dir).name.ifBlank { dir }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (listing.dirs.isEmpty()) {
                            item("empty") {
                                Text("Пустая папка", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { onPick(listing.path) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (create) "Начать чат здесь" else "Выбрать эту папку")
                    }
                }
            }
        }
    }
}
