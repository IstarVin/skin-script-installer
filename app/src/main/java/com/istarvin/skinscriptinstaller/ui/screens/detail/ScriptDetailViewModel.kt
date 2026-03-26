package com.istarvin.skinscriptinstaller.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.data.user.ActiveUserStore
import com.istarvin.skinscriptinstaller.domain.InstallProgress
import com.istarvin.skinscriptinstaller.domain.InstallScriptUseCase
import com.istarvin.skinscriptinstaller.domain.RestoreScriptUseCase
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FileTreeNode(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val children: List<FileTreeNode> = emptyList(),
    val depth: Int = 0
)

@HiltViewModel
class ScriptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScriptRepository,
    private val activeUserStore: ActiveUserStore,
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

    private val _expandedDirectoryIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedDirectoryIds: StateFlow<Set<String>> = _expandedDirectoryIds.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private val _eligibleUserIds = MutableStateFlow<List<Int>>(emptyList())
    val eligibleUserIds: StateFlow<List<Int>> = _eligibleUserIds.asStateFlow()

    private val _selectedUserId = MutableStateFlow(0)
    val selectedUserId: StateFlow<Int> = _selectedUserId.asStateFlow()

    val installProgress: StateFlow<InstallProgress?> = installScriptUseCase.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val restoreProgress: StateFlow<InstallProgress?> = restoreScriptUseCase.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isShizukuReady: StateFlow<Boolean> = shizukuManager.isServiceBound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadScript()
        observeActiveUser()
        observeFileService()
    }

    private fun observeActiveUser() {
        viewModelScope.launch {
            activeUserStore.activeUserId.collectLatest { activeUserId ->
                if (_selectedUserId.value != activeUserId) {
                    _selectedUserId.value = activeUserId
                    loadInstallationForSelectedUser()
                }
            }
        }
    }

    private fun observeFileService() {
        viewModelScope.launch {
            shizukuManager.fileService.collectLatest {
                refreshEligibleUsers()
            }
        }
    }

    private fun refreshEligibleUsers() {
        val service = shizukuManager.fileService.value
        if (service == null) {
            _eligibleUserIds.value = emptyList()
            _selectedUserId.value = 0
            return
        }

        val detected = try {
            service.listEligibleMlUserIds().toList().distinct().sorted()
        } catch (_: Exception) {
            emptyList()
        }

        _eligibleUserIds.value = detected

        _selectedUserId.value = when {
            detected.isEmpty() -> 0
            _selectedUserId.value in detected -> _selectedUserId.value
            0 in detected -> 0
            else -> detected.first()
        }

        activeUserStore.setActiveUser(_selectedUserId.value)
        loadInstallationForSelectedUser()
    }

    fun selectInstallUser(userId: Int) {
        if (userId in _eligibleUserIds.value) {
            _selectedUserId.value = userId
            activeUserStore.setActiveUser(userId)
            loadInstallationForSelectedUser()
        }
    }

    private fun loadScript() {
        viewModelScope.launch {
            val script = repository.getScriptById(scriptId)
            _script.value = script
            script?.let {
                buildFileTree(it.storagePath)
                loadInstallationForSelectedUser()
            }
        }
    }

    private fun loadInstallationForSelectedUser() {
        val currentScriptId = _script.value?.id ?: return
        viewModelScope.launch {
            _installation.value = repository.getLatestInstallation(currentScriptId, _selectedUserId.value)
        }
    }

    private fun buildFileTree(storagePath: String) {
        val root = File(storagePath)
        if (root.exists()) {
            val tree = buildTreeNodes(root, 0)
            _fileTree.value = tree
            _expandedDirectoryIds.value = collectDefaultExpandedDirectoryIds(tree)
        } else {
            _fileTree.value = emptyList()
            _expandedDirectoryIds.value = emptySet()
        }
    }

    private fun buildTreeNodes(dir: File, depth: Int): List<FileTreeNode> {
        val children = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name }
        ) ?: return emptyList()

        return children.map { file ->
            FileTreeNode(
                id = file.absolutePath,
                name = file.name,
                isDirectory = file.isDirectory,
                children = if (file.isDirectory) buildTreeNodes(file, depth + 1) else emptyList(),
                depth = depth
            )
        }
    }

    private fun collectDefaultExpandedDirectoryIds(nodes: List<FileTreeNode>): Set<String> {
        val pathToArt = findPathToDirectory(nodes, targetName = "Art") ?: return emptySet()
        return pathToArt.dropLast(1).mapTo(mutableSetOf()) { it.id }
    }

    private fun findPathToDirectory(
        nodes: List<FileTreeNode>,
        targetName: String
    ): List<FileTreeNode>? {
        for (node in nodes) {
            if (!node.isDirectory) {
                continue
            }

            if (node.name == targetName) {
                return listOf(node)
            }

            val childPath = findPathToDirectory(node.children, targetName)
            if (childPath != null) {
                return listOf(node) + childPath
            }
        }
        return null
    }

    fun toggleDirectory(directoryId: String) {
        _expandedDirectoryIds.value = _expandedDirectoryIds.value.toMutableSet().apply {
            if (contains(directoryId)) remove(directoryId) else add(directoryId)
        }
    }

    fun install() {
        performInstall(_selectedUserId.value)
    }

    fun installForUser(userId: Int) {
        if (userId !in _eligibleUserIds.value) {
            _error.value = "Selected user is not eligible"
            return
        }
        _selectedUserId.value = userId
        performInstall(userId)
    }

    private fun performInstall(targetUserId: Int) {
        viewModelScope.launch {
            if (_eligibleUserIds.value.isEmpty()) {
                _error.value = "No Mobile Legends user found in /storage/emulated"
                return@launch
            }

            activeUserStore.setActiveUser(targetUserId)

            _isOperating.value = true
            _error.value = null
            installScriptUseCase.resetProgress()

            val result = installScriptUseCase.execute(scriptId, targetUserId)
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
                _installation.value = repository.getLatestInstallation(scriptId, _selectedUserId.value)
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

