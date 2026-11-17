package space.bongdo.nit.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.bongdo.nit.data.UserFile

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    directory: String,
    running: Boolean,
    busy: Boolean,
    attachments: List<UserFile>,
    onAttach: (Uri, String?) -> Unit,
    onRemove: (UserFile) -> Unit,
    onSend: (String, (Boolean) -> Unit) -> Unit,
    onStop: () -> Unit,
    onCommand: (String) -> Unit,
) {
    var attachOpen by rememberSaveable { mutableStateOf(false) }
    var commandDismissed by rememberSaveable { mutableStateOf(false) }
    val photo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onAttach(uri, null) }
    val document = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onAttach(uri, null) }
    val file = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) onAttach(uri, null) }

    val canSend = (value.isNotBlank() || attachments.isNotEmpty()) && !busy && !running
    val query = if (value.startsWith("/") && !value.contains("\n")) value.drop(1) else null
    val matches = if (query != null && !query.contains(" ")) {
        SLASH_COMMANDS.filter { it.id.startsWith(query.lowercase(), ignoreCase = true) }
    } else {
        emptyList()
    }
    val paletteOpen = matches.isNotEmpty() && !commandDismissed

    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            if (paletteOpen) {
                SlashPalette(
                    commands = matches,
                    onPick = { command ->
                        onValueChange("")
                        commandDismissed = true
                        onCommand(command.id)
                    },
                )
            }
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(bottom = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attachments.forEach { item ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(start = 7.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                fileIconFor(item.name),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                item.name ?: "файл",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(120.dp),
                            )
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "убрать",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(13.dp)
                                    .clickableNoRipple { onRemove(item) },
                            )
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = { attachOpen = true }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = "прикрепить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(Modifier.weight(1f).padding(vertical = 11.dp, horizontal = 2.dp)) {
                    BasicTextField(
                        value = value,
                        onValueChange = {
                            onValueChange(it)
                            commandDismissed = false
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        keyboardActions = KeyboardActions(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp, max = 150.dp),
                    )
                    if (value.isEmpty()) {
                        Text(
                            if (directory.isBlank()) "Выберите папку в меню…" else "Спроси что угодно… / команды",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
                if (running) {
                    IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Outlined.Stop,
                            contentDescription = "остановить",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        if (!canSend) return@IconButton
                        onSend(value) { _ -> }
                    },
                    enabled = canSend,
                    modifier = Modifier.padding(end = 5.dp, bottom = 5.dp).size(34.dp),
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            contentDescription = "отправить",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    if (attachOpen) {
        AttachSheet(
            onDismiss = { attachOpen = false },
            onPhoto = {
                attachOpen = false
                photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDocument = {
                attachOpen = false
                document.launch(
                    arrayOf(
                        "application/pdf",
                        "text/plain",
                        "text/markdown",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    ),
                )
            },
            onFile = {
                attachOpen = false
                file.launch("*/*")
            },
        )
    }
}
