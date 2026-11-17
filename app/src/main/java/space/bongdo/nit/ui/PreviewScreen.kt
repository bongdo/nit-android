package space.bongdo.nit.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun PreviewScreen(
    title: String,
    kind: String,
    file: File,
    mime: String?,
    onClose: () -> Unit,
    theme: String,
) {
    NitTheme(theme) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                PreviewTopBar(title, onClose)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        !file.exists() -> PreviewMessage("Файл недоступен")
                        kind == PreviewKind.HTML -> HtmlContent(file, themeIsDark(theme, androidx.compose.foundation.isSystemInDarkTheme()))
                        kind == PreviewKind.MARKDOWN -> MarkdownContent(file)
                        kind == PreviewKind.TEXT -> TextContent(file)
                        kind == PreviewKind.IMAGE -> ImageContent(file, title)
                        kind == PreviewKind.PDF -> PdfContent(file)
                        kind == PreviewKind.AUDIO -> AudioContent(file)
                        kind == PreviewKind.VIDEO -> VideoContent(file)
                        else -> FileContent(file, mime)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTopBar(title: String, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
            }
        }
    }
}

@Composable
private fun PreviewMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PreviewLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun rememberFileText(file: File): Result<String>? {
    val state = produceState<Result<String>?>(initialValue = null, file) {
        val result = withContext(Dispatchers.IO) { runCatching { file.readText() } }
        value = result
    }
    return state.value
}

@Composable
private fun HtmlContent(file: File, dark: Boolean) {
    val text = rememberFileText(file)
    val html = text?.getOrNull()
    when {
        text == null -> PreviewLoading()
        html == null -> PreviewMessage("Не удалось открыть HTML")
        else -> HtmlView(html, Modifier.fillMaxSize(), dark = dark)
    }
}

@Composable
private fun MarkdownContent(file: File) {
    val text = rememberFileText(file)
    val markdown = text?.getOrNull()
    when {
        text == null -> PreviewLoading()
        markdown == null -> PreviewMessage("Не удалось прочитать файл")
        else -> SelectionContainer(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            MarkdownText(markdown, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun TextContent(file: File) {
    val text = rememberFileText(file)
    val body = text?.getOrNull()
    when {
        text == null -> PreviewLoading()
        body == null -> PreviewMessage("Не удалось прочитать файл")
        else -> Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SelectionContainer {
                HighlightedCode(body)
            }
        }
    }
}

@Composable
private fun HighlightedCode(text: String) {
    val palette = LocalSyntaxPalette.current
    val annotated = remember(text, palette) {
        buildAnnotatedString {
            var cursor = 0
            for (span in tokenizeSyntax(text)) {
                if (span.start > cursor) append(text.substring(cursor, span.start))
                withStyle(SpanStyle(color = palette.colorFor(span.kind))) {
                    append(text.substring(span.start, span.end))
                }
                cursor = span.end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
    Text(text = annotated, style = CodeStyle, softWrap = false)
}

@Composable
private fun ImageContent(file: File, title: String) {
    var failed by remember(file) { mutableStateOf(false) }
    if (failed) {
        PreviewMessage("Не удалось открыть изображение")
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            AsyncImage(
                model = file,
                contentDescription = title,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                onError = { failed = true },
            )
        }
    }
}

private class PdfDoc(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    val count: Int get() = renderer.pageCount

    fun render(index: Int): ImageBitmap = synchronized(this) {
        val page = renderer.openPage(index)
        try {
            val scale = (1080f / page.width).coerceIn(0.5f, 3f)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.asImageBitmap()
        } finally {
            page.close()
        }
    }

    fun close() {
        synchronized(this) {
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }
}

private fun openPdf(file: File): Result<PdfDoc> = runCatching {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    try {
        PdfDoc(descriptor, PdfRenderer(descriptor))
    } catch (error: Throwable) {
        runCatching { descriptor.close() }
        throw error
    }
}

@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun PdfContent(file: File) {
    val state = produceState<Result<PdfDoc>?>(initialValue = null, file) {
        val result = withContext(Dispatchers.IO) { openPdf(file) }
        value = result
    }
    val result = state.value
    when {
        result == null -> PreviewLoading()
        result.isFailure -> PreviewMessage(
            if (result.exceptionOrNull() is SecurityException) {
                "PDF защищён паролем"
            } else {
                "Не удалось открыть PDF"
            },
        )
        else -> {
            val pdf = result.getOrThrow()
            DisposableEffect(pdf) { onDispose { pdf.close() } }
            LazyColumn(Modifier.fillMaxSize()) {
                items(pdf.count) { index -> PdfPage(pdf, index) }
            }
        }
    }
}

@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun PdfPage(pdf: PdfDoc, index: Int) {
    val state = produceState<ImageBitmap?>(initialValue = null, pdf, index) {
        val result = withContext(Dispatchers.IO) { runCatching { pdf.render(index) }.getOrNull() }
        value = result
    }
    val bitmap = state.value
    if (bitmap == null) {
        Box(Modifier.fillMaxWidth().height(260.dp))
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = "Страница ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AudioContent(file: File) {
    val player = remember(file) { MediaPlayer() }
    var ready by remember(file) { mutableStateOf(false) }
    var playing by remember(file) { mutableStateOf(false) }
    var duration by remember(file) { mutableIntStateOf(0) }
    var position by remember(file) { mutableIntStateOf(0) }
    var error by remember(file) { mutableStateOf<String?>(null) }

    DisposableEffect(file) {
        runCatching {
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { prepared ->
                duration = prepared.duration.coerceAtLeast(0)
                ready = true
                prepared.start()
                playing = true
            }
            player.setOnCompletionListener {
                playing = false
                position = duration
            }
            player.setOnErrorListener { _, _, _ ->
                playing = false
                error = "Не удалось воспроизвести аудио"
                true
            }
            player.prepareAsync()
        }.onFailure {
            error = "Не удалось открыть аудио"
        }
        onDispose {
            playing = false
            runCatching { player.release() }
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player.currentPosition }.getOrDefault(position)
            delay(250)
        }
    }

    val message = error
    if (message != null) {
        PreviewMessage(message)
        return
    }

    val range = duration.coerceAtLeast(1).toFloat()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FilledIconButton(
            onClick = {
                if (!ready) return@FilledIconButton
                if (playing) {
                    runCatching { player.pause() }
                    playing = false
                    position = runCatching { player.currentPosition }.getOrDefault(position)
                } else {
                    runCatching { player.start() }
                    playing = true
                }
            },
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (playing) "Пауза" else "Воспроизвести",
                modifier = Modifier.size(30.dp),
            )
        }
        Slider(
            value = position.toFloat().coerceIn(0f, range),
            onValueChange = {
                position = it.toInt()
                runCatching { player.seekTo(position) }
            },
            valueRange = 0f..range,
            enabled = ready,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = durationLabel(position),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = durationLabel(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VideoContent(file: File) {
    var error by remember(file) { mutableStateOf<String?>(null) }
    val message = error
    if (message != null) {
        PreviewMessage(message)
    } else {
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            factory = { context ->
                VideoView(context).apply {
                    setOnErrorListener { _, _, _ ->
                        error = "Не удалось воспроизвести видео"
                        true
                    }
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    setVideoPath(file.absolutePath)
                    setOnPreparedListener { prepared -> prepared.start() }
                }
            },
        )
    }
}

@Composable
private fun FileContent(file: File, mime: String?) {
    val context = LocalContext.current
    var error by remember(file) { mutableStateOf<String?>(null) }
    val type = mime ?: mimeFor(file.name)
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = byteLabel(file.length()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = type,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            error = null
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, type)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            } catch (notFound: ActivityNotFoundException) {
                error = "Нет приложения для этого типа файла"
            } catch (invalid: IllegalArgumentException) {
                error = "Не удалось открыть файл"
            }
        }) {
            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Открыть системой")
        }
        val message = error
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun durationLabel(ms: Int): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
