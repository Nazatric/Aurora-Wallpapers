package com.auroro.wallpapers.core.design

import com.auroro.wallpapers.core.data.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test fun followSystemUsesTheCurrentSystemValueAndExplicitModesOverrideIt() {
        assertFalse(resolveDarkMode(ThemeMode.SYSTEM, systemIsDark = false))
        assertTrue(resolveDarkMode(ThemeMode.SYSTEM, systemIsDark = true))
        assertFalse(resolveDarkMode(ThemeMode.LIGHT, systemIsDark = true))
        assertTrue(resolveDarkMode(ThemeMode.DARK, systemIsDark = false))
    }
}
