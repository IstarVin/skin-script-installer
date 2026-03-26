package com.istarvin.skinscriptinstaller.ui.screens.list

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.domain.ImportScriptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class ScriptListViewModel @Inject constructor(
    private val repository: ScriptRepository,
    private val importScriptUseCase: ImportScriptUseCase
) : ViewModel() {

    val scripts: StateFlow<List<SkinScript>> = repository.getAllScripts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scriptsWithStatus = MutableStateFlow<List<ScriptWithStatus>>(emptyList())
    val scriptsWithStatus: StateFlow<List<ScriptWithStatus>> = _scriptsWithStatus.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    init {
        viewModelScope.launch {
            scripts.collect { scriptList ->
                val withStatus = scriptList.map { script ->
                    val installation = repository.getLatestInstallation(script.id)
                    ScriptWithStatus(script, installation)
                }
                _scriptsWithStatus.value = withStatus
            }
        }
    }

    fun importScript(treeUri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null
            val result = importScriptUseCase.execute(treeUri)
            result.onFailure { e ->
                _importError.value = e.message ?: "Import failed"
            }
            _isImporting.value = false
        }
    }

    fun deleteScript(script: SkinScript) {
        viewModelScope.launch {
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

