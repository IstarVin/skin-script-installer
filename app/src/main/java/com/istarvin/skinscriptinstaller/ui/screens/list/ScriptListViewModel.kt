package com.istarvin.skinscriptinstaller.ui.screens.list

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.domain.ImportScriptUseCase
import com.istarvin.skinscriptinstaller.domain.RestoreScriptUseCase
import com.istarvin.skinscriptinstaller.service.InvalidPasswordException
import com.istarvin.skinscriptinstaller.service.PasswordRequiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScriptWithStatus(
    val script: SkinScript,
    val latestInstallation: Installation? = null
) {
    val status: String
        get() = latestInstallation?.status ?: "not_installed"
}

data class ZipPasswordPrompt(
    val zipUri: Uri,
    val errorMessage: String? = null
)

@HiltViewModel
class ScriptListViewModel @Inject constructor(
    private val repository: ScriptRepository,
    private val importScriptUseCase: ImportScriptUseCase,
    private val restoreScriptUseCase: RestoreScriptUseCase
) : ViewModel() {

    private val scripts: StateFlow<List<SkinScript>> = repository.getAllScripts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scriptsWithStatus: StateFlow<List<ScriptWithStatus>> = combine(
        scripts,
        repository.getLatestInstallations()
    ) { scriptList, latestInstallations ->
        val latestByScriptId = latestInstallations.associateBy { it.scriptId }
        scriptList.map { script ->
            ScriptWithStatus(script, latestByScriptId[script.id])
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _zipPasswordPrompt = MutableStateFlow<ZipPasswordPrompt?>(null)
    val zipPasswordPrompt: StateFlow<ZipPasswordPrompt?> = _zipPasswordPrompt.asStateFlow()

    fun importScript(treeUri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null
            _zipPasswordPrompt.value = null
            val result = importScriptUseCase.execute(treeUri)
            result.onFailure { e ->
                _importError.value = e.message ?: "Import failed"
            }
            _isImporting.value = false
        }
    }

    fun importZip(zipUri: Uri) {
        importZipInternal(zipUri, password = null)
    }

    fun retryZipWithPassword(password: String) {
        val prompt = _zipPasswordPrompt.value ?: return
        importZipInternal(prompt.zipUri, password.toCharArray())
    }

    fun dismissZipPasswordPrompt() {
        _zipPasswordPrompt.value = null
    }

    private fun importZipInternal(zipUri: Uri, password: CharArray?) {
        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null

            val result = importScriptUseCase.executeZip(zipUri, password)
            result.onSuccess {
                _zipPasswordPrompt.value = null
            }
            result.onFailure { e ->
                when (e) {
                    is PasswordRequiredException -> {
                        _zipPasswordPrompt.value = ZipPasswordPrompt(
                            zipUri = zipUri,
                            errorMessage = null
                        )
                    }

                    is InvalidPasswordException -> {
                        _zipPasswordPrompt.value = ZipPasswordPrompt(
                            zipUri = zipUri,
                            errorMessage = "Incorrect password, try again"
                        )
                    }

                    else -> {
                        _zipPasswordPrompt.value = null
                        _importError.value = e.message ?: "Import failed"
                    }
                }
            }

            _isImporting.value = false
        }
    }

    fun deleteScript(script: SkinScript, restoreBeforeDelete: Boolean = false) {
        viewModelScope.launch {
            _importError.value = null

            if (restoreBeforeDelete) {
                val latestInstallation = repository.getLatestInstallation(script.id)
                if (latestInstallation == null || latestInstallation.status != "installed") {
                    _importError.value = "No active installation found to restore"
                    return@launch
                }

                restoreScriptUseCase.resetProgress()
                val restoreResult = restoreScriptUseCase.execute(latestInstallation.id)
                restoreResult.onFailure { e ->
                    _importError.value = e.message ?: "Restore failed"
                    return@launch
                }
            }

            // Delete from DB
            repository.deleteScript(script)
            // Delete files from internal storage
            val dir = java.io.File(script.storagePath)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    fun clearImportError() {
        _importError.value = null
    }
}

