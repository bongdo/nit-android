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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
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

@Composable
fun SessionsContent(
    state: UiState,
    query: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onHide: (String) -> Unit,
    onUnhide: (String) -> Unit,
) {
    var allDirectories by rememberSaveable { mutableStateOf(true) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val selectedDirectory = state.selected?.location?.directory ?: state.settings.lastDirectory
    val visible = remember(state.sessions, state.hidden, query, allDirectories, selectedDirectory, showHidden) {
        state.sessions.filter { session ->
            val parentHidden = session.parentID != null &&
                session.parentID in state.hidden && !showHidden
            (showHidden || session.id !in state.hidden) &&
                !parentHidden &&
                (allDirectories || selectedDirectory.isBlank() || session.location.directory == selectedDirectory) &&
                (query.isBlank() ||
                    session.title.orEmpty().contains(query, ignoreCase = true) ||
                    session.location.directory.contains(query, ignoreCase = true) ||
                    session.id.contains(query, ignoreCase = true))
        }
    }
    val visibleIds = visible.mapTo(mutableSetOf()) { it.id }
    val roots = visible.filter { it.parentID == null || it.parentID !in visibleIds }
    val childrenByParent = visible.filter { it.parentID != null }.groupBy { it.parentID }
    val hiddenCount = state.hidden.size

    LaunchedEffect(state.selected?.id) {
        val id = state.selected?.id ?: return@LaunchedEffect
        val parent = state.sessions.firstOrNull { it.id == id }?.parentID ?: return@LaunchedEffect
        expanded = expanded + parent
    }

    fun kindOf(session: Session): SessionKind {
        val childRunning = childrenByParent[session.id].orEmpty().any { it.id in state.runningIds }
        val childWaiting = childrenByParent[session.id].orEmpty().any { it.id in state.waitingIds }
        return when {
            state.waitingIds.contains(session.id) || childWaiting -> SessionKind.WAITING
            state.runningIds.contains(session.id) || childRunning -> SessionKind.RUNNING
            state.status[session.id] == "failed" -> SessionKind.FAILED
            state.status[session.id] == "done" -> SessionKind.DONE
            else -> SessionKind.NONE
        }
    }

    val waiting = roots.filter { kindOf(it) == SessionKind.WAITING }
    val running = roots.filter { kindOf(it) == SessionKind.RUNNING && !waiting.contains(it) }
    val failed = roots.filter { kindOf(it) == SessionKind.FAILED }
    val done = roots.filter { kindOf(it) == SessionKind.DONE }
    val finished = (waiting + failed + done)
        .distinctBy { it.id }
        .sortedWith(compareBy({ finishRank(it, state) }, { -it.time.updated }))
    val hoisted = finished.map { it.id }.toSet() + running.map { it.id }.toSet()
    val rest = roots.filterNot { it.id in hoisted }
    val groups = remember(rest) { rest.groupBy { it.location.directory }.toList().sortedBy { it.first } }
    val directoryCount = roots.map { it.location.directory }.distinct().size
    val waitingCount = waiting.size

    fun nodesFor(list: List<Session>): List<SessionNode> = list.flatMap { root ->
        val children = childrenByParent[root.id].orEmpty()
        val open = expanded.contains(root.id)
        val rootNode = SessionNode(
            session = root,
            depth = 0,
            kind = kindOf(root),
            hasChildren = children.isNotEmpty(),
            expanded = open,
            hidden = root.id in state.hidden,
        )
        listOf(rootNode) + if (open) {
            children.map { child ->
                SessionNode(
                    session = child,
                    depth = 1,
                    kind = kindOf(child),
                    hasChildren = false,
                    expanded = false,
                    hidden = child.id in state.hidden,
                )
            }
        } else {
            emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScopeTab("All", allDirectories) { allDirectories = true }
            if (selectedDirectory.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                ScopeTab(File(selectedDirectory).name.ifBlank { selectedDirectory }, !allDirectories) {
                    allDirectories = false
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "$directoryCount ${if (directoryCount == 1) "dir" else "dirs"} · " +
                    "${roots.size} ${if (roots.size == 1) "chat" else "chats"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            if (hiddenCount > 0) {
                IconButton(onClick = { showHidden = !showHidden }) {
                    Icon(
                        if (showHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (showHidden) "Hide hidden chats" else "Show hidden chats",
                        tint = if (showHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onNew) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "New chat",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (waitingCount > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(attentionColor()),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "$waitingCount ${if (waitingCount == 1) "chat is" else "chats are"} waiting for you",
                    style = MaterialTheme.typography.labelSmall,
                    color = attentionColor(),
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        ) {
            val finishedNodes = nodesFor(finished)
            if (finishedNodes.isNotEmpty()) {
                item("finished-head") {
                    GroupHeader("Waiting & finished", finished.size, Icons.Outlined.PriorityHigh, accent = true)
                }
                items(finishedNodes, key = { "node-${it.session.id}" }) { node ->
                    SessionRow(
                        node = node,
                        selected = state.selected?.id == node.session.id,
                        draft = state.drafts.contains(node.session.id),
                        onToggle = {
                            expanded = if (expanded.contains(node.session.id)) {
                                expanded - node.session.id
                            } else {
                                expanded + node.session.id
                            }
                        },
                        onUnhide = { onUnhide(node.session.id) },
                        onHide = { onHide(node.session.id) },
                        onClick = { onSelect(node.session.id) },
                    )
                }
            }
            val runningNodes = nodesFor(running)
            if (runningNodes.isNotEmpty()) {
                item("running-head") { GroupHeader("Running", running.size, Icons.Outlined.PlayArrow) }
                items(runningNodes, key = { "node-${it.session.id}" }) { node ->
                    SessionRow(
                        node = node,
                        selected = state.selected?.id == node.session.id,
                        draft = state.drafts.contains(node.session.id),
                        onToggle = {
                            expanded = if (expanded.contains(node.session.id)) {
                                expanded - node.session.id
                            } else {
                                expanded + node.session.id
                            }
                        },
                        onUnhide = { onUnhide(node.session.id) },
                        onHide = { onHide(node.session.id) },
                        onClick = { onSelect(node.session.id) },
                    )
                }
            }
            groups.forEach { (directory, sessions) ->
                val nodes = nodesFor(sessions)
                item("head-$directory") {
                    GroupHeader(File(directory).name.ifBlank { directory }, sessions.size, Icons.Outlined.Folder)
                }
                items(nodes, key = { "node-${it.session.id}" }) { node ->
                    SessionRow(
                        node = node,
                        selected = state.selected?.id == node.session.id,
                        draft = state.drafts.contains(node.session.id),
                        onToggle = {
                            expanded = if (expanded.contains(node.session.id)) {
                                expanded - node.session.id
                            } else {
                                expanded + node.session.id
                            }
                        },
                        onUnhide = { onUnhide(node.session.id) },
                        onHide = { onHide(node.session.id) },
                        onClick = { onSelect(node.session.id) },
                    )
                }
            }
            if (roots.isEmpty()) {
                item("empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.searching) "Searching…" else "No chats",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Start a new chat or change the filter.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

data class SessionNode(
    val session: Session,
    val depth: Int,
    val kind: SessionKind,
    val hasChildren: Boolean,
    val expanded: Boolean,
    val hidden: Boolean,
)

private fun finishRank(session: Session, state: UiState): Int = when {
    state.waitingIds.contains(session.id) -> 0
    state.status[session.id] == "failed" -> 1
    else -> 2
}

enum class SessionKind { NONE, RUNNING, WAITING, DONE, FAILED }

@Composable
private fun attentionColor(): androidx.compose.ui.graphics.Color {
    val spark = LocalSpark.current
    return if (spark == androidx.compose.ui.graphics.Color.Unspecified) {
        MaterialTheme.colorScheme.tertiary
    } else {
        spark
    }
}

@Composable
private fun successColor(): androidx.compose.ui.graphics.Color {
    val success = LocalSuccess.current
    return if (success == androidx.compose.ui.graphics.Color.Unspecified) {
        MaterialTheme.colorScheme.tertiary
    } else {
        success
    }
}

@Composable
private fun Modifier.sessionGlow(kind: SessionKind): Modifier {
    if (kind != SessionKind.WAITING && kind != SessionKind.DONE && kind != SessionKind.FAILED) return this
    val accent = when (kind) {
        SessionKind.WAITING -> attentionColor()
        SessionKind.DONE -> successColor()
        else -> MaterialTheme.colorScheme.error
    }
    val duration = when (kind) {
        SessionKind.WAITING -> 2800
        SessionKind.DONE -> 5000
        else -> 4400
    }
    val transition = rememberInfiniteTransition(label = "session-glow")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "session-shift",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration / 2, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "session-breathe",
    )
    return drawBehind {
        drawRect(accent.copy(alpha = 0.035f + breathe * 0.035f))
        val x = (shift * 2.4f - 0.7f) * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    androidx.compose.ui.graphics.Color.Transparent,
                    accent.copy(alpha = 0.18f),
                    androidx.compose.ui.graphics.Color.Transparent,
                ),
                start = androidx.compose.ui.geometry.Offset(x - size.width * 0.45f, 0f),
                end = androidx.compose.ui.geometry.Offset(x + size.width * 0.45f, size.height),
            ),
        )
    }
}

@Composable
private fun SessionRow(
    node: SessionNode,
    selected: Boolean,
    draft: Boolean,
    onToggle: () -> Unit,
    onUnhide: () -> Unit,
    onHide: () -> Unit,
    onClick: () -> Unit,
) {
    val session = node.session
    val kind = node.kind
    val running = kind == SessionKind.RUNNING
    val waiting = kind == SessionKind.WAITING
    val done = kind == SessionKind.DONE
    val failed = kind == SessionKind.FAILED
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
    val shape = RoundedCornerShape(9.dp)
    val dotColor = when {
        waiting -> attentionColor()
        failed -> MaterialTheme.colorScheme.error
        done -> successColor()
        running -> MaterialTheme.colorScheme.tertiary.copy(alpha = alpha)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp + if (node.depth > 0) 18.dp else 0.dp, end = 8.dp, top = 1.dp, bottom = 1.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .sessionGlow(kind)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.depth == 0 && node.hasChildren) {
            Box(
                Modifier
                    .size(18.dp)
                    .clickableNoRipple(onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (node.expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                    contentDescription = if (node.expanded) "Collapse subagents" else "Expand subagents",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
        } else if (node.depth > 0) {
            Icon(
                Icons.Outlined.SubdirectoryArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Box(
            Modifier
                .size(if (running || waiting) 7.dp else 4.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.title?.takeIf { it.isNotBlank() } ?: session.id,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                waiting -> if (node.depth > 0) "Subagent · waiting" else "Waiting for you"
                failed -> if (node.depth > 0) "Subagent · failed" else "Failed"
                done -> if (node.depth > 0) "Subagent · done" else "Done"
                running -> if (node.depth > 0) "Subagent · running" else "Running"
                node.depth > 0 -> "Subagent"
                else -> session.agent?.takeIf { it.isNotBlank() }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (waiting) attentionColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (draft) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Draft saved",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp).padding(end = 4.dp),
            )
        }
        if (node.hidden) {
            IconButton(onClick = onUnhide, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = "Unhide chat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
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
private fun GroupHeader(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (accent) Brush.horizontalGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), androidx.compose.ui.graphics.Color.Transparent),
                ) else Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Transparent)),
            )
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (accent) attentionColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) attentionColor() else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("  $count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            SettingsRow(Icons.Outlined.Folder, "Default folder", state.settings.lastDirectory.ifBlank { "not selected" }) {
                onPickDirectory()
            }
            SettingsRow(
                Icons.Outlined.Memory,
                "Model",
                state.selected?.model?.let { modelLabel(it) } ?: "default",
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
                                subtitle = "model variant",
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
                "Agent",
                state.selected?.agent ?: "default",
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
            SettingsRow(Icons.Outlined.Palette, "Theme", themeLabel(state.settings.theme)) {
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
                    Text("Text size", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = font,
                        onValueChange = { font = it },
                        onValueChangeFinished = { onFont(font) },
                        valueRange = 0.85f..1.35f,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(Icons.Outlined.Logout, "Sign out", state.settings.server) { onLogout() }
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

@Composable
fun DirectoryPicker(
    state: UiState,
    create: Boolean,
    banner: String?,
    onDismiss: () -> Unit,
    onDismissBanner: () -> Unit,
    onLoad: (String?) -> Unit,
    onPick: (String) -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    var query by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }
    var pathError by remember { mutableStateOf<String?>(null) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    val listing = state.folders
    val path = listing?.path ?: selectedPath ?: state.settings.lastDirectory
    fun go(target: String) {
        selectedPath = target
        onLoad(target)
    }
    LaunchedEffect(Unit) { onLoad(state.settings.lastDirectory.ifBlank { null }) }
    val dirs = remember(listing, query) {
        val all = listing?.dirs.orEmpty()
        if (query.isBlank()) all else all.filter { it.contains(query, ignoreCase = true) }
    }
    val recent = remember(state.projects) {
        state.projects.map { it.canonical }.distinct().take(6)
    }

    fun openPath(value: String) {
        val trimmed = value.trim()
        if (!trimmed.startsWith("/")) {
            pathError = "Enter an absolute path, for example /home/user/project"
            return
        }
        pathError = null
        go(trimmed)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (create) "New chat" else "Default folder",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (create) "Choose where this chat lives" else "Choose the default folder",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    manual = !manual
                    pathInput = path
                    pathError = null
                }) {
                    Icon(
                        Icons.Outlined.Keyboard,
                        contentDescription = "Type a path",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (manual) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it; pathError = null },
                        placeholder = { Text("/absolute/path", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(9.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { openPath(pathInput) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { openPath(pathInput) }) { Text("Go") }
                }
                pathError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search folders", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(17.dp)) },
                trailingIcon = if (query.isNotBlank()) {
                    { Icon(Icons.Outlined.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp).clickableNoRipple { query = "" }) }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            )
            if (!banner.isNullOrBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        banner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onLoad(path) }) { Text("Retry") }
                    TextButton(onClick = onDismissBanner) { Text("Dismiss") }
                }
            }
            if (recent.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    recent.forEach { item ->
                        val active = item == path
                        Text(
                            File(item).name.ifBlank { item },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickableNoRipple { go(item) }
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            if (path.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("/", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val parts = path.trim('/').split('/').filter { it.isNotBlank() }
                    var acc = ""
                    parts.forEachIndexed { index, part ->
                        acc += "/$part"
                        val target = acc
                        Text(
                            part,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (index == parts.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .clickableNoRipple { go(target) }
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                        )
                        if (index != parts.lastIndex) {
                            Text("/", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (listing == null) {
                    item("loading") {
                        Text(
                            if (banner.isNullOrBlank()) "Loading folder…" else "Could not load this folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                } else {
                    if (path != "/" && path.isNotBlank()) {
                        item("up") {
                            FolderRow(
                                name = "..",
                                hint = "Up",
                                onClick = { go(File(path).parent) },
                            )
                        }
                    }
                    items(dirs, key = { it }) { dir ->
                        FolderRow(
                            name = File(dir).name.ifBlank { dir },
                            hint = "Folder",
                            onClick = { go(File(path, dir).path) },
                        )
                    }
                    if (dirs.isEmpty()) {
                        item("empty") {
                            Text(
                                if (query.isBlank()) "No folders here" else "No folders match",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    path.ifBlank { "No folder selected" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Button(
                    onClick = { if (listing != null && path.isNotBlank()) onPick(path) },
                    enabled = listing != null && path.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (create) "Start chat here" else "Use this folder")
                }
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(15.dp),
        )
    }
}
