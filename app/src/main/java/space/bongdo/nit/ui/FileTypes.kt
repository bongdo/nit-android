package space.bongdo.nit.ui

import android.webkit.MimeTypeMap

object PreviewKind {
    const val HTML = "html"
    const val MARKDOWN = "markdown"
    const val TEXT = "text"
    const val IMAGE = "image"
    const val PDF = "pdf"
    const val AUDIO = "audio"
    const val VIDEO = "video"
    const val FILE = "file"
}

private val textExtensions = setOf(
    "txt", "log", "csv", "tsv", "json", "xml", "yaml", "yml", "ini", "conf", "toml",
    "kt", "kts", "java", "js", "ts", "jsx", "tsx", "py", "rb", "go", "rs", "c", "h",
    "cpp", "hpp", "cs", "php", "sh", "bash", "zsh", "sql", "gradle", "properties",
    "env", "gitignore", "diff", "patch",
)

private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "tiff", "tif")

private val audioExtensions = setOf("mp3", "wav", "ogg", "oga", "m4a", "flac", "aac", "opus", "amr")

private val videoExtensions = setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v", "avi", "mpg", "mpeg")

private fun extension(name: String?): String =
    name?.substringAfterLast('.', "")?.trim()?.lowercase().orEmpty()

fun fileKind(name: String?, mime: String?): String {
    return when (val ext = extension(name)) {
        "html", "htm", "xhtml" -> PreviewKind.HTML
        "md", "markdown", "mdown" -> PreviewKind.MARKDOWN
        in textExtensions -> PreviewKind.TEXT
        in imageExtensions -> PreviewKind.IMAGE
        "pdf" -> PreviewKind.PDF
        in audioExtensions -> PreviewKind.AUDIO
        in videoExtensions -> PreviewKind.VIDEO
        else -> {
            val type = mime?.trim()?.lowercase().orEmpty()
            when {
                ext.isEmpty() && type.isEmpty() -> PreviewKind.FILE
                type.startsWith("text/html") -> PreviewKind.HTML
                type.startsWith("text/markdown") -> PreviewKind.MARKDOWN
                type.startsWith("text/") -> PreviewKind.TEXT
                type.startsWith("image/") -> PreviewKind.IMAGE
                type == "application/pdf" -> PreviewKind.PDF
                type.startsWith("audio/") -> PreviewKind.AUDIO
                type.startsWith("video/") -> PreviewKind.VIDEO
                else -> PreviewKind.FILE
            }
        }
    }
}

fun mimeFor(name: String?): String {
    val ext = extension(name)
    if (ext.isNotEmpty()) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
    }
    return when (fileKind(name, null)) {
        PreviewKind.HTML -> "text/html"
        PreviewKind.MARKDOWN -> "text/markdown"
        PreviewKind.TEXT -> "text/plain"
        PreviewKind.IMAGE -> "image/*"
        PreviewKind.PDF -> "application/pdf"
        PreviewKind.AUDIO -> "audio/*"
        PreviewKind.VIDEO -> "video/*"
        else -> "application/octet-stream"
    }
}
