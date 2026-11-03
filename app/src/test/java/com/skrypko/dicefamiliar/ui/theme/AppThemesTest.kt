package com.skrypko.dicefamiliar.ui.theme

import androidx.compose.ui.graphics.luminance
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.domain.settings.DarkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemesTest {

    @Test
    fun registryHasUniqueIds_andTheDefaultFirst() {
        val ids = AppThemes.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(AppSettings.DEFAULT_THEME_ID, AppThemes.default.id)
        assertEquals(listOf("classic", "tavern", "cyberpunk"), ids)
    }

    @Test
    fun unknownIdFallsBackToTheDefault() {
        assertEquals(AppThemes.default, AppThemes.byId("removed-theme"))
        assertEquals("tavern", AppThemes.byId("tavern").id)
    }

    @Test
    fun brightnessFollowsThePalettes() {
        val classic = AppThemes.byId("classic")
        assertTrue(classic.followsDarkMode)
        assertTrue(classic.isDark(DarkMode.SYSTEM, systemDark = true))
        assertFalse(classic.isDark(DarkMode.LIGHT, systemDark = true))
        assertTrue(classic.isDark(DarkMode.DARK, systemDark = false))

        val tavern = AppThemes.byId("tavern")
        assertFalse(tavern.followsDarkMode)
        assertFalse(tavern.isDark(DarkMode.DARK, systemDark = true))

        val cyberpunk = AppThemes.byId("cyberpunk")
        assertFalse(cyberpunk.followsDarkMode)
        assertTrue(cyberpunk.isDark(DarkMode.LIGHT, systemDark = false))
    }

    @Test
    fun everyThemeHasReadableTextAndTray() {
        AppThemes.all.forEach { spec ->
            listOf(false, true).forEach { dark ->
                val scheme = spec.scheme(dark)
                assertContrast("${spec.id} onSurface", scheme.onSurface.luminance(), scheme.surface.luminance())
                assertContrast("${spec.id} onPrimary", scheme.onPrimary.luminance(), scheme.primary.luminance())
                assertContrast("${spec.id} dice text", scheme.onPrimaryContainer.luminance(), scheme.primaryContainer.luminance())
            }
        }
    }

    /** WCAG contrast ratio of at least 4.5:1. */
    private fun assertContrast(label: String, a: Float, b: Float) {
        val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        assertTrue("$label contrast $ratio", ratio >= 4.5f)
    }
}
