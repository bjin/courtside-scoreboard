package io.github.bjin.courtside.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Platform sans-serif supplies locale-aware fallback faces; every language shares one sp scale. */
val CourtsideTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    )
}
