package com.beyondmaps.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.beyondmaps.R
import com.beyondmaps.ui.components.AtmosphereBackground
import com.beyondmaps.ui.components.FeatureCard
import com.beyondmaps.ui.theme.AccentBlue
import com.beyondmaps.ui.theme.AccentPurple
import com.beyondmaps.ui.theme.BorderSubtle
import com.beyondmaps.ui.theme.TextDim
import com.beyondmaps.ui.theme.TextGhost
import com.beyondmaps.ui.theme.TextPrimary
import com.beyondmaps.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class HomeFeature(
    val label: String,
    val description: String,
    val accent: Color,
    val route: String,
    val iconRes: Int,
)

@Composable
fun HomeScreen(navController: NavHostController) {
    val features = listOf(
        HomeFeature("Travel Guide", "Ask anything, get local answers", AccentBlue, "chat", R.drawable.ic_guide),
        HomeFeature("Phrases", "Say the right thing", AccentPurple, "phrases", R.drawable.ic_phrases),
    )
    val alphas = remember { List(features.size) { Animatable(0f) } }
    val offsets = remember { List(features.size) { Animatable(14f) } }

    LaunchedEffect(Unit) {
        features.indices.forEach { index ->
            launch {
                delay(index * 65L)
                alphas[index].animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            }
            launch {
                delay(index * 65L)
                offsets[index].animateTo(0f, tween(360, easing = FastOutSlowInEasing))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AtmosphereBackground()
        Column(
            modifier = Modifier
                .zIndex(1f)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextPrimary)) { append("Beyond") }
                    append(" ")
                    withStyle(SpanStyle(color = AccentBlue)) { append("Maps") }
                },
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Navigate anywhere. Even without signal.",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                color = TextDim,
            )

            Spacer(modifier = Modifier.height(20.dp))
            DestinationStatusCard(
                destination = "Tokyo, Japan",
                status = "Ready",
            )

            Spacer(modifier = Modifier.height(22.dp))
            features.forEachIndexed { index, feature ->
                FeatureCard(
                    label = feature.label,
                    description = feature.description,
                    accentColor = feature.accent,
                    icon = painterResource(feature.iconRes),
                    alpha = alphas[index].value,
                    offsetYPx = offsets[index].value,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate(feature.route) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(18.dp))
            OfflineTrustBadge(
                text = "On-device AI \u00b7 No connection required",
            )
        }
    }
}

@Composable
private fun DestinationStatusCard(
    destination: String,
    status: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0x141D2A44), Color(0x0F223557), Color(0x121B2B45))
                ),
                shape = RoundedCornerShape(22.dp),
            )
            .border(0.6.dp, Color(0x24FFFFFF), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Current destination",
                style = MaterialTheme.typography.labelSmall,
                color = TextGhost,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = destination,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = TextPrimary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .background(Color(0x1E1B5A48), RoundedCornerShape(999.dp))
                .border(0.5.dp, Color(0x3352D3A9), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(Color(0xFF6EF2C5), CircleShape),
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = Color(0xFF7EE8C8),
            )
        }
    }
}

@Composable
private fun OfflineTrustBadge(text: String) {
    Row(
        modifier = Modifier
            .background(Color(0x121A3559), RoundedCornerShape(999.dp))
            .border(0.5.dp, BorderSubtle.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(Color(0xFF5ED5F6), CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
            color = TextSecondary,
        )
    }
}
