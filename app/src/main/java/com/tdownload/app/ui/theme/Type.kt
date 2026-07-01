package com.tdownload.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = ColorTextPrimary,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = ColorTextPrimary,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        color = ColorTextSecondary,
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        color = ColorTextTertiary,
    ),
)
