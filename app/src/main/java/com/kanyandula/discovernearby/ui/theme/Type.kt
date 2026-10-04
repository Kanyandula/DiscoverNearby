package com.kanyandula.discovernearby.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Canvas text roles: headlineLarge = message title, headlineMedium = tile label and screen title,
// headlineSmall = place name, titleLarge = app header, titleMedium = row details and message body,
// bodyLarge = tile subtitle, labelLarge = buttons. The rest of the Material 3 scale is unchanged.
internal val CanvasTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
        bodyLarge = bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
        labelLarge = labelLarge.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    )
}
