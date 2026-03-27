package com.istarvin.skinscriptinstaller.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BackupJsonCodecTest {

    @Test
    fun `encode and decode preserves hero icon`() {
        val manifest = AppBackupManifest(
            formatVersion = 2,
            exportedAt = 1_700_000_000_000,
            appVersionCode = 10,
            appVersionName = "1.0.10",
            databaseVersion = 4,
            scripts = emptyList(),
            installations = emptyList(),
            installedFiles = emptyList(),
            heroes = listOf(
                HeroBackupRecord(id = 1L, name = "Miya", heroIcon = "https://cdn.example/miya.webp"),
                HeroBackupRecord(id = 2L, name = "Layla", heroIcon = null)
            ),
            skins = emptyList()
        )

        val decoded = BackupJsonCodec.decode(BackupJsonCodec.encode(manifest))

        assertEquals("https://cdn.example/miya.webp", decoded.heroes.first { it.id == 1L }.heroIcon)
        assertNull(decoded.heroes.first { it.id == 2L }.heroIcon)
    }

    @Test
    fun `decode defaults hero icon to null for old backups`() {
        val rawJson =
            """
            {
              "formatVersion": 2,
              "exportedAt": 1700000000000,
              "appVersionCode": 10,
              "appVersionName": "1.0.10",
              "databaseVersion": 4,
              "heroes": [
                {
                  "id": 1,
                  "name": "Miya"
                }
              ],
              "skins": [],
              "scripts": [],
              "installations": [],
              "installedFiles": []
            }
            """.trimIndent()

        val decoded = BackupJsonCodec.decode(rawJson)

        assertNull(decoded.heroes.single().heroIcon)
    }
}
