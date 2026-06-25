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
import com.autodict.ui.list.EntryListScreen
import com.autodict.ui.record.RecordScreen
import com.autodict.ui.settings.SettingsScreen

/** Navigasjonsrutene i appen. */
object Routes {
    const val RECORD = "record"
    const val LIST = "list"
    const val DETAIL = "detail/{entryId}"
    const val SETTINGS = "settings"
    const val EDIT = "edit?audio={audio}&created={created}&duration={duration}"

    fun detail(entryId: String) = "detail/$entryId"

    fun edit(audioPath: String, createdMillis: Long, durationSeconds: Int) =
        "edit?audio=${Uri.encode(audioPath)}&created=$createdMillis&duration=$durationSeconds"
}

@Composable
fun AutodictNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.RECORD) {
        composable(Routes.RECORD) {
            RecordScreen(
                onOpenList = { navController.navigate(Routes.LIST) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onRecorded = { draft ->
                    navController.navigate(
                        Routes.edit(draft.audioPath, draft.createdMillis, draft.durationSeconds),
                    )
                },
            )
        }

        composable(Routes.LIST) {
            EntryListScreen(
                onOpenEntry = { entryId -> navController.navigate(Routes.detail(entryId)) },
                onBack = { navController.popBackStack() },
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
                onSaved = {
                    navController.navigate(Routes.LIST) {
                        popUpTo(Routes.RECORD)
                    }
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
