package com.beyondmaps.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.beyondmaps.ui.theme.BgDeep
import com.beyondmaps.ui.theme.OrbBlue
import com.beyondmaps.ui.theme.OrbNavy

@Composable
fun AtmosphereBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orbs")
    val orb1X = transition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb1X",
    ).value
    val orb1Y = transition.animateFloat(
        initialValue = 0f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb1Y",
    ).value
    val orb2X = transition.animateFloat(
        initialValue = 0f,
        targetValue = -15f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb2X",
    ).value
    val orb2Y = transition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb2Y",
    ).value
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize().background(BgDeep)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius1 = with(density) { 300.dp.toPx() }
            val radius2 = with(density) { 240.dp.toPx() }
            val radius3 = with(density) { 180.dp.toPx() }
            val orb1Center = Offset(
                x = with(density) { (-80).dp.toPx() + orb1X.dp.toPx() },
                y = with(density) { (-100).dp.toPx() + orb1Y.dp.toPx() },
            )
            val orb2Center = Offset(
                x = size.width + with(density) { 60.dp.toPx() + orb2X.dp.toPx() },
                y = size.height - with(density) { 80.dp.toPx() - orb2Y.dp.toPx() },
            )
            val orb3Center = Offset(
                x = size.width - with(density) { 40.dp.toPx() },
                y = size.height * 0.4f,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OrbBlue.copy(alpha = 0.18f), Color.Transparent),
                    center = orb1Center,
                    radius = radius1,
                ),
                radius = radius1,
                center = orb1Center,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OrbBlue.copy(alpha = 0.10f), Color.Transparent),
                    center = orb2Center,
                    radius = radius2,
                ),
                radius = radius2,
                center = orb2Center,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OrbNavy.copy(alpha = 0.08f), Color.Transparent),
                    center = orb3Center,
                    radius = radius3,
                ),
                radius = radius3,
                center = orb3Center,
            )
        }
    }
}
