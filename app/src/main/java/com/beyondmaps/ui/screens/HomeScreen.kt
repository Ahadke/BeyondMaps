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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.beyondmaps.ui.theme.AccentGold
import com.beyondmaps.ui.theme.AccentGreen
import com.beyondmaps.ui.theme.AccentPurple
import com.beyondmaps.ui.theme.BgCard
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
        HomeFeature("Menu Scan", "Decode any menu, any language", AccentGreen, "menu_scan", R.drawable.ic_menu),
        HomeFeature("Cultural Tips", "Customs and etiquette", AccentGold, "cultural_tips", R.drawable.ic_culture),
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
                .padding(horizontal = 24.dp, vertical = 48.dp),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .border(0.5.dp, BorderSubtle, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Current destination",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGhost,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Tokyo, Japan",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
                        color = TextSecondary,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF25436A),
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            for (i in features.indices step 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    val left = features[i]
                    FeatureCard(
                        label = left.label,
                        description = left.description,
                        accentColor = left.accent,
                        icon = painterResource(left.iconRes),
                        alpha = alphas[i].value,
                        offsetYPx = offsets[i].value,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(left.route) },
                    )
                    if (i + 1 < features.size) {
                        val right = features[i + 1]
                        FeatureCard(
                            label = right.label,
                            description = right.description,
                            accentColor = right.accent,
                            icon = painterResource(right.iconRes),
                            alpha = alphas[i + 1].value,
                            offsetYPx = offsets[i + 1].value,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(right.route) },
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "On-device · No connection required",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                color = TextGhost,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, BorderSubtle.copy(alpha = 0.04f))
                    .padding(top = 14.dp),
            )
        }
    }
}
