package com.beyondmaps.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.beyondmaps.ui.theme.AccentBlue
import com.beyondmaps.ui.theme.BgAiBubble
import com.beyondmaps.ui.theme.BorderCard

@Composable
fun TypingIndicator() {
    Row {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color(0x4D1E468C), RoundedCornerShape(7.dp))
                .border(0.5.dp, Color(0x333C78DC), RoundedCornerShape(7.dp)),
        )
        Spacer(modifier = Modifier.size(9.dp))
        Box(
            modifier = Modifier
                .background(
                    BgAiBubble,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 13.dp, bottomEnd = 13.dp, bottomStart = 13.dp),
                )
                .border(
                    0.5.dp,
                    BorderCard,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 13.dp, bottomEnd = 13.dp, bottomStart = 13.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { index ->
                    val transition = rememberInfiniteTransition(label = "typing$index")
                    val alpha = transition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = 0.7f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 900,
                                delayMillis = index * 220,
                                easing = LinearEasing,
                            ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "alpha$index",
                    ).value
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .alpha(alpha)
                            .background(AccentBlue, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}
