package com.istarvin.skinscriptinstaller.ui.screens.list

import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptWithStatusTest {

    private val sampleScript = SkinScript(id = 1L, name = "Test", storagePath = "/path")

    @Test
    fun `status returns not_installed when latestInstallation is null`() {
        val sws = ScriptWithStatus(script = sampleScript, latestInstallation = null)
        assertEquals("not_installed", sws.status)
    }

    @Test
    fun `status returns installed when latestInstallation status is installed`() {
        val installation = Installation(id = 1L, scriptId = 1L, status = "installed")
        val sws = ScriptWithStatus(script = sampleScript, latestInstallation = installation)
        assertEquals("installed", sws.status)
    }

    @Test
    fun `status returns restored when latestInstallation status is restored`() {
        val installation = Installation(id = 1L, scriptId = 1L, status = "restored")
        val sws = ScriptWithStatus(script = sampleScript, latestInstallation = installation)
        assertEquals("restored", sws.status)
    }
}
