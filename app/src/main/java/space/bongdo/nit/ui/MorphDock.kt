package space.bongdo.nit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.wrapContentHeight
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MorphDock(
    progress: Float,
    expandedHeight: Dp,
    sessionsListState: LazyListState,
    query: String,
    onQueryChange: (String) -> Unit,
    onProgress: (Float) -> Unit,
    onDragStart: () -> Unit,
    onGrab: () -> Unit,
    onSettle: (Boolean) -> Unit,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    collapsed: @Composable ColumnScope.() -> Unit,
    sessions: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentProgress by rememberUpdatedState(progress)
    val onProgressState = rememberUpdatedState(onProgress)
    val onSettleState = rememberUpdatedState(onSettle)
    val onDragStartState = rememberUpdatedState(onDragStart)
    val onGrabState = rememberUpdatedState(onGrab)
    val onToggleState = rememberUpdatedState(onToggle)

    var topHeightPx by remember { mutableFloatStateOf(with(density) { 112.dp.toPx() }) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var dragStartProgress by remember { mutableFloatStateOf(0f) }

    val tonguePx = with(density) { 32.dp.toPx() }
    val spacerPx = with(density) { 10.dp.toPx() }
    val searchPx = with(density) { 52.dp.toPx() }
    val expandedPx = with(density) { expandedHeight.toPx() }
    val collapsedPx = topHeightPx + tonguePx + spacerPx
    val rangePx = (expandedPx - collapsedPx).coerceAtLeast(1f)
    val rangeState = rememberUpdatedState(rangePx)
    val heightPx = collapsedPx + rangePx * progress.coerceIn(0f, 1f)
    val topBlockPx = topHeightPx + (searchPx - topHeightPx) * smooth(progress, 0.12f, 0.7f)
    val composerAlpha = 1f - smooth(progress, 0.05f, 0.45f)
    val searchAlpha = smooth(progress, 0.25f, 0.68f)
    val listAlpha = smooth(progress, 0.35f, 0.88f)
    val shapeRadius = 18.dp * smooth(progress, 0.05f, 0.4f)
    val rangeDragState = rememberUpdatedState(rangePx)

    fun scheduleSettle() {
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(170)
            onSettleState.value(currentProgress > 0.5f)
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    currentProgress > 0f &&
                    !sessionsListState.canScrollBackward
                ) {
                    settleJob?.cancel()
                    onGrabState.value()
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val current = currentProgress
                if (source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    current > 0f &&
                    !sessionsListState.canScrollBackward
                ) {
                    val next = (current - available.y / rangeState.value).coerceIn(0f, 1f)
                    onProgressState.value(next)
                    scheduleSettle()
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    fun dragModifier(): Modifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {
                settleJob?.cancel()
                dragStartProgress = currentProgress
                onDragStartState.value()
            },
            onVerticalDrag = { change, amount ->
                val next = (currentProgress - amount / rangeDragState.value).coerceIn(0f, 1f)
                onProgressState.value(next)
                change.consume()
            },
            onDragEnd = {
                val open = if (dragStartProgress > 0.5f) {
                    currentProgress > 0.65f
                } else {
                    currentProgress > 0.35f
                }
                onSettleState.value(open)
            },
            onDragCancel = {
                onSettleState.value(currentProgress > 0.5f)
            },
        )
    }

    Column(
        modifier
            .fillMaxWidth()
            .height(with(density) { heightPx.toDp() })
            .clip(
                RoundedCornerShape(
                    topStart = shapeRadius,
                    topEnd = shapeRadius,
                ),
            )
            .background(
                lerp(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.surface,
                    smooth(progress, 0.1f, 0.6f),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { tonguePx.toDp() })
                .pointerInput(Unit) { detectTapGestures { onToggleState.value() } }
                .then(dragModifier()),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .width(66.dp)
                    .height(21.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 5.dp, bottomEnd = 5.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 5.dp, bottomEnd = 5.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(30.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { topBlockPx.toDp() })
                .clipToBounds(),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(14.dp)
                    .then(dragModifier()),
            )
            Box(Modifier.fillMaxSize().alpha(composerAlpha)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { topHeightPx = it.height.toFloat() },
                ) {
                    Column(Modifier.fillMaxWidth(), content = collapsed)
                }
            }
            SearchRow(
                modifier = Modifier.fillMaxSize().alpha(searchAlpha),
                query = query,
                onQueryChange = onQueryChange,
                onClose = onClose,
                active = progress > 0.55f,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .alpha(listAlpha),
        ) {
            Box(Modifier.fillMaxSize().nestedScroll(connection)) {
                sessions()
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SearchRow(
    modifier: Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    active: Boolean,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(9.dp))
        if (active) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "Поиск по чатам",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "очистить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "свернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                query.ifEmpty { "Поиск по чатам" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun smooth(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)
