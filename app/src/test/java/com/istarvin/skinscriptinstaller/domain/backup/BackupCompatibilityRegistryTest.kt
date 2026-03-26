package com.istarvin.skinscriptinstaller.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCompatibilityRegistryTest {

    @Test
    fun `validate returns compatible for supported backup`() {
        val manifest = AppBackupManifest(
            formatVersion = 1,
            exportedAt = System.currentTimeMillis(),
            appVersionCode = 2,
            appVersionName = "1.0.2",
            databaseVersion = 2,
            scripts = emptyList(),
            installations = emptyList(),
            installedFiles = emptyList()
        )

        val result = BackupCompatibilityRegistry.validate(
            manifest = manifest,
            migrationEdges = setOf(1 to 2)
        )

        assertTrue(result.isCompatible)
    }

    @Test
    fun `validate blocks incompatible schema when migration path is unavailable`() {
        val manifest = AppBackupManifest(
            formatVersion = 1,
            exportedAt = System.currentTimeMillis(),
            appVersionCode = 2,
            appVersionName = "1.0.2",
            databaseVersion = 99,
            scripts = emptyList(),
            installations = emptyList(),
            installedFiles = emptyList()
        )

        val result = BackupCompatibilityRegistry.validate(
            manifest = manifest,
            migrationEdges = setOf(1 to 2)
        )

        assertFalse(result.isCompatible)
        assertTrue(result.reason.orEmpty().contains("cannot be migrated", ignoreCase = true))
    }
}
