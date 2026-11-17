package space.bongdo.nit

import org.junit.Assert.assertTrue
import org.junit.Test
import space.bongdo.nit.ui.THEME_IDS
import space.bongdo.nit.ui.themeIsDark
import space.bongdo.nit.ui.themeSpec

class ThemesTest {
    @Test
    fun everyThemeHasAPaletteAndDarkFlag() {
        for (id in THEME_IDS) {
            val (palette, dark) = themeSpec(id, false)
            assertTrue("$id background", palette.background.alpha > 0f)
            assertTrue("$id primary", palette.primary.alpha > 0f)
            assertTrue("$id dark flag", dark == themeIsDark(id, false) || id == "system")
        }
    }

    @Test
    fun systemFollowsTheSystem() {
        assertTrue(themeIsDark("system", true))
        assertTrue(!themeIsDark("system", false))
    }
}
