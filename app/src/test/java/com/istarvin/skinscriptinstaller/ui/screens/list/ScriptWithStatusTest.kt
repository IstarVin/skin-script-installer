package com.istarvin.skinscriptinstaller.ui.screens.list

import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptWithStatusTest {

    private val sampleScript = SkinScript(id = 1L, name = "Test", storagePath = "/path")

    @Test
    fun `status returns not_installed when latestInstallation is null`() {
        val sws = ScriptWithStatus(script = sampleScript, latestInstallation = null)
        assertEquals(InstallationStatus.NOT_INSTALLED, sws.status)
    }

    @Test
    fun `status returns installed when latestInstallation status is installed`() {
        val installation = Installation(id = 1L, scriptId = 1L, status = InstallationStatus.INSTALLED)
        val sws = ScriptWithStatus(script = sampleScript, latestInstallation = installation)
        assertEquals(InstallationStatus.INSTALLED, sws.status)
    }

    @Test
    fun `status returns replaced when latestInstallation status is replaced`() {
        val installation = Installation(id = 1L, scriptId = 1L, status = InstallationStatus.REPLACED)
        val sws = ScriptWithStatus(script = sampleScript, latestInstallation = installation)
        assertEquals(InstallationStatus.REPLACED, sws.status)
    }

    @Test
    fun `status returns restored when latestInstallation status is restored`() {
        val installation = Installation(id = 1L, scriptId = 1L, status = InstallationStatus.RESTORED)
        val sws = ScriptWithStatus(script = sampleScript, latestInstallation = installation)
        assertEquals(InstallationStatus.RESTORED, sws.status)
    }
}
