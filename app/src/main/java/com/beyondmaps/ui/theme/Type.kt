package com.beyondmaps.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.beyondmaps.R

val dmSansFamily = FontFamily(
    Font(R.font.dm_sans, FontWeight.Light),
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium),
)

val dmSerifFamily = FontFamily(
    Font(R.font.dm_serif_display_regular, FontWeight.Normal),
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = dmSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = dmSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = (-0.2).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = dmSansFamily,
        fontWeight = FontWeight.Light,
        fontSize = 13.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = dmSansFamily,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = dmSansFamily,
        fontWeight = FontWeight.Light,
        fontSize = 10.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = dmSansFamily,
        fontWeight = FontWeight.Light,
        fontSize = 11.sp,
        lineHeight = 17.sp,
    ),
)
