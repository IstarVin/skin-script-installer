package com.istarvin.skinscriptinstaller.domain.backup

import com.istarvin.skinscriptinstaller.data.db.AppDatabase

object BackupCompatibilityRegistry {
    const val CURRENT_BACKUP_FORMAT_VERSION = 2

    private val supportedRules = listOf(
        CompatibilityRule(
            formatVersion = 1,
            minAppVersionCode = 2,
            maxAppVersionCode = Int.MAX_VALUE,
            supportedDatabaseVersions = setOf(1, 2)
        ),
        CompatibilityRule(
            formatVersion = 2,
            minAppVersionCode = 2,
            maxAppVersionCode = Int.MAX_VALUE,
            supportedDatabaseVersions = setOf(1, 2, 3, 4)
        )
    )

    fun validate(
        manifest: AppBackupManifest,
        migrationEdges: Set<Pair<Int, Int>>
    ): BackupCompatibilityResult {
        val matchingRule = supportedRules.firstOrNull { rule ->
            rule.formatVersion == manifest.formatVersion &&
                manifest.appVersionCode in rule.minAppVersionCode..rule.maxAppVersionCode
        } ?: return BackupCompatibilityResult(
            isCompatible = false,
            reason = "Unsupported backup format or app version in backup file"
        )

        if (manifest.databaseVersion !in matchingRule.supportedDatabaseVersions) {
            val canMigrate = canMigrate(
                fromVersion = manifest.databaseVersion,
                toVersion = AppDatabase.DATABASE_VERSION,
                migrationEdges = migrationEdges
            )
            if (!canMigrate) {
                return BackupCompatibilityResult(
                    isCompatible = false,
                    reason = "Backup database schema (${manifest.databaseVersion}) is incompatible with current app schema (${AppDatabase.DATABASE_VERSION}) and cannot be migrated"
                )
            }
        }

        return BackupCompatibilityResult(isCompatible = true)
    }

    private fun canMigrate(
        fromVersion: Int,
        toVersion: Int,
        migrationEdges: Set<Pair<Int, Int>>
    ): Boolean {
        if (fromVersion == toVersion) return true

        val visited = mutableSetOf<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(fromVersion)
        visited.add(fromVersion)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val nextNodes = migrationEdges
                .filter { it.first == current }
                .map { it.second }

            nextNodes.forEach { next ->
                if (next == toVersion) {
                    return true
                }
                if (visited.add(next)) {
                    queue.add(next)
                }
            }
        }

        return false
    }

    private data class CompatibilityRule(
        val formatVersion: Int,
        val minAppVersionCode: Int,
        val maxAppVersionCode: Int,
        val supportedDatabaseVersions: Set<Int>
    )
}
