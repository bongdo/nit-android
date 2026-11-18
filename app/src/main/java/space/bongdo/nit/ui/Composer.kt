package space.bongdo.nit.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import space.bongdo.nit.data.UserFile

@Composable
fun ComposerBody(
    value: String,
    onValueChange: (String) -> Unit,
    directory: String,
    running: Boolean,
    busy: Boolean,
    attachments: List<UserFile>,
    imageModel: (UserFile) -> Any?,
    onAttach: (Uri, String?) -> Unit,
    onRemove: (UserFile) -> Unit,
    onSend: (String, String?, (Boolean) -> Unit) -> Unit,
    onStop: () -> Unit,
    onCommand: (String) -> Unit,
    focusRequest: Int = 0,
) {
    var attachOpen by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
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

    val canSend = (value.isNotBlank() || attachments.isNotEmpty()) && !busy
    val query = if (value.startsWith("/") && !value.contains("\n")) value.drop(1) else null
    val matches = if (query != null && !query.contains(" ")) {
        SLASH_COMMANDS.filter { it.id.startsWith(query.lowercase(), ignoreCase = true) }
    } else {
        emptyList()
    }
    val paletteOpen = matches.isNotEmpty() && !commandDismissed

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        AnimatedVisibility(
            visible = paletteOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
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
                        val model = imageModel(item)
                        if (model != null) {
                            AsyncImage(
                                model = model,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                            )
                        } else {
                            Icon(
                                fileIconFor(item.name),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            item.name ?: "file",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(120.dp),
                        )
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Remove",
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

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 22.dp, max = 150.dp)
                        .focusRequester(focusRequester),
                )
                if (value.isEmpty()) {
                    Text(
                        if (directory.isBlank()) "Choose a folder in the menu…" else "Ask anything… / commands",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickableNoRipple { attachOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = "Attach file",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (running) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp)),
                    ) {
                        Box(
                            Modifier
                                .size(width = 38.dp, height = 34.dp)
                                .clickableNoRipple(onStop),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Stop,
                                contentDescription = "Stop the run",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 34.dp)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickableNoRipple {
                                    if (canSend) onSend(value, "queue") { _ -> }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.ArrowUpward,
                                contentDescription = "Send after this run",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickableNoRipple {
                                if (canSend) onSend(value, null) { _ -> }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            contentDescription = "Send",
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
