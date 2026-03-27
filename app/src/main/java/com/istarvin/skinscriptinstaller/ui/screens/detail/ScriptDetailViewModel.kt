package com.istarvin.skinscriptinstaller.ui.screens.detail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.Skin
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.domain.ClassifyScriptUseCase
import com.istarvin.skinscriptinstaller.domain.ImportScriptUseCase
import com.istarvin.skinscriptinstaller.domain.InstallProgress
import com.istarvin.skinscriptinstaller.domain.InstallScriptUseCase
import com.istarvin.skinscriptinstaller.domain.RestoreScriptUseCase
import com.istarvin.skinscriptinstaller.domain.UserSelectionManager
import com.istarvin.skinscriptinstaller.service.InvalidPasswordException
import com.istarvin.skinscriptinstaller.service.PasswordRequiredException
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

data class DetailZipPasswordPrompt(
    val zipUri: Uri,
    val errorMessage: String? = null
)

@HiltViewModel
class ScriptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScriptRepository,
    private val userSelectionManager: UserSelectionManager,
    private val importScriptUseCase: ImportScriptUseCase,
    private val installScriptUseCase: InstallScriptUseCase,
    private val restoreScriptUseCase: RestoreScriptUseCase,
    private val classifyScriptUseCase: ClassifyScriptUseCase,
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    private val scriptId: Long = savedStateHandle["scriptId"] ?: -1L
    private var pendingReinstallUserId: Int? = null

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

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _zipPasswordPrompt = MutableStateFlow<DetailZipPasswordPrompt?>(null)
    val zipPasswordPrompt: StateFlow<DetailZipPasswordPrompt?> = _zipPasswordPrompt.asStateFlow()

    // Classification state
    private val _heroName = MutableStateFlow<String?>(null)
    val heroName: StateFlow<String?> = _heroName.asStateFlow()

    private val _originalSkinName = MutableStateFlow<String?>(null)
    val originalSkinName: StateFlow<String?> = _originalSkinName.asStateFlow()

    private val _replacementSkinName = MutableStateFlow<String?>(null)
    val replacementSkinName: StateFlow<String?> = _replacementSkinName.asStateFlow()

    val allHeroes: StateFlow<List<Hero>> = repository.getAllHeroes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suggestedHeroName: StateFlow<String?> = combine(_script, allHeroes) { script, heroes ->
        if (script == null || heroes.isEmpty()) null
        else inferHeroFromScriptName(script.name, heroes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _skinsForSelectedHero = MutableStateFlow<List<Skin>>(emptyList())
    val skinsForSelectedHero: StateFlow<List<Skin>> = _skinsForSelectedHero.asStateFlow()

    val eligibleUserIds: StateFlow<List<Int>> = userSelectionManager.eligibleUserIds

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
        userSelectionManager.observeFileService(viewModelScope)
    }

    private fun observeActiveUser() {
        viewModelScope.launch {
            userSelectionManager.activeUserId.collectLatest { activeUserId ->
                if (_selectedUserId.value != activeUserId) {
                    _selectedUserId.value = activeUserId
                    loadInstallationForSelectedUser()
                }
            }
        }
        viewModelScope.launch {
            userSelectionManager.eligibleUserIds.collectLatest {
                // Sync selected user when eligible users change
                val current = _selectedUserId.value
                val eligible = userSelectionManager.eligibleUserIds.value
                if (eligible.isNotEmpty() && current !in eligible) {
                    _selectedUserId.value = eligible.first()
                    loadInstallationForSelectedUser()
                }
            }
        }
    }

    fun selectInstallUser(userId: Int) {
        if (userId in userSelectionManager.eligibleUserIds.value) {
            _selectedUserId.value = userId
            userSelectionManager.selectUser(userId)
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
                loadClassification(it)
            }
        }
    }

    private suspend fun loadClassification(script: SkinScript) {
        val hero = script.heroId?.let { repository.getHeroById(it) }
        val originalSkin = script.originalSkinId?.let { repository.getSkinById(it) }
        val replacementSkin = script.replacementSkinId?.let { repository.getSkinById(it) }
        _heroName.value = hero?.name
        _originalSkinName.value = originalSkin?.name
        _replacementSkinName.value = replacementSkin?.name
        // Load skins for the current hero (for autocomplete)
        hero?.let { loadSkinsForHero(it.id) }
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
        if (userId !in userSelectionManager.eligibleUserIds.value) {
            _error.value = "Selected user is not eligible"
            return
        }
        _selectedUserId.value = userId
        performInstall(userId)
    }

    private fun performInstall(targetUserId: Int) {
        viewModelScope.launch {
            if (userSelectionManager.eligibleUserIds.value.isEmpty()) {
                _error.value = "No Mobile Legends user found in /storage/emulated"
                return@launch
            }

            userSelectionManager.selectUser(targetUserId)

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

    fun reinstall() {
        performReinstall(_selectedUserId.value)
    }

    fun reinstallForUser(userId: Int) {
        if (userId !in userSelectionManager.eligibleUserIds.value) {
            _error.value = "Selected user is not eligible"
            return
        }
        _selectedUserId.value = userId
        performReinstall(userId)
    }

    private fun performReinstall(targetUserId: Int) {
        viewModelScope.launch {
            if (userSelectionManager.eligibleUserIds.value.isEmpty()) {
                _error.value = "No Mobile Legends user found in /storage/emulated"
                return@launch
            }

            val inst = repository.getLatestInstallation(scriptId, targetUserId)

            if (inst == null || inst.status != "installed") {
                _error.value = "No installed version available to reinstall"
                return@launch
            }

            userSelectionManager.selectUser(targetUserId)

            _isOperating.value = true
            _error.value = null
            restoreScriptUseCase.resetProgress()
            installScriptUseCase.resetProgress()

            val restoreResult = restoreScriptUseCase.execute(inst.id)
            if (restoreResult.isFailure) {
                _error.value = restoreResult.exceptionOrNull()?.message ?: "Reinstall failed during restore"
                _isOperating.value = false
                return@launch
            }

            val installResult = installScriptUseCase.execute(scriptId, targetUserId)
            installResult.onSuccess {
                _installation.value = it
            }.onFailure { e ->
                _error.value = e.message ?: "Reinstall failed during install"
            }

            _isOperating.value = false
        }
    }

    fun updateScript(treeUri: Uri) {
        viewModelScope.launch {
            performScriptUpdate {
                importScriptUseCase.prepareTreeImport(treeUri)
            }
        }
    }

    fun updateZip(zipUri: Uri) {
        updateZipInternal(zipUri, password = null)
    }

    fun retryZipWithPassword(password: String) {
        val prompt = _zipPasswordPrompt.value ?: return
        updateZipInternal(prompt.zipUri, password.toCharArray())
    }

    fun dismissZipPasswordPrompt() {
        _zipPasswordPrompt.value = null
    }

    private fun updateZipInternal(zipUri: Uri, password: CharArray?) {
        viewModelScope.launch {
            performScriptUpdate(zipUriForPrompt = zipUri) {
                importScriptUseCase.prepareZipImport(zipUri, password)
            }
        }
    }

    private suspend fun performScriptUpdate(
        zipUriForPrompt: Uri? = null,
        importer: suspend () -> Result<com.istarvin.skinscriptinstaller.domain.ImportedScriptPayload>
    ) {
        val currentScript = _script.value ?: run {
            _error.value = "Script not found"
            return
        }

        _isImporting.value = true
        _isOperating.value = true
        _error.value = null

        val targetUserId = _selectedUserId.value
        val activeInstallation = repository.getLatestInstallation(currentScript.id, targetUserId)
        val wasInstalled = activeInstallation?.status == "installed"
        val shouldReinstallAfterUpdate = pendingReinstallUserId == targetUserId || wasInstalled

        if (!shouldReinstallAfterUpdate) {
            pendingReinstallUserId = null
        }

        if (wasInstalled) {
            restoreScriptUseCase.resetProgress()
            val restoreResult = restoreScriptUseCase.execute(activeInstallation.id)
            if (restoreResult.isFailure) {
                _error.value = restoreResult.exceptionOrNull()?.message ?: "Update failed during restore"
                _installation.value = repository.getLatestInstallation(currentScript.id, targetUserId)
                pendingReinstallUserId = null
                _isImporting.value = false
                _isOperating.value = false
                return
            }
            pendingReinstallUserId = targetUserId
            _installation.value = repository.getLatestInstallation(currentScript.id, targetUserId)
        }

        val importedPayload = importer().getOrElse { error ->
            when (error) {
                is PasswordRequiredException -> {
                    if (zipUriForPrompt != null) {
                        _zipPasswordPrompt.value = DetailZipPasswordPrompt(zipUri = zipUriForPrompt)
                    } else {
                        _error.value = error.message ?: "Password required"
                    }
                }

                is InvalidPasswordException -> {
                    if (zipUriForPrompt != null) {
                        _zipPasswordPrompt.value = DetailZipPasswordPrompt(
                            zipUri = zipUriForPrompt,
                            errorMessage = "Incorrect password, try again"
                        )
                    } else {
                        _error.value = error.message ?: "Incorrect password"
                    }
                }

                else -> {
                    _zipPasswordPrompt.value = null
                    _error.value = error.message ?: "Update failed during import"
                    pendingReinstallUserId = null
                }
            }
            _installation.value = repository.getLatestInstallation(currentScript.id, targetUserId)
            _isImporting.value = false
            _isOperating.value = false
            return
        }

        _zipPasswordPrompt.value = null

        val oldStorageDir = File(currentScript.storagePath)
        val newStorageDir = File(importedPayload.storagePath)

        try {
            val updatedScript = currentScript.copy(
                name = importedPayload.name,
                importedAt = System.currentTimeMillis(),
                storagePath = importedPayload.storagePath
            )
            repository.updateScript(updatedScript)
            _script.value = updatedScript
            buildFileTree(updatedScript.storagePath)

            if (shouldReinstallAfterUpdate) {
                userSelectionManager.selectUser(targetUserId)
                installScriptUseCase.resetProgress()
                val installResult = installScriptUseCase.execute(currentScript.id, targetUserId)
                installResult.onSuccess {
                    _installation.value = it
                }.onFailure { e ->
                    _error.value = e.message ?: "Update failed during install"
                }
            } else {
                _installation.value = repository.getLatestInstallation(currentScript.id, targetUserId)
            }

            if (oldStorageDir.exists() && oldStorageDir.absolutePath != newStorageDir.absolutePath) {
                oldStorageDir.deleteRecursively()
            }

            pendingReinstallUserId = null
            refreshScript()
        } catch (e: Exception) {
            _error.value = e.message ?: "Update failed"
            _installation.value = repository.getLatestInstallation(currentScript.id, targetUserId)
        } finally {
            if (_script.value?.storagePath != newStorageDir.absolutePath && newStorageDir.exists()) {
                newStorageDir.deleteRecursively()
            }
            _isImporting.value = false
            _isOperating.value = false
        }
    }

    private suspend fun refreshScript() {
        val refreshed = repository.getScriptById(scriptId)
        _script.value = refreshed
        refreshed?.let {
            buildFileTree(it.storagePath)
            loadInstallationForSelectedUser()
            loadClassification(it)
        }
    }

    fun clearError() {
        _error.value = null
    }

    // --- Classification ---

    fun loadSkinsForHeroName(heroName: String) {
        viewModelScope.launch {
            val hero = repository.getHeroByName(heroName)
            if (hero != null) {
                loadSkinsForHero(hero.id)
            } else {
                // New hero — show the default skins that will be auto-created
                _skinsForSelectedHero.value = defaultSkinsForHero(heroId = 0)
            }
        }
    }

    private suspend fun loadSkinsForHero(heroId: Long) {
        val skins = repository.getSkinsByHeroIdOnce(heroId)
        _skinsForSelectedHero.value = if (skins.isEmpty()) {
            defaultSkinsForHero(heroId)
        } else {
            skins
        }
    }

    private fun inferHeroFromScriptName(scriptName: String, heroes: List<Hero>): String? {
        // Strip parenthetical/bracket suffixes (e.g. "(SFILE.MOBI)"), normalise to lowercase words
        val normalized = scriptName
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .lowercase()
        // Sort longest names first so multi-word heroes (e.g. "Yi Sun-shin") beat subsets (e.g. "Yi")
        return heroes
            .sortedByDescending { it.name.length }
            .firstOrNull { hero ->
                val heroNorm = hero.name
                    .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
                    .lowercase()
                    .trim()
                val words = heroNorm.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.isEmpty()) return@firstOrNull false
                val pattern = words.joinToString("\\s+") { Regex.escape(it) }
                normalized.contains(Regex("\\b$pattern\\b"))
            }?.name
    }

    private fun defaultSkinsForHero(heroId: Long): List<Skin> {
        return listOf(
            Skin(heroId = heroId, name = "Default"),
            Skin(heroId = heroId, name = "Basic")
        )
    }

    fun classifyScript(heroName: String, originalSkinName: String, replacementSkinName: String) {
        viewModelScope.launch {
            _error.value = null
            val result = classifyScriptUseCase.execute(
                scriptId = scriptId,
                heroName = heroName.trim(),
                originalSkinName = originalSkinName.trim(),
                replacementSkinName = replacementSkinName.trim()
            )
            result.onSuccess {
                // Reload script and classification
                val updatedScript = repository.getScriptById(scriptId)
                _script.value = updatedScript
                updatedScript?.let { loadClassification(it) }
            }.onFailure { e ->
                _error.value = e.message ?: "Classification failed"
            }
        }
    }

    fun clearClassification() {
        viewModelScope.launch {
            _error.value = null
            val result = classifyScriptUseCase.clearClassification(scriptId)
            result.onSuccess {
                _heroName.value = null
                _originalSkinName.value = null
                _replacementSkinName.value = null
                _skinsForSelectedHero.value = emptyList()
                _script.value = repository.getScriptById(scriptId)
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to clear classification"
            }
        }
    }
}
