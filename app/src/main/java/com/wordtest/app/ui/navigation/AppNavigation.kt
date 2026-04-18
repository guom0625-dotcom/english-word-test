package com.wordtest.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wordtest.app.WordTestApplication
import com.wordtest.app.ui.apikey.ApiKeyScreen
import com.wordtest.app.ui.home.HomeScreen
import com.wordtest.app.ui.importscreen.ImportScreen
import com.wordtest.app.ui.result.ResultScreen
import com.wordtest.app.ui.test.TestScreen
import com.wordtest.app.ui.wordlist.WordListScreen

sealed class Screen(val route: String) {
    object ApiKey : Screen("apikey/{firstLaunch}") {
        fun createRoute(firstLaunch: Boolean) = "apikey/$firstLaunch"
    }
    object Home : Screen("home")
    object Import : Screen("import")
    object WordList : Screen("wordlist/{sessionId}") {
        fun createRoute(sessionId: Long) = "wordlist/$sessionId"
    }
    object Test : Screen("test/{sessionId}/{silent}") {
        fun createRoute(sessionId: Long, silent: Boolean) = "test/$sessionId/$silent"
    }
    object Result : Screen("result/{score}/{total}/{sessionId}") {
        fun createRoute(score: Int, total: Int, sessionId: Long) = "result/$score/$total/$sessionId"
    }
}

@Composable
fun AppNavigation(app: WordTestApplication) {
    val navController = rememberNavController()
    val startDestination = if (app.apiKeyStore.hasApiKey())
        Screen.Home.route
    else
        Screen.ApiKey.createRoute(true)

    NavHost(navController = navController, startDestination = startDestination) {
        composable(
            route = Screen.ApiKey.route,
            arguments = listOf(navArgument("firstLaunch") { type = NavType.BoolType })
        ) { backStackEntry ->
            val firstLaunch = backStackEntry.arguments?.getBoolean("firstLaunch") ?: true
            ApiKeyScreen(
                isFirstLaunch = firstLaunch,
                onSaved = {
                    if (firstLaunch) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.ApiKey.createRoute(true)) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = if (!firstLaunch) ({ navController.popBackStack() }) else null
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                repository = app.repository,
                onNewSession = { navController.navigate(Screen.Import.route) },
                onStartTest = { sessionId, silent ->
                    navController.navigate(Screen.Test.createRoute(sessionId, silent))
                },
                onEditWords = { sessionId -> navController.navigate(Screen.WordList.createRoute(sessionId)) },
                onApiKeySetting = { navController.navigate(Screen.ApiKey.createRoute(false)) }
            )
        }

        composable(Screen.Import.route) {
            ImportScreen(
                geminiService = app.geminiService,
                onWordsExtracted = { sessionId ->
                    navController.navigate(Screen.WordList.createRoute(sessionId)) {
                        popUpTo(Screen.Import.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.WordList.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            WordListScreen(
                sessionId = sessionId,
                repository = app.repository,
                geminiService = app.geminiService,
                onStartTest = { silent ->
                    navController.navigate(Screen.Test.createRoute(sessionId, silent)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Test.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("silent") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            val silent = backStackEntry.arguments?.getBoolean("silent") ?: false
            TestScreen(
                sessionId = sessionId,
                silentMode = silent,
                repository = app.repository,
                onFinished = { score, total ->
                    navController.navigate(Screen.Result.createRoute(score, total, sessionId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(
            route = Screen.Result.route,
            arguments = listOf(
                navArgument("score") { type = NavType.IntType },
                navArgument("total") { type = NavType.IntType },
                navArgument("sessionId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val score = backStackEntry.arguments?.getInt("score") ?: 0
            val total = backStackEntry.arguments?.getInt("total") ?: 0
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
            ResultScreen(
                score = score, total = total, sessionId = sessionId,
                repository = app.repository,
                onHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onRetry = { silent ->
                    navController.navigate(Screen.Test.createRoute(sessionId, silent)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }
    }
}
