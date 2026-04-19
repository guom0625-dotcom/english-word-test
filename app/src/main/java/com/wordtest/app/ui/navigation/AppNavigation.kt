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
    object Test : Screen("test/{sessionId}/{silent}/{autoMic}/{ordered}/{mcOnly}/{reverseMode}") {
        fun createRoute(sessionId: Long, silent: Boolean, autoMic: Boolean = false, ordered: Boolean = false, mcOnly: Boolean = false, reverseMode: Boolean = false) =
            "test/$sessionId/$silent/$autoMic/$ordered/$mcOnly/$reverseMode"
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
                updateChecker = app.updateChecker,
                onNewSession = { navController.navigate(Screen.Import.route) },
                onStartTest = { sessionId, silent, autoMic, ordered, mcOnly, reverseMode ->
                    navController.navigate(Screen.Test.createRoute(sessionId, silent, autoMic, ordered, mcOnly, reverseMode))
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
                onStartTest = { silent, autoMic, ordered, mcOnly, reverseMode ->
                    navController.navigate(Screen.Test.createRoute(sessionId, silent, autoMic, ordered, mcOnly, reverseMode)) {
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
                navArgument("silent") { type = NavType.BoolType },
                navArgument("autoMic") { type = NavType.BoolType },
                navArgument("ordered") { type = NavType.BoolType },
                navArgument("mcOnly") { type = NavType.BoolType },
                navArgument("reverseMode") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            val silent = backStackEntry.arguments?.getBoolean("silent") ?: false
            val autoMic = backStackEntry.arguments?.getBoolean("autoMic") ?: false
            val ordered = backStackEntry.arguments?.getBoolean("ordered") ?: false
            val mcOnly = backStackEntry.arguments?.getBoolean("mcOnly") ?: false
            val reverseMode = backStackEntry.arguments?.getBoolean("reverseMode") ?: false
            TestScreen(
                sessionId = sessionId,
                silentMode = silent,
                initialAutoMic = autoMic,
                ordered = ordered,
                multipleChoiceOnly = mcOnly,
                reverseMode = reverseMode,
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
                onRetry = { silent, autoMic, ordered, mcOnly, reverseMode ->
                    navController.navigate(Screen.Test.createRoute(sessionId, silent, autoMic, ordered, mcOnly, reverseMode)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }
    }
}
