package com.beyondmaps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.beyondmaps.ui.components.AtmosphereBackground
import com.beyondmaps.ui.theme.TextDim

@Composable
fun StubScreen(title: String, navController: NavHostController) {
    Box(modifier = Modifier.fillMaxSize()) {
        AtmosphereBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Back",
                style = MaterialTheme.typography.labelMedium,
                color = TextDim,
                modifier = Modifier.align(Alignment.Start)
                    .padding(top = 8.dp)
                    .noRippleBack { navController.navigateUp() },
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextDim)
            Text(text = "Coming soon", style = MaterialTheme.typography.displayLarge, color = TextDim)
        }
    }
}

private fun Modifier.noRippleBack(onClick: () -> Unit): Modifier {
    return this.then(
        clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        )
    )
}
