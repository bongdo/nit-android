package space.bongdo.nit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

private const val LinkTag = "markdown-link"
private const val FileTag = "markdown-file"

data class MarkdownActions(
    val onOpenHtml: (String, String) -> Unit = { _, _ -> },
    val onOpenArtifact: (String, String) -> Unit = { _, _ -> },
    val onOpenFile: (String, String) -> Unit = { _, _ -> },
    val directory: String? = null,
)

val LocalMarkdownActions = androidx.compose.runtime.staticCompositionLocalOf { MarkdownActions() }

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    onOpenHtml: (String, String) -> Unit = { _, _ -> },
    onOpenArtifact: (String, String) -> Unit = { _, _ -> },
    onOpenFile: (String, String) -> Unit = { _, _ -> },
    directory: String? = null,
) {
    val actions = MarkdownActions(onOpenHtml, onOpenArtifact, onOpenFile, directory)
    val segments = remember(text) { richSegments(text) }
    androidx.compose.runtime.CompositionLocalProvider(LocalMarkdownActions provides actions) {
        Column(modifier) {
            segments.forEach { segment ->
                if (segment.text.isNotBlank()) {
                    val root = remember(segment.text) {
                        MarkdownParser(GFMFlavourDescriptor(), false).buildMarkdownTreeFromString(segment.text)
                    }
                    root.children.forEach { MarkdownBlock(it, segment.text, 0) }
                }
                segment.artifacts.forEach { artifact -> HtmlArtifactCard(artifact) }
            }
        }
    }
}

internal fun splitTableRow(line: String): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '\\' && index + 1 < line.length && line[index + 1] == '|' -> {
                current.append('|')
                index++
            }
            char == '|' -> {
                cells += current.toString().trim()
                current.clear()
            }
            else -> current.append(char)
        }
        index++
    }
    cells += current.toString().trim()
    if (cells.size > 1 && cells.first().isEmpty()) cells.removeAt(0)
    if (cells.size > 1 && cells.last().isEmpty()) cells.removeAt(cells.lastIndex)
    return cells
}

internal fun isHtmlFence(info: String): Boolean =
    info.trim().substringBefore(' ').equals("html", ignoreCase = true)

@Composable
private fun MarkdownBlock(
    node: ASTNode,
    source: String,
    depth: Int,
) {
    when (node.type) {
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 ->
            MarkdownHeading(node, source, MaterialTheme.typography.headlineSmall, 14.dp)
        MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 ->
            MarkdownHeading(node, source, MaterialTheme.typography.titleLarge, 12.dp)
        MarkdownElementTypes.ATX_3 ->
            MarkdownHeading(node, source, MaterialTheme.typography.titleMedium, 10.dp)
        MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 ->
            MarkdownHeading(
                node,
                source,
                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                8.dp,
            )
        MarkdownElementTypes.PARAGRAPH -> Box(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
        ) {
            InlineMarkdown(node, source)
        }
        MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST ->
            MarkdownList(node, source, depth)
        MarkdownElementTypes.BLOCK_QUOTE -> MarkdownQuote(node, source, depth)
        MarkdownTokenTypes.HORIZONTAL_RULE -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        MarkdownElementTypes.CODE_FENCE -> {
            val code = fencedCode(node, source)
            val language = fenceLanguage(node, source)
            if (isHtmlFence(language) || (language.isBlank() && isPureHtml(code))) HtmlFenceCard(code, language) else CodeBlock(code, language)
        }
        MarkdownElementTypes.CODE_BLOCK -> CodeBlock(indentedCode(node, source), null)
        GFMElementTypes.TABLE -> MarkdownTable(node, source)
        MarkdownElementTypes.HTML_BLOCK -> {
            val html = node.getTextInNode(source).toString().trim()
            if (looksLikeHtml(html)) HtmlArtifactCard(HtmlArtifact(source = html)) else Text(
                text = html,
                style = CodeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        MarkdownElementTypes.LINK_DEFINITION -> Unit
        else -> node.children.forEach { MarkdownBlock(it, source, depth) }
    }
}

@Composable
private fun MarkdownHeading(node: ASTNode, source: String, style: TextStyle, top: Dp) {
    Box(Modifier.fillMaxWidth().padding(top = top, bottom = 3.dp)) {
        InlineMarkdown(node, source, style)
    }
}

@Composable
private fun MarkdownList(
    node: ASTNode,
    source: String,
    depth: Int,
) {
    val ordered = node.type == MarkdownElementTypes.ORDERED_LIST
    node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
        .forEachIndexed { index, item ->
            val number = item.children.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
                ?.getTextInNode(source)?.toString()?.trim()?.trimEnd('.')
            val check = item.children.firstOrNull { it.type == GFMTokenTypes.CHECK_BOX }
                ?.getTextInNode(source)?.toString()
            val marker = when {
                check != null -> if (check.contains('x', ignoreCase = true)) "☑" else "☐"
                ordered -> "${number ?: (index + 1)}."
                else -> "•"
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = (depth * 14).dp, top = 2.dp, bottom = 2.dp),
            ) {
                Text(
                    text = marker,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(if (ordered) 32.dp else 20.dp),
                )
                Column(Modifier.weight(1f)) {
                    item.children.forEach { child ->
                        when (child.type) {
                            MarkdownTokenTypes.LIST_BULLET,
                            MarkdownTokenTypes.LIST_NUMBER,
                            GFMTokenTypes.CHECK_BOX,
                            MarkdownTokenTypes.EOL,
                            MarkdownTokenTypes.WHITE_SPACE -> Unit
                            MarkdownElementTypes.UNORDERED_LIST,
                            MarkdownElementTypes.ORDERED_LIST ->
                                MarkdownList(child, source, (depth + 1).coerceAtMost(3))
                            else -> MarkdownBlock(child, source, depth + 1)
                        }
                    }
                }
            }
        }
}

@Composable
private fun MarkdownQuote(
    node: ASTNode,
    source: String,
    depth: Int,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            node.children.forEach { child ->
                if (child.type != MarkdownTokenTypes.BLOCK_QUOTE) {
                    MarkdownBlock(child, source, depth)
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(node: ASTNode, source: String) {
    val rows = node.children.filter {
        it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW
    }
    if (rows.isEmpty()) return
    val cells = rows.map { row -> row.children.filter { it.type == GFMTokenTypes.CELL } }
    val columns = cells.maxOf { it.size }
    val widths = (0 until columns).map { column ->
        val longest = cells.maxOf { row ->
            row.getOrNull(column)?.getTextInNode(source)?.length ?: 0
        }
        (longest * 7 + 22).coerceIn(64, 240).dp
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            val header = row.type == GFMElementTypes.HEADER
            val cellStyle = if (header) {
                MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.bodySmall
            }
            Row(Modifier.height(IntrinsicSize.Min)) {
                cells[rowIndex].forEachIndexed { column, cell ->
                    if (column > 0) {
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                    Box(
                        Modifier.width(widths.getOrElse(column) { 100.dp })
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        InlineMarkdown(cell, source, cellStyle)
                    }
                }
            }
            if (rowIndex < rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, language: String?) {
    val clipboard = LocalClipboardManager.current
    val palette = LocalSyntaxPalette.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            if (!language.isNullOrBlank()) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    contentDescription = if (copied) "Скопировано" else "Копировать",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            val highlighted = remember(code, palette) {
                buildAnnotatedString {
                    var cursor = 0
                    for (span in tokenizeSyntax(code)) {
                        if (span.start > cursor) append(code.substring(cursor, span.start))
                        withStyle(SpanStyle(color = palette.colorFor(span.kind))) {
                            append(code.substring(span.start, span.end))
                        }
                        cursor = span.end
                    }
                    if (cursor < code.length) append(code.substring(cursor))
                }
            }
            Text(
                text = highlighted,
                style = CodeStyle,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun HtmlFenceCard(content: String, language: String?) {
    val actions = LocalMarkdownActions.current
    HtmlPreviewBlock(
        title = "HTML-превью",
        source = content,
        onOpen = { actions.onOpenHtml(content, language?.ifBlank { null } ?: "HTML") },
    )
}

@Composable
private fun HtmlPreviewBlock(title: String, source: String, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("открыть окном", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        HtmlView(
            html = source,
            modifier = Modifier.fillMaxWidth().height(220.dp),
            dark = LocalIsDark.current,
        )
    }
}

@Composable
private fun HtmlArtifactCard(artifact: HtmlArtifact) {
    val actions = LocalMarkdownActions.current
    val title = artifact.title.ifBlank {
        artifact.path.substringAfterLast('/').ifBlank { "HTML-превью" }
    }
    if (artifact.source.isNotBlank()) {
        HtmlPreviewBlock(title = title, source = artifact.source, onOpen = { actions.onOpenHtml(artifact.source, title) })
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable {
                if (artifact.path.isNotBlank()) actions.onOpenArtifact(artifact.path, title)
                else actions.onOpenHtml(artifact.source, title)
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "HTML-страница · открыть в новом окне",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun InlineMarkdown(
    node: ASTNode,
    source: String,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val accent = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val directory = LocalMarkdownActions.current.directory
    val annotated = remember(node, source, accent, codeBackground, directory) {
        buildAnnotatedString { appendInline(node, source, accent, codeBackground, directory) }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val uriHandler = LocalUriHandler.current
    val actions = LocalMarkdownActions.current
    Text(
        text = annotated,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val result = layout ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(position)
                annotated.getStringAnnotations(FileTag, offset, offset).firstOrNull()?.let { file ->
                    actions.onOpenFile(file.item, file.item.substringAfterLast('/'))
                    return@detectTapGestures
                }
                annotated.getStringAnnotations(LinkTag, offset, offset).firstOrNull()?.let { link ->
                    runCatching { uriHandler.openUri(link.item) }
                }
            }
        },
    )
}

private fun AnnotatedString.Builder.appendInline(
    node: ASTNode,
    source: String,
    accent: Color,
    codeBackground: Color,
    directory: String?,
) {
    when (node.type) {
        MarkdownTokenTypes.TEXT, MarkdownTokenTypes.WHITE_SPACE ->
            append(node.getTextInNode(source))
        MarkdownTokenTypes.EOL -> append(' ')
        MarkdownTokenTypes.HARD_LINE_BREAK -> append('\n')
        MarkdownElementTypes.EMPH -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            node.children.forEach {
                if (it.type != MarkdownTokenTypes.EMPH) appendInline(it, source, accent, codeBackground, directory)
            }
        }
        MarkdownElementTypes.STRONG -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            node.children.forEach {
                if (it.type != MarkdownTokenTypes.EMPH) appendInline(it, source, accent, codeBackground, directory)
            }
        }
        GFMElementTypes.STRIKETHROUGH -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            node.children.forEach {
                if (it.type != GFMTokenTypes.TILDE) appendInline(it, source, accent, codeBackground, directory)
            }
        }
        MarkdownElementTypes.CODE_SPAN -> {
            val raw = node.getTextInNode(source).toString()
            val ticks = raw.takeWhile { it == '`' }.length
            var body = if (ticks in 1..(raw.length / 2)) raw.substring(ticks, raw.length - ticks) else raw
            if (body.length > 2 && body.startsWith(' ') && body.endsWith(' ') && body.isNotBlank()) {
                body = body.substring(1, body.length - 1)
            }
            val target = fileTarget(body)
            if (target != null) {
                val resolved = resolvePath(target.path, directory)
                pushStringAnnotation(FileTag, resolved)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = accent, textDecoration = TextDecoration.Underline)) {
                    append(body)
                }
                pop()
            } else {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                    append(body)
                }
            }
        }
        MarkdownElementTypes.INLINE_LINK -> {
            val destination = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                ?.getTextInNode(source)?.toString()?.trim()?.trim('<', '>')
                ?.let(::openable)
            val rawDestination = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                ?.getTextInNode(source)?.toString()?.trim()?.trim('<', '>').orEmpty()
            if (rawDestination.contains("/api/file?") || rawDestination.contains("/api/view?") || rawDestination.startsWith("file:")) {
                appendFileLink(rawDestination, accent) {
                    appendLinkText(node, source, accent, codeBackground, directory)
                }
            } else {
                appendLink(destination, accent) {
                    appendLinkText(node, source, accent, codeBackground, directory)
                }
            }
        }
        MarkdownElementTypes.AUTOLINK -> {
            val url = node.getTextInNode(source).toString().trim('<', '>').trim()
            val destination = when {
                url.startsWith("mailto:", ignoreCase = true) -> url
                url.contains('@') && !url.contains("://") -> "mailto:$url"
                else -> openable(url)
            }
            appendLink(destination, accent) { append(url) }
        }
        GFMTokenTypes.GFM_AUTOLINK -> {
            val raw = node.getTextInNode(source).toString()
            val url = if (raw.startsWith("www.")) "https://$raw" else raw
            appendLink(openable(url), accent) { append(raw) }
        }
        MarkdownElementTypes.IMAGE -> {
            val alt = node.findChildOfType(MarkdownElementTypes.INLINE_LINK)
                ?.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                ?.getTextInNode(source)?.toString()?.trim()?.trim('[', ']').orEmpty()
            append(if (alt.isBlank()) "[картинка]" else "[картинка: $alt]")
        }
        MarkdownTokenTypes.HTML_TAG -> Unit
        // shortcut: reference-style links stay plain text, add a link-definition map when needed
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK ->
            node.children.forEach { appendInline(it, source, accent, codeBackground, directory) }
        else -> node.children.forEach { appendInline(it, source, accent, codeBackground, directory) }
    }
}

private fun AnnotatedString.Builder.appendLinkText(
    node: ASTNode,
    source: String,
    accent: Color,
    codeBackground: Color,
    directory: String?,
) {
    node.children.filter { it.type == MarkdownElementTypes.LINK_TEXT }
        .forEach { appendInline(it, source, accent, codeBackground, directory) }
}

private fun AnnotatedString.Builder.appendFileLink(destination: String, accent: Color, content: () -> Unit) {
    pushStringAnnotation(FileTag, destination)
    pushStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline))
    content()
    pop()
    pop()
}

private fun AnnotatedString.Builder.appendLink(destination: String?, accent: Color, content: () -> Unit) {
    if (destination == null) {
        content()
        return
    }
    pushStringAnnotation(LinkTag, destination)
    pushStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline))
    content()
    pop()
    pop()
}


private fun openable(url: String?): String? {
    val value = url?.trim().orEmpty()
    return when {
        value.startsWith("http://", ignoreCase = true) -> value
        value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("mailto:", ignoreCase = true) -> value
        else -> null
    }
}

private fun fencedCode(node: ASTNode, source: String): String {
    val first = node.children.firstOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
        ?: return ""
    val end = node.children.firstOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_END }
    return source.substring(first.startOffset, end?.startOffset ?: node.endOffset).trimEnd('\n', '\r')
}

private fun fenceLanguage(node: ASTNode, source: String): String =
    node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
        ?.getTextInNode(source)?.toString()?.trim().orEmpty()

private fun indentedCode(node: ASTNode, source: String): String =
    node.children.filter { it.type == MarkdownTokenTypes.CODE_LINE }
        .joinToString("\n") { it.getTextInNode(source).toString().removePrefix("    ") }
