package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import javax.inject.Inject

data class RestoreAllScriptsFailure(
    val scriptName: String,
    val message: String
)

data class RestoreAllScriptsResult(
    val totalCandidates: Int,
    val restoredCount: Int,
    val failures: List<RestoreAllScriptsFailure>
) {
    val isSuccess: Boolean
        get() = totalCandidates > 0 && failures.isEmpty()
}

class RestoreAllScriptsUseCase @Inject constructor(
    private val repository: ScriptRepository,
    private val restoreScriptUseCase: RestoreScriptUseCase
) {
    suspend fun execute(userId: Int): RestoreAllScriptsResult {
        val restorableScripts = repository.getLatestInstalledScriptsByUserId(userId)

        if (restorableScripts.isEmpty()) {
            return RestoreAllScriptsResult(
                totalCandidates = 0,
                restoredCount = 0,
                failures = emptyList()
            )
        }

        restoreScriptUseCase.resetProgress()

        val failures = mutableListOf<RestoreAllScriptsFailure>()
        var restoredCount = 0

        restorableScripts.forEach { candidate ->
            val result = restoreScriptUseCase.execute(candidate.installationId)
            if (result.isSuccess) {
                restoredCount += 1
            } else {
                failures += RestoreAllScriptsFailure(
                    scriptName = candidate.scriptName,
                    message = result.exceptionOrNull()?.message ?: "Restore failed"
                )
            }
        }

        return RestoreAllScriptsResult(
            totalCandidates = restorableScripts.size,
            restoredCount = restoredCount,
            failures = failures
        )
    }
}
