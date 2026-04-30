package com.beyondmaps.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.beyondmaps.ui.theme.AccentBlue
import com.beyondmaps.ui.theme.BgCard
import com.beyondmaps.ui.theme.BorderCard
import com.beyondmaps.ui.theme.BorderSubtle
import com.beyondmaps.ui.theme.TextGhost
import com.beyondmaps.ui.theme.TextSecondary

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, BorderSubtle.copy(alpha = 0.04f))
            .padding(top = 10.dp, start = 14.dp, end = 14.dp, bottom = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(BgCard, RoundedCornerShape(22.dp))
                    .border(0.5.dp, BorderCard, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (value.isBlank()) {
                            Text(
                                text = "Ask anything about Tokyo...",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextGhost,
                            )
                        }
                        inner()
                    }
                )
            }

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.86f else 1f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                ),
                label = "sendScale",
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(androidx.compose.ui.graphics.Color(0x991A3C80), CircleShape)
                    .border(0.5.dp, androidx.compose.ui.graphics.Color(0x404682FF), CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 10.dp, height = 2.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF6AA0F0), RoundedCornerShape(1.dp))
                )
                Spacer(modifier = Modifier.height(0.dp))
            }
        }
    }
}
