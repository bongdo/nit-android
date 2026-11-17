package space.bongdo.nit

import org.junit.Assert.assertEquals
import org.junit.Test
import space.bongdo.nit.ui.PreviewKind
import space.bongdo.nit.ui.fileKind

class FileTypesTest {

    @Test
    fun `extensions map to preview kinds`() {
        assertEquals(PreviewKind.HTML, fileKind("page.html", null))
        assertEquals(PreviewKind.MARKDOWN, fileKind("README.md", null))
        assertEquals(PreviewKind.TEXT, fileKind("notes.TXT", null))
        assertEquals(PreviewKind.IMAGE, fileKind("photo.jpeg", null))
        assertEquals(PreviewKind.PDF, fileKind("report.pdf", null))
        assertEquals(PreviewKind.AUDIO, fileKind("voice.mp3", null))
        assertEquals(PreviewKind.VIDEO, fileKind("clip.mp4", null))
    }

    @Test
    fun `mime decides when extension is unknown`() {
        assertEquals(PreviewKind.HTML, fileKind("download", "text/html; charset=utf-8"))
        assertEquals(PreviewKind.MARKDOWN, fileKind("download", "text/markdown"))
        assertEquals(PreviewKind.TEXT, fileKind("download", "text/plain"))
        assertEquals(PreviewKind.IMAGE, fileKind("download", "image/png"))
        assertEquals(PreviewKind.PDF, fileKind("download", "application/pdf"))
        assertEquals(PreviewKind.AUDIO, fileKind("download", "audio/mpeg"))
        assertEquals(PreviewKind.VIDEO, fileKind("download", "video/mp4"))
    }

    @Test
    fun `unknown stays a plain file`() {
        assertEquals(PreviewKind.FILE, fileKind("archive.zip", "application/zip"))
        assertEquals(PreviewKind.FILE, fileKind(null, null))
    }

    @Test
    fun `extension wins over mime`() {
        assertEquals(PreviewKind.MARKDOWN, fileKind("notes.md", "text/plain"))
    }
}
