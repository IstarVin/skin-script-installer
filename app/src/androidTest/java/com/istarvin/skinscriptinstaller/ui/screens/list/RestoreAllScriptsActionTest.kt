package com.istarvin.skinscriptinstaller.ui.screens.list

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import com.istarvin.skinscriptinstaller.ui.components.RestoreAllScriptsAction
import com.istarvin.skinscriptinstaller.ui.theme.SkinScriptInstallerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RestoreAllScriptsActionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restoreAllActionShowsConfirmationAndInvokesCallback() {
        var restoreClicks = 0

        setRestoreAllAction(
            activeUserId = 10,
            restorableCount = 3,
            enabled = true,
            onConfirmRestoreAll = { restoreClicks += 1 }
        )

        composeRule.onNodeWithText("Restore All").assertExists().performClick()
        composeRule.onNodeWithText("Restore All Scripts").assertExists()
        composeRule.onNodeWithText("Restore 3 installed scripts for User 10? This will revert all currently installed script changes for that user.")
            .assertExists()

        composeRule.onNodeWithText("Confirm Restore").performClick()

        assertEquals(1, restoreClicks)
    }

    @Test
    fun restoreAllActionDisablesButtonWhenNoRestorableScriptsExist() {
        setRestoreAllAction(
            activeUserId = 0,
            restorableCount = 0,
            enabled = false
        )

        composeRule.onNodeWithText("Restore all installed scripts for User 0").assertExists()
        composeRule.onNodeWithText("Restore All").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)
        )
    }

    private fun setRestoreAllAction(
        activeUserId: Int,
        restorableCount: Int,
        enabled: Boolean,
        isRestoring: Boolean = false,
        onConfirmRestoreAll: () -> Unit = {}
    ) {
        composeRule.setContent {
            SkinScriptInstallerTheme {
                RestoreAllScriptsAction(
                    activeUserId = activeUserId,
                    restorableCount = restorableCount,
                    enabled = enabled,
                    isRestoring = isRestoring,
                    onConfirmRestoreAll = onConfirmRestoreAll
                )
            }
        }
    }
}
