package com.beyondmaps.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val BeyondMapsColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentPurple,
    background = BgDeep,
    surface = BgDeep,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeyondMapsTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        MaterialTheme(
            colorScheme = BeyondMapsColorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
