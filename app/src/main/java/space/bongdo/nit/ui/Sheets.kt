package space.bongdo.nit.ui

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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Folder
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
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val filtered = remember(state.sessions, query) {
        if (query.isBlank()) state.sessions.filter { it.parentID == null && it.id !in state.hidden }
        else state.sessions.filter {
            it.parentID == null && it.id !in state.hidden &&
                (it.title.orEmpty().contains(query, ignoreCase = true) ||
                    it.location.directory.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true))
        }
    }
    val groups = remember(filtered) {
        filtered.groupBy { it.location.directory }.toList().sortedBy { it.first }
    }
    LaunchedEffect(query) {
        if (query.length < 2) return@LaunchedEffect
        delay(350)
        onSearch(query)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 760.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Все чаты", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${groups.size} ${plural(groups.size, "папка", "папки", "папок")} · ${filtered.size} ${plural(filtered.size, "чат", "чата", "чатов")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNew) {
                    Icon(Icons.Outlined.Add, contentDescription = "новый чат")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; if (it.isBlank()) onSearch("") },
                placeholder = { Text("Поиск чатов", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                },
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
            LazyColumn(Modifier.weight(1f, fill = false)) {
                groups.forEach { (directory, sessions) ->
                    item("head-$directory") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                File(directory).name.ifBlank { directory },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "  ${sessions.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            running = state.runningIds.contains(session.id),
                            selected = state.selected?.id == session.id,
                            onClick = { onSelect(session.id) },
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item("empty") {
                        Text(
                            if (state.searching) "ищу…" else "ничего не найдено",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
                item("bottom-space") { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun SessionRow(session: Session, running: Boolean, selected: Boolean, onClick: () -> Unit) {
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
                .background(if (running) Success else MaterialTheme.colorScheme.outlineVariant),
        )
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
                val order = listOf("system", "dark", "light")
                val next = order[(order.indexOf(state.settings.theme) + 1).mod(order.size)]
                onTheme(next)
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

private fun themeLabel(theme: String): String = when (theme) {
    "dark" -> "тёмная"
    "light" -> "светлая"
    else -> "как в системе"
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 26.dp)) {
            Text(if (create) "Где начать чат" else "Папка по умолчанию", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Тот же каталог, что в nit-gui на компьютере.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (!browse && state.projects.isNotEmpty()) {
                SectionLabel("Проекты")
                state.projects.take(12).forEach { project ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(project.canonical) }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            File(project.canonical).name.ifBlank { project.canonical },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            project.canonical,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(180.dp),
                        )
                    }
                }
                TextButton(onClick = {
                    browse = true
                    onLoad(state.settings.lastDirectory.ifBlank { null })
                }) {
                    Text("Выбрать другую папку")
                }
            } else {
                val listing = state.folders
                if (listing != null) {
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
                        TextButton(onClick = { onPick(listing.path) }) { Text("Здесь") }
                    }
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(listing.dirs, key = { it }) { dir ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onLoad(File(listing.path, dir).path) }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(9.dp))
                                Text(File(dir).name.ifBlank { dir }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Text("Загружаю…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        browse = true
                        onLoad("/")
                    }) { Text("Начать с /") }
                }
            }
        }
    }
}

