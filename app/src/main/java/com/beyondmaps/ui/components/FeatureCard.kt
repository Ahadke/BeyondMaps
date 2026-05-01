package com.beyondmaps.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beyondmaps.ui.theme.TextPrimary
import com.beyondmaps.ui.theme.TextSecondary
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
    val shape = RoundedCornerShape(22.dp)
    val breathingTransition = rememberInfiniteTransition(label = "cardEdgeGlow")
    val edgeGlowAlpha by breathingTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(animation = tween(2600)),
        label = "edgeGlowAlpha",
    )
    val cardBackground = Brush.linearGradient(
        colors = if (isPressed) {
            listOf(
                Color(0x161B2436),
                accentColor.copy(alpha = 0.18f),
                Color(0x120F1726),
            )
        } else {
            listOf(
                Color(0x1418202F),
                accentColor.copy(alpha = 0.11f),
                Color(0x100D1422),
            )
        }
    )

    Box(
        modifier = modifier
            .height(132.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = offsetYPx
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(cardBackground)
            .border(0.7.dp, Color.White.copy(alpha = 0.15f), shape)
            .drawBehind {
                drawRoundRect(
                    color = accentColor.copy(alpha = edgeGlowAlpha),
                    cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx()),
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.2f))
                    .border(0.6.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            ) {
                Image(
                    painter = icon,
                    contentDescription = label,
                    modifier = Modifier
                        .size(15.dp)
                        .padding(0.dp)
                        .align(androidx.compose.ui.Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.height(11.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
