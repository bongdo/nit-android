package space.bongdo.nit

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import space.bongdo.nit.ui.PreviewKind
import space.bongdo.nit.ui.PreviewScreen
import java.io.File

class PreviewActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_PATH = "path"
        const val EXTRA_KIND = "kind"
        const val EXTRA_MIME = "mime"
        const val EXTRA_THEME = "theme"

        fun open(
            context: Context,
            title: String,
            kind: String,
            file: File,
            mime: String? = null,
            theme: String = "system",
        ) {
            val intent = Intent(context, PreviewActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_MIME, mime)
                .putExtra(EXTRA_THEME, theme)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "File" }
        setTaskDescription(ActivityManager.TaskDescription(title))
        val file = File(intent.getStringExtra(EXTRA_PATH).orEmpty())
        val kind = intent.getStringExtra(EXTRA_KIND)?.takeIf { it.isNotBlank() } ?: PreviewKind.FILE
        val mime = intent.getStringExtra(EXTRA_MIME)
        val theme = intent.getStringExtra(EXTRA_THEME)?.takeIf { it.isNotBlank() } ?: "system"
        setContent {
            PreviewScreen(title, kind, file, mime, { finish() }, theme)
        }
    }
}
