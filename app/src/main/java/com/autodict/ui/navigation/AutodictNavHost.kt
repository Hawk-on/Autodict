package com.autodict.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.autodict.ui.detail.EntryDetailScreen
import com.autodict.ui.edit.EntryEditScreen
import com.autodict.ui.home.HomeScreen
import com.autodict.ui.onboarding.OnboardingScreen
import com.autodict.ui.settings.SettingsScreen

/** Navigasjonsrutene i appen. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DETAIL = "detail/{entryId}"
    const val SETTINGS = "settings"
    const val EDIT = "edit?audio={audio}&created={created}&duration={duration}"

    fun detail(entryId: String) = "detail/$entryId"

    fun edit(audioPath: String, createdMillis: Long, durationSeconds: Int) =
        "edit?audio=${Uri.encode(audioPath)}&created=$createdMillis&duration=$durationSeconds"
}

/**
 * @param startWithOnboarding om oppstartsrettleiinga skal visast før heimeskjermen.
 *   Avgjerast av innringaren, som har lese innstillinga ferdig – slik at NavHost ikkje
 *   først byggjer opp heimen og så byter, noko som ville blinka.
 */
@Composable
fun AutodictNavHost(
    navController: NavHostController = rememberNavController(),
    startWithOnboarding: Boolean = false,
) {
    NavHost(
        navController = navController,
        startDestination = if (startWithOnboarding) Routes.ONBOARDING else Routes.HOME,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.HOME) {
                        // Rettleiinga skal ikkje liggje att i backstacken – tilbake frå
                        // heimen går ut av appen, ikkje inn i oppsettet igjen.
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenEntry = { entryId -> navController.navigate(Routes.detail(entryId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onRecorded = { draft ->
                    navController.navigate(
                        Routes.edit(draft.audioPath, draft.createdMillis, draft.durationSeconds),
                    )
                },
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) {
            EntryDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.EDIT,
            arguments = listOf(
                navArgument("audio") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("created") { type = NavType.StringType; defaultValue = "" },
                navArgument("duration") { type = NavType.StringType; defaultValue = "0" },
            ),
        ) {
            EntryEditScreen(
                onBack = { navController.popBackStack() },
                // Lagra oppføring: tilbake til heimen, der ho no ligg øvst i lista.
                onSaved = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onRestartOnboarding = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
    }
}
