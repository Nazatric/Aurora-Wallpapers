package com.auroro.wallpapers.core.design

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.auroro.wallpapers.core.data.FontChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeTest {
    @Test fun fontPreferenceUsesBundledOpenSansOrSystemAndAppliesReadableScale() {
        val compact = appTypography(FontChoice.OPEN_SANS, 0.85f)
        val enlarged = appTypography(FontChoice.OPEN_SANS, 1.30f)
        val system = appTypography(FontChoice.SYSTEM, 1f)

        assertEquals(AeroFont, compact.bodyMedium.fontFamily)
        assertEquals(AeroFont, enlarged.titleLarge.fontFamily)
        assertEquals(FontFamily.Default, system.bodyMedium.fontFamily)
        assertEquals(14.sp * 0.85f, compact.bodyMedium.fontSize)
        assertEquals(14.sp * 1.30f, enlarged.bodyMedium.fontSize)
        assertEquals(14.sp, system.bodyMedium.fontSize)
    }
}
