package com.istarvin.skinscriptinstaller.ui.navigation

/**
 * Navigation route definitions for the app.
 */
sealed class Screen(val route: String) {
    data object ScriptList : Screen("script_list")
    data object ScriptDetail : Screen("script_detail/{scriptId}?autoClassify={autoClassify}") {
        fun createRoute(scriptId: Long, autoClassify: Boolean = false) =
            "script_detail/$scriptId?autoClassify=$autoClassify"
    }
    data object Settings : Screen("settings")
}

