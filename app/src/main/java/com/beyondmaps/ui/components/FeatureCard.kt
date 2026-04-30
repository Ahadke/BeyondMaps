package com.beyondmaps.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beyondmaps.ui.theme.BgCard
import com.beyondmaps.ui.theme.BgCardHover
import com.beyondmaps.ui.theme.TextMuted
import com.beyondmaps.ui.theme.TextPrimary
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight

@Composable
fun FeatureCard(
    label: String,
    description: String,
    accentColor: Color,
    icon: Painter,
    alpha: Float,
    offsetYPx: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
        ),
        label = "cardScale",
    )

    Box(
        modifier = modifier
            .height(150.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = offsetYPx
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPressed) BgCardHover else BgCard)
            .border(0.5.dp, com.beyondmaps.ui.theme.BorderCard, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 17.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(accentColor, Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 70.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(accentColor.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accentColor.copy(alpha = 0.15f))
            ) {
                Image(
                    painter = icon,
                    contentDescription = label,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(0.dp)
                        .align(androidx.compose.ui.Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }
    }
}
