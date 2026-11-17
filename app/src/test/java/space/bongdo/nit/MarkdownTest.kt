package space.bongdo.nit

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.bongdo.nit.ui.isHtmlFence
import space.bongdo.nit.ui.splitTableRow

class MarkdownTest {

    @Test
    fun `splitTableRow unescapes pipes`() {
        assertEquals(listOf("a", "b | c"), splitTableRow("| a | b \\| c |"))
    }

    @Test
    fun `splitTableRow trims cells and outer pipes`() {
        assertEquals(listOf("1", "2"), splitTableRow("  1 | 2  "))
        assertEquals(listOf("left", "right"), splitTableRow("| left | right |"))
    }

    @Test
    fun `html fence is case insensitive`() {
        assertTrue(isHtmlFence("html"))
        assertTrue(isHtmlFence(" HTML "))
        assertFalse(isHtmlFence("kotlin"))
        assertFalse(isHtmlFence(""))
    }

    @Test
    fun `parser exposes heading and table cells`() {
        val source = "# Title\n\n| a | b |\n| --- | --- |\n| 1 | 2 |\n"
        val root = MarkdownParser(GFMFlavourDescriptor(), false).buildMarkdownTreeFromString(source)
        assertNotNull(find(root, MarkdownElementTypes.ATX_1))
        val header = find(root, GFMElementTypes.HEADER)
        assertNotNull(header)
        assertEquals(2, header!!.children.count { it.type == GFMTokenTypes.CELL })
        val row = find(root, GFMElementTypes.ROW)
        assertNotNull(row)
        assertEquals(2, row!!.children.count { it.type == GFMTokenTypes.CELL })
    }

    private fun find(node: ASTNode, type: IElementType): ASTNode? {
        if (node.type == type) return node
        return node.children.firstNotNullOfOrNull { find(it, type) }
    }
}
