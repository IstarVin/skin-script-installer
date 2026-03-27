package com.istarvin.skinscriptinstaller.ui.screens.settings

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.istarvin.skinscriptinstaller.ui.theme.SkinScriptInstallerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shizukuOffShowsBlockingNoticeAndRefreshOnly() {
        setSettingsContent(
            isShizukuAvailable = false,
            isPermissionGranted = false,
            isServiceBound = false
        )

        composeRule.onNodeWithText("Start Shizuku first").assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ShizukuBlockingNotice).assertExists()
        composeRule.onNodeWithText("Refresh Status").assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ShizukuPrimaryAction).assertDoesNotExist()
        composeRule.onNodeWithText("Grant Shizuku Permission").assertDoesNotExist()
        composeRule.onNodeWithText("Connect File Service").assertDoesNotExist()
    }

    @Test
    fun permissionMissingShowsGrantPermissionAction() {
        setSettingsContent(
            isShizukuAvailable = true,
            isPermissionGranted = false,
            isServiceBound = false
        )

        composeRule.onNodeWithText("Finish permission setup").assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ShizukuBlockingNotice).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.ShizukuPrimaryAction).assertExists().assertHasClickAction()
        composeRule.onNodeWithText("Grant Shizuku Permission").assertExists()
        composeRule.onNodeWithText("Connect File Service").assertDoesNotExist()
    }

    @Test
    fun serviceDisconnectedShowsConnectAction() {
        setSettingsContent(
            isShizukuAvailable = true,
            isPermissionGranted = true,
            isServiceBound = false
        )

        composeRule.onNodeWithText("Connect the file service").assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ShizukuBlockingNotice).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.ShizukuPrimaryAction).assertExists().assertHasClickAction()
        composeRule.onNodeWithText("Connect File Service").assertExists()
        composeRule.onNodeWithText("Grant Shizuku Permission").assertDoesNotExist()
    }

    @Test
    fun readyStateShowsNoPrimaryAction() {
        setSettingsContent(
            isShizukuAvailable = true,
            isPermissionGranted = true,
            isServiceBound = true
        )

        composeRule.onNodeWithText("Shizuku ready").assertExists()
        composeRule.onNodeWithText("Ready").assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ShizukuBlockingNotice).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.ShizukuPrimaryAction).assertDoesNotExist()
        composeRule.onNodeWithText("Refresh Status").assertExists()
    }

    @Test
    fun refreshActionInvokesCallback() {
        var refreshClicks = 0

        setSettingsContent(onRefreshStatus = { refreshClicks += 1 })

        composeRule.onNodeWithText("Refresh Status").performClick()

        assertEquals(1, refreshClicks)
    }

    @Test
    fun maintenanceSectionKeepsOrderAndInlineStatesScoped() {
        setSettingsContent(
            backupMessage = "Backup export completed",
            isRefreshingCatalog = true,
            updateCheckMessage = "App is up to date"
        )

        composeRule.waitForIdle()

        val titleOrder = composeRule.onAllNodes(
            hasText("Backup & Restore") or hasText("Hero Catalog") or hasText("App Updates"),
            useUnmergedTree = true
        ).fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        }

        assertEquals(
            listOf("Backup & Restore", "Hero Catalog", "App Updates"),
            titleOrder
        )

        composeRule.onNode(
            hasText("Backup export completed") and
                hasAnyAncestor(hasTestTag(SettingsTestTags.MaintenanceBackup))
        ).assertExists()
        composeRule.onNode(
            hasText("Restore All") and
                hasAnyAncestor(hasTestTag(SettingsTestTags.MaintenanceRestoreAll))
        ).assertExists()
        composeRule.onNode(
            hasText("Fetching hero catalog...") and
                hasAnyAncestor(hasTestTag(SettingsTestTags.MaintenanceCatalog))
        ).assertExists()
        composeRule.onNode(
            hasText("App is up to date") and
                hasAnyAncestor(hasTestTag(SettingsTestTags.MaintenanceUpdates))
        ).assertExists()
    }

    private fun setSettingsContent(
        isShizukuAvailable: Boolean = true,
        isPermissionGranted: Boolean = true,
        isServiceBound: Boolean = true,
        onRefreshStatus: () -> Unit = {},
        backupMessage: String? = null,
        activeUserId: Int = 0,
        restoreAllCount: Int = 0,
        canRestoreAll: Boolean = false,
        isRestoringAll: Boolean = false,
        restoreAllMessage: String? = null,
        onRestoreAll: () -> Unit = {},
        isRefreshingCatalog: Boolean = false,
        updateCheckMessage: String? = null
    ) {
        composeRule.setContent {
            SkinScriptInstallerTheme {
                SettingsContent(
                    onNavigateBack = {},
                    isShizukuAvailable = isShizukuAvailable,
                    isPermissionGranted = isPermissionGranted,
                    isServiceBound = isServiceBound,
                    onRequestPermission = {},
                    onBindService = {},
                    onRefreshStatus = onRefreshStatus,
                    onExportBackup = {},
                    onRestoreBackup = {},
                    isBackupOperationRunning = false,
                    backupMessage = backupMessage,
                    activeUserId = activeUserId,
                    restoreAllCount = restoreAllCount,
                    canRestoreAll = canRestoreAll,
                    isRestoringAll = isRestoringAll,
                    restoreAllMessage = restoreAllMessage,
                    onRestoreAll = onRestoreAll,
                    onRefreshHeroCatalog = {},
                    isRefreshingCatalog = isRefreshingCatalog,
                    catalogRefreshMessage = null,
                    onCheckForUpdates = {},
                    isCheckingForUpdates = false,
                    updateCheckMessage = updateCheckMessage
                )
            }
        }
    }
}
