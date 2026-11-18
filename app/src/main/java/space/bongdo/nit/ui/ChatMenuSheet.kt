package space.bongdo.nit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatMenuPanel(
    hasSelection: Boolean,
    running: Boolean,
    onCompact: () -> Unit,
    onFork: () -> Unit,
    onRetry: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuIcon(
            icon = Icons.Outlined.Compress,
            label = "Compact context",
            enabled = hasSelection,
            onClick = onCompact,
        )
        MenuIcon(
            icon = Icons.Outlined.ForkRight,
            label = "Fork chat",
            enabled = hasSelection && !running,
            onClick = onFork,
        )
        MenuIcon(
            icon = Icons.Outlined.Refresh,
            label = "Retry last reply",
            enabled = hasSelection && !running,
            onClick = onRetry,
        )
        MenuIcon(
            icon = Icons.Outlined.VisibilityOff,
            label = "Hide chat",
            enabled = hasSelection,
            onClick = onHide,
        )
        MenuIcon(
            icon = Icons.Outlined.DeleteOutline,
            label = "Delete chat",
            enabled = hasSelection,
            danger = true,
            onClick = onDelete,
        )
    }
}

@Composable
private fun MenuIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(42.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                danger -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
}
