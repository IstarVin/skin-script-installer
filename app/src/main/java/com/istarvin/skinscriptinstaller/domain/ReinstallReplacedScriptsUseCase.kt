package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import javax.inject.Inject

data class ReinstallReplacedScriptsFailure(
    val scriptId: Long,
    val scriptName: String,
    val message: String
)

data class ReinstallReplacedScriptsResult(
    val totalCandidates: Int,
    val reinstalledCount: Int,
    val failures: List<ReinstallReplacedScriptsFailure>
) {
    val isSuccess: Boolean
        get() = totalCandidates > 0 && failures.isEmpty()
}

class ReinstallReplacedScriptsUseCase @Inject constructor(
    private val repository: ScriptRepository,
    private val restoreScriptUseCase: RestoreScriptUseCase,
    private val installScriptUseCase: InstallScriptUseCase
) {
    suspend fun execute(userId: Int): ReinstallReplacedScriptsResult {
        val candidates = repository.getLatestInstallationsOnce(userId)
            .filter { it.status == InstallationStatus.REPLACED }

        if (candidates.isEmpty()) {
            return ReinstallReplacedScriptsResult(
                totalCandidates = 0,
                reinstalledCount = 0,
                failures = emptyList()
            )
        }

        restoreScriptUseCase.resetProgress()
        installScriptUseCase.resetProgress()

        val failures = mutableListOf<ReinstallReplacedScriptsFailure>()
        var reinstalledCount = 0

        candidates.forEach { installation ->
            val script = repository.getScriptById(installation.scriptId)
            val scriptName = script?.name ?: "Script ${installation.scriptId}"

            if (script == null) {
                failures += ReinstallReplacedScriptsFailure(
                    scriptId = installation.scriptId,
                    scriptName = scriptName,
                    message = "Script not found"
                )
                return@forEach
            }

            val restoreResult = restoreScriptUseCase.execute(installation.id)
            if (restoreResult.isFailure) {
                failures += ReinstallReplacedScriptsFailure(
                    scriptId = installation.scriptId,
                    scriptName = scriptName,
                    message = restoreResult.exceptionOrNull()?.message ?: "Restore failed"
                )
                return@forEach
            }

            val installResult = installScriptUseCase.execute(script.id, userId)
            if (installResult.isSuccess) {
                reinstalledCount += 1
            } else {
                failures += ReinstallReplacedScriptsFailure(
                    scriptId = installation.scriptId,
                    scriptName = scriptName,
                    message = installResult.exceptionOrNull()?.message ?: "Install failed"
                )
            }
        }

        return ReinstallReplacedScriptsResult(
            totalCandidates = candidates.size,
            reinstalledCount = reinstalledCount,
            failures = failures
        )
    }
}
