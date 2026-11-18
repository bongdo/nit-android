package space.bongdo.nit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import space.bongdo.nit.data.Message
import space.bongdo.nit.data.ModelRef
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun Root(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NitTheme(theme = state.settings.theme, fontScale = state.settings.fontScale) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            when (state.phase) {
                Phase.Loading -> LoadingScreen()
                Phase.Login -> LoginScreen(state, viewModel)
                Phase.Ready -> ChatScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
    }
}

@Composable
fun SparkMark(size: Int = 44) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("✦", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SparkMark(38)
        Spacer(Modifier.height(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun sessionTitle(message: Message?, fallback: String): String {
    val text = message?.text?.trim().orEmpty()
    if (text.isNotBlank()) return text.lineSequence().first().take(90)
    return fallback
}

fun modelLabel(model: ModelRef?): String {
    if (model == null) return ""
    return model.variant?.let { "${model.id} · $it" } ?: model.id
}

fun shortTime(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val instant = Instant.ofEpochMilli(epochMillis)
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    return when {
        now.minusSeconds(60 * 60 * 12).isBefore(instant) ->
            DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(instant)
        now.minusSeconds(60 * 60 * 24 * 7).isBefore(instant) ->
            DateTimeFormatter.ofPattern("EEE").withZone(zone).format(instant)
        else -> DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("ru")).withZone(zone).format(instant)
    }
}

fun byteLabel(bytes: Long?): String {
    val value = bytes ?: return ""
    return when {
        value >= 1_048_576 -> String.format(java.util.Locale.ROOT, "%.1f MB", value / 1_048_576.0)
        value >= 1024 -> String.format(java.util.Locale.ROOT, "%.0f KB", value / 1024.0)
        else -> "$value B"
    }
}

@Composable
fun Banner(text: String, onDismiss: () -> Unit, action: String = "ok") {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(4.dp).clickableNoRipple(onDismiss),
            )
        }
    }
}
