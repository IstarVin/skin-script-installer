package com.istarvin.skinscriptinstaller.ui.navigation

/**
 * Navigation route definitions for the app.
 */
sealed class Screen(val route: String) {
    data object ScriptList : Screen("script_list")
    data object ScriptDetail : Screen("script_detail/{scriptId}") {
        fun createRoute(scriptId: Long) = "script_detail/$scriptId"
    }
    data object Settings : Screen("settings")
}

