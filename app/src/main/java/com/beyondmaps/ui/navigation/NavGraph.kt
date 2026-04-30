package com.beyondmaps.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beyondmaps.ui.screens.ChatScreen
import com.beyondmaps.ui.screens.HomeScreen
import com.beyondmaps.ui.screens.StubScreen

@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { fadeIn(tween(210)) + slideInHorizontally(tween(210)) { it / 10 } },
        exitTransition = { fadeOut(tween(170)) },
        popEnterTransition = { fadeIn(tween(190)) },
        popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 10 } },
    ) {
        composable("home") { HomeScreen(navController) }
        composable("chat") { ChatScreen(navController) }
        composable("menu_scan") { StubScreen(title = "Menu Scan", navController = navController) }
        composable("cultural_tips") { StubScreen(title = "Cultural Tips", navController = navController) }
        composable("phrases") { StubScreen(title = "Phrases", navController = navController) }
    }
}
