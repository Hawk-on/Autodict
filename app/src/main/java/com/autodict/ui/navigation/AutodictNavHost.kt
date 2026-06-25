package com.autodict.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autodict.ui.detail.EntryDetailScreen
import com.autodict.ui.list.EntryListScreen
import com.autodict.ui.record.RecordScreen
import com.autodict.ui.settings.SettingsScreen

/** Navigasjonsrutene i appen. Held som enkle string-konstantar inntil vidare. */
object Routes {
    const val RECORD = "record"
    const val LIST = "list"
    const val DETAIL = "detail/{entryId}"
    const val SETTINGS = "settings"

    fun detail(entryId: String) = "detail/$entryId"
}

@Composable
fun AutodictNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.RECORD) {
        composable(Routes.RECORD) {
            RecordScreen(
                onOpenList = { navController.navigate(Routes.LIST) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.LIST) {
            EntryListScreen(
                onOpenEntry = { entryId -> navController.navigate(Routes.detail(entryId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DETAIL) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId").orEmpty()
            EntryDetailScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
