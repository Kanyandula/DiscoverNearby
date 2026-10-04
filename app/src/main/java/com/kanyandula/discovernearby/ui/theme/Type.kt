package com.kanyandula.discovernearby.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Canvas text roles (Discover artboard): headlineMedium = tile label and screen title, titleLarge = app
// header, bodyLarge = supporting text. The rest of the Material 3 scale is unchanged.
internal val CanvasTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
    )
}
