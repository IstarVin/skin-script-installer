package com.istarvin.skinscriptinstaller.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.domain.InstallProgress
import com.istarvin.skinscriptinstaller.domain.InstallScriptUseCase
import com.istarvin.skinscriptinstaller.domain.RestoreScriptUseCase
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FileTreeNode(
    val name: String,
    val isDirectory: Boolean,
    val children: List<FileTreeNode> = emptyList(),
    val depth: Int = 0
)

@HiltViewModel
class ScriptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScriptRepository,
    private val installScriptUseCase: InstallScriptUseCase,
    private val restoreScriptUseCase: RestoreScriptUseCase,
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    private val scriptId: Long = savedStateHandle["scriptId"] ?: -1L

    private val _script = MutableStateFlow<SkinScript?>(null)
    val script: StateFlow<SkinScript?> = _script.asStateFlow()

    private val _installation = MutableStateFlow<Installation?>(null)
    val installation: StateFlow<Installation?> = _installation.asStateFlow()

    private val _fileTree = MutableStateFlow<List<FileTreeNode>>(emptyList())
    val fileTree: StateFlow<List<FileTreeNode>> = _fileTree.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    val installProgress: StateFlow<InstallProgress?> = installScriptUseCase.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val restoreProgress: StateFlow<InstallProgress?> = restoreScriptUseCase.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isShizukuReady: StateFlow<Boolean> = shizukuManager.isServiceBound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadScript()
    }

    private fun loadScript() {
        viewModelScope.launch {
            val script = repository.getScriptById(scriptId)
            _script.value = script
            script?.let {
                _installation.value = repository.getLatestInstallation(it.id)
                buildFileTree(it.storagePath)
            }
        }
    }

    private fun buildFileTree(storagePath: String) {
        val root = File(storagePath)
        if (root.exists()) {
            _fileTree.value = buildTreeNodes(root, 0)
        }
    }

    private fun buildTreeNodes(dir: File, depth: Int): List<FileTreeNode> {
        val children = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name }
        ) ?: return emptyList()

        return children.map { file ->
            FileTreeNode(
                name = file.name,
                isDirectory = file.isDirectory,
                children = if (file.isDirectory) buildTreeNodes(file, depth + 1) else emptyList(),
                depth = depth
            )
        }
    }

    fun install() {
        viewModelScope.launch {
            _isOperating.value = true
            _error.value = null
            installScriptUseCase.resetProgress()

            val result = installScriptUseCase.execute(scriptId)
            result.onSuccess {
                _installation.value = it
            }.onFailure { e ->
                _error.value = e.message ?: "Install failed"
            }
            _isOperating.value = false
        }
    }

    fun restore() {
        viewModelScope.launch {
            val inst = _installation.value ?: return@launch
            _isOperating.value = true
            _error.value = null
            restoreScriptUseCase.resetProgress()

            val result = restoreScriptUseCase.execute(inst.id)
            result.onSuccess {
                // Reload installation to get updated status
                _installation.value = repository.getLatestInstallation(scriptId)
            }.onFailure { e ->
                _error.value = e.message ?: "Restore failed"
            }
            _isOperating.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}

