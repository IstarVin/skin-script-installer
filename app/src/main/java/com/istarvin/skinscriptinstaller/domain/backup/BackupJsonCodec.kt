package com.istarvin.skinscriptinstaller.domain.backup

import org.json.JSONArray
import org.json.JSONObject

object BackupJsonCodec {
    fun encode(manifest: AppBackupManifest): String {
        val root = JSONObject()
            .put("formatVersion", manifest.formatVersion)
            .put("exportedAt", manifest.exportedAt)
            .put("appVersionCode", manifest.appVersionCode)
            .put("appVersionName", manifest.appVersionName)
            .put("databaseVersion", manifest.databaseVersion)
            .put("scripts", JSONArray().apply {
                manifest.scripts.forEach { script ->
                    put(
                        JSONObject()
                            .put("id", script.id)
                            .put("name", script.name)
                            .put("importedAt", script.importedAt)
                            .put("relativeStoragePath", script.relativeStoragePath)
                    )
                }
            })
            .put("installations", JSONArray().apply {
                manifest.installations.forEach { installation ->
                    put(
                        JSONObject()
                            .put("id", installation.id)
                            .put("scriptId", installation.scriptId)
                            .put("userId", installation.userId)
                            .put("installedAt", installation.installedAt)
                            .put("restoredAt", installation.restoredAt)
                            .put("status", installation.status)
                    )
                }
            })
            .put("installedFiles", JSONArray().apply {
                manifest.installedFiles.forEach { file ->
                    put(
                        JSONObject()
                            .put("id", file.id)
                            .put("installationId", file.installationId)
                            .put("destPath", file.destPath)
                            .put("wasOverwrite", file.wasOverwrite)
                            .put("backupRelativePath", file.backupRelativePath)
                    )
                }
            })

        return root.toString()
    }

    fun decode(rawJson: String): AppBackupManifest {
        val root = JSONObject(rawJson)

        val scripts = root.getJSONArray("scripts").toList { item ->
            SkinScriptBackupRecord(
                id = item.getLong("id"),
                name = item.getString("name"),
                importedAt = item.getLong("importedAt"),
                relativeStoragePath = item.getString("relativeStoragePath")
            )
        }

        val installations = root.getJSONArray("installations").toList { item ->
            InstallationBackupRecord(
                id = item.getLong("id"),
                scriptId = item.getLong("scriptId"),
                userId = item.optInt("userId", 0),
                installedAt = item.getLong("installedAt"),
                restoredAt = item.takeUnless { it.isNull("restoredAt") }?.getLong("restoredAt"),
                status = item.optString("status", "installed")
            )
        }

        val installedFiles = root.getJSONArray("installedFiles").toList { item ->
            InstalledFileBackupRecord(
                id = item.getLong("id"),
                installationId = item.getLong("installationId"),
                destPath = item.getString("destPath"),
                wasOverwrite = item.optBoolean("wasOverwrite", false),
                backupRelativePath = item.takeUnless { it.isNull("backupRelativePath") }
                    ?.getString("backupRelativePath")
            )
        }

        return AppBackupManifest(
            formatVersion = root.getInt("formatVersion"),
            exportedAt = root.getLong("exportedAt"),
            appVersionCode = root.optInt("appVersionCode", 0),
            appVersionName = root.optString("appVersionName", ""),
            databaseVersion = root.getInt("databaseVersion"),
            scripts = scripts,
            installations = installations,
            installedFiles = installedFiles
        )
    }

    private inline fun <T> JSONArray.toList(block: (JSONObject) -> T): List<T> {
        val result = ArrayList<T>(length())
        for (index in 0 until length()) {
            val obj = getJSONObject(index)
            result.add(block(obj))
        }
        return result
    }
}
