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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Alignment
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
    val shape = RoundedCornerShape(20.dp)
    val cardBackground = Brush.linearGradient(
        colors = if (isPressed) {
            listOf(
                Color(0x18223855),
                Color(0x14263E67),
                Color(0x102A3E5A),
            )
        } else {
            listOf(
                Color(0x14243A56),
                Color(0x10283E63),
                Color(0x0E253B54),
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
            .border(0.7.dp, Color.White.copy(alpha = 0.16f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp)
    ) {
        // One subtle accent bloom; kept small to avoid nested-card appearance.
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 68.dp)
                .align(Alignment.TopStart)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            accentColor.copy(alpha = if (isPressed) 0.25f else 0.2f),
                            accentColor.copy(alpha = 0.07f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accentColor.copy(alpha = 0.24f))
                    .border(0.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
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
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
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
