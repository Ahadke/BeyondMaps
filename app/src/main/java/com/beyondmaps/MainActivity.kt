package com.beyondmaps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeyondMapsApp()
        }
    }
}

private enum class AppScreen {
    Main,
    Home,
    Feature,
    MenuScan,
}

private data class Feature(
    val icon: String,
    val title: String,
    val description: String,
    val placeholder: String,
)

private val beyondMapsLightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = Color(0xFF0F766E),
    surface = Color.White,
    background = Color.White,
)

/**
 * Material3 default [Typography] uses downloadable / @font resources; on some devices that throws
 * [Resources.NotFoundException]. Use platform sans-serif only and drop Android platform font hooks.
 */
private fun safeTypography(): Typography {
    val d = Typography()
    fun TextStyle.df() = copy(
        fontFamily = FontFamily.SansSerif,
        platformStyle = null,
    )
    return Typography(
        displayLarge = d.displayLarge.df(),
        displayMedium = d.displayMedium.df(),
        displaySmall = d.displaySmall.df(),
        headlineLarge = d.headlineLarge.df(),
        headlineMedium = d.headlineMedium.df(),
        headlineSmall = d.headlineSmall.df(),
        titleLarge = d.titleLarge.df(),
        titleMedium = d.titleMedium.df(),
        titleSmall = d.titleSmall.df(),
        bodyLarge = d.bodyLarge.df(),
        bodyMedium = d.bodyMedium.df(),
        bodySmall = d.bodySmall.df(),
        labelLarge = d.labelLarge.df(),
        labelMedium = d.labelMedium.df(),
        labelSmall = d.labelSmall.df(),
    )
}

private val features = listOf(
    Feature(
        icon = "💬",
        title = "Offline Chat",
        description = "Ask about Tokyo without internet.",
        placeholder = "Ask travel questions and get answers from the local destination pack.",
    ),
    Feature(
        icon = "🍜",
        title = "Menu Scanner",
        description = "Understand menus on the go.",
        placeholder = "Scan menus offline and understand dishes, allergens, and recommendations.",
    ),
    Feature(
        icon = "🎎",
        title = "Culture Tips",
        description = "Navigate customs with confidence.",
        placeholder = "Learn local customs, etiquette, and travel tips.",
    ),
    Feature(
        icon = "🗣️",
        title = "Phrase Help",
        description = "Find useful local phrases.",
        placeholder = "Find useful local phrases with pronunciation and cultural notes.",
    ),
)

@Composable
private fun BeyondMapsApp() {
    var screen by remember { mutableStateOf(AppScreen.Main) }
    var selectedFeature by remember { mutableStateOf(features.first()) }

    MaterialTheme(
        colorScheme = beyondMapsLightColorScheme,
        typography = safeTypography(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            when (screen) {
                AppScreen.Main -> MainScreen(onStartTrip = { screen = AppScreen.Home })
                AppScreen.Home -> HomeScreen(
                    onFeatureClick = {
                        selectedFeature = it
                        screen = AppScreen.Feature
                    },
                    onMenuScanClick = { screen = AppScreen.MenuScan },
                )
                AppScreen.Feature -> FeatureScreen(
                    feature = selectedFeature,
                    onBack = { screen = AppScreen.Home },
                )
                AppScreen.MenuScan -> MenuScanScreen(
                    onBack = { screen = AppScreen.Home },
                )
            }
        }
    }
}

@Composable
private fun MainScreen(onStartTrip: () -> Unit) {
    ScreenContainer {
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "BeyondMaps",
            color = Color(0xFF111827),
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Offline AI travel companion",
            color = Color(0xFF4B5563),
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Badge(text = "Offline-first • On-device AI")
        Spacer(modifier = Modifier.height(40.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Tokyo", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Downloaded destination pack",
                    color = Color(0xFF64748B),
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onStartTrip,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "Start Trip",
                        modifier = Modifier.padding(vertical = 6.dp),
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onFeatureClick: (Feature) -> Unit, onMenuScanClick: () -> Unit) {
    ScreenContainer {
        Text(
            text = "Tokyo Offline Guide",
            color = Color(0xFF111827),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Badge(text = "Offline Ready")
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onMenuScanClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = "Menu Scan",
                modifier = Modifier.padding(vertical = 6.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureCard(
                feature = features[0],
                modifier = Modifier.weight(1f),
                onClick = { onFeatureClick(features[0]) },
            )
            FeatureCard(
                feature = features[1],
                modifier = Modifier.weight(1f),
                onClick = { onFeatureClick(features[1]) },
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureCard(
                feature = features[2],
                modifier = Modifier.weight(1f),
                onClick = { onFeatureClick(features[2]) },
            )
            FeatureCard(
                feature = features[3],
                modifier = Modifier.weight(1f),
                onClick = { onFeatureClick(features[3]) },
            )
        }
    }
}

@Composable
private fun FeatureScreen(feature: Feature, onBack: () -> Unit) {
    ScreenContainer {
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(text = "Back")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = feature.title,
            color = Color(0xFF111827),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = feature.icon, fontSize = 42.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = feature.placeholder,
                    color = Color(0xFF334155),
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    feature: Feature,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(176.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = feature.icon, fontSize = 34.sp)
            Column {
                Text(
                    text = feature.title,
                    color = Color(0xFF111827),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = feature.description,
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFFEFF6FF))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F766E)),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = Color(0xFF1E40AF),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ScreenContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun BeyondMapsPreview() {
    BeyondMapsApp()
}

