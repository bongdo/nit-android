package space.bongdo.nit.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp

@Composable
fun HtmlPreviewDialog(html: String, title: String = "HTML-превью", onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "закрыть")
                    }
                }
                HtmlView(html, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlView(
    html: String,
    modifier: Modifier = Modifier,
    dark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = null
                }
                tag = html.hashCode().toString() + dark
                loadDataWithBaseURL(
                    null,
                    wrapHtml(html, dark),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            },
        update = { view ->
            val key = html.hashCode().toString() + dark
            if (view.tag != key) {
                view.tag = key
                view.loadDataWithBaseURL(null, wrapHtml(html, dark), "text/html", "utf-8", null)
            }
        },
    )
}

private fun wrapHtml(html: String, dark: Boolean): String {
    val background = if (dark) "#16171b" else "#f7f6f3"
    val foreground = if (dark) "#e9e7e2" else "#1b1c1f"
    val border = if (dark) "#343843" else "#cfcac1"
    if (html.contains("<html", ignoreCase = true)) {
        val style = """<style>:root { color-scheme: ${if (dark) "dark" else "light"}; } html, body { background: $background; color: $foreground; }</style>"""
        val head = Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html)
        return if (head != null) html.substring(0, head.range.last + 1) + style + html.substring(head.range.last + 1) else style + html
    }
    return """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          :root { color-scheme: ${if (dark) "dark" else "light"}; }
          html, body { margin: 0; padding: 12px; background: $background; color: $foreground;
            font: 15px/1.5 -apple-system, system-ui, sans-serif; }
          img, svg, canvas { max-width: 100%; height: auto; }
          pre, code { white-space: pre-wrap; word-break: break-word; }
          table { border-collapse: collapse; } th, td { border: 1px solid $border; padding: 6px 9px; }
          a { color: #e3a15c; }
        </style></head><body>$html</body></html>
    """.trimIndent()
}
