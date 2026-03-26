package com.istarvin.skinscriptinstaller.ui.screens.list

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.Skin
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.data.user.ActiveUserStore
import com.istarvin.skinscriptinstaller.domain.ImportScriptUseCase
import com.istarvin.skinscriptinstaller.domain.RestoreScriptUseCase
import com.istarvin.skinscriptinstaller.service.InvalidPasswordException
import com.istarvin.skinscriptinstaller.service.PasswordRequiredException
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val UNCATEGORIZED_SECTION_KEY = "__uncategorized__"

data class ScriptWithStatus(
    val script: SkinScript,
    val latestInstallation: Installation? = null,
    val heroName: String? = null,
    val originalSkinName: String? = null,
    val replacementSkinName: String? = null
) {
    val status: String
        get() = latestInstallation?.status ?: "not_installed"

    val isClassified: Boolean
        get() = heroName != null
}

data class ZipPasswordPrompt(
    val zipUri: Uri,
    val errorMessage: String? = null
)

data class HeroScriptGroup(
    val key: String,
    val title: String,
    val scripts: List<ScriptWithStatus>
) {
    val count: Int
        get() = scripts.size
}

data class HeroScriptSection(
    val key: String,
    val title: String,
    val scripts: List<ScriptWithStatus>,
    val isExpanded: Boolean
) {
    val count: Int
        get() = scripts.size
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScriptListViewModel @Inject constructor(
    private val repository: ScriptRepository,
    private val importScriptUseCase: ImportScriptUseCase,
    private val restoreScriptUseCase: RestoreScriptUseCase,
    private val activeUserStore: ActiveUserStore,
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    private var didInitializeExpandedSections = false

    private val _eligibleUserIds = MutableStateFlow<List<Int>>(emptyList())
    val eligibleUserIds: StateFlow<List<Int>> = _eligibleUserIds.asStateFlow()

    private val _expandedSectionKeys = MutableStateFlow<Set<String>>(emptySet())
    val expandedSectionKeys: StateFlow<Set<String>> = _expandedSectionKeys.asStateFlow()

    val activeUserId: StateFlow<Int> = activeUserStore.activeUserId

    private val scripts: StateFlow<List<SkinScript>> = repository.getAllScripts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val heroes: StateFlow<List<Hero>> = repository.getAllHeroes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scriptsWithStatus: StateFlow<List<ScriptWithStatus>> = activeUserId
        .flatMapLatest { selectedUserId ->
            combine(
                scripts,
                repository.getLatestInstallations(selectedUserId),
                heroes
            ) { scriptList, latestInstallations, heroList ->
                val latestByScriptId = latestInstallations.associateBy { it.scriptId }
                val heroById = heroList.associateBy { it.id }
                scriptList.map { script ->
                    val hero = script.heroId?.let { heroById[it] }
                    val originalSkin = script.originalSkinId?.let { id ->
                        repository.getSkinById(id)
                    }
                    val replacementSkin = script.replacementSkinId?.let { id ->
                        repository.getSkinById(id)
                    }
                    ScriptWithStatus(
                        script = script,
                        latestInstallation = latestByScriptId[script.id],
                        heroName = hero?.name,
                        originalSkinName = originalSkin?.name,
                        replacementSkinName = replacementSkin?.name
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val heroScriptGroups: StateFlow<List<HeroScriptGroup>> = scriptsWithStatus
        .map(::buildHeroScriptGroups)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val heroScriptSections: StateFlow<List<HeroScriptSection>> = combine(
        heroScriptGroups,
        expandedSectionKeys
    ) { groups, expandedKeys ->
        groups.map { group ->
            HeroScriptSection(
                key = group.key,
                title = group.title,
                scripts = group.scripts,
                isExpanded = group.key in expandedKeys
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _zipPasswordPrompt = MutableStateFlow<ZipPasswordPrompt?>(null)
    val zipPasswordPrompt: StateFlow<ZipPasswordPrompt?> = _zipPasswordPrompt.asStateFlow()

    init {
        observeEligibleUsers()
        observeHeroSections()
    }

    private fun observeHeroSections() {
        viewModelScope.launch {
            heroScriptGroups.collect { groups ->
                val groupKeys = groups.mapTo(linkedSetOf()) { it.key }
                val currentExpandedKeys = _expandedSectionKeys.value
                val nextExpandedKeys = when {
                    groupKeys.isEmpty() -> emptySet()
                    !didInitializeExpandedSections -> {
                        didInitializeExpandedSections = true
                        groupKeys
                    }

                    else -> buildSet {
                        currentExpandedKeys
                            .filterTo(this) { it in groupKeys }
                        addAll(groupKeys - currentExpandedKeys)
                    }
                }

                if (nextExpandedKeys != currentExpandedKeys) {
                    _expandedSectionKeys.value = nextExpandedKeys
                }
            }
        }
    }

    private fun observeEligibleUsers() {
        viewModelScope.launch {
            shizukuManager.fileService.collect {
                refreshEligibleUsers()
            }
        }
    }

    private fun refreshEligibleUsers() {
        val service = shizukuManager.fileService.value
        if (service == null) {
            _eligibleUserIds.value = emptyList()
            activeUserStore.setActiveUser(0)
            return
        }

        val detected = try {
            service.listEligibleMlUserIds().toList().distinct().sorted()
        } catch (_: Exception) {
            emptyList()
        }

        _eligibleUserIds.value = detected

        val nextActiveUser = when {
            detected.isEmpty() -> 0
            activeUserId.value in detected -> activeUserId.value
            0 in detected -> 0
            else -> detected.first()
        }
        activeUserStore.setActiveUser(nextActiveUser)
    }

    fun selectActiveUser(userId: Int) {
        if (userId in _eligibleUserIds.value) {
            activeUserStore.setActiveUser(userId)
        }
    }

    fun toggleHeroSection(sectionKey: String) {
        _expandedSectionKeys.value = _expandedSectionKeys.value.toMutableSet().apply {
            if (!add(sectionKey)) {
                remove(sectionKey)
            }
        }
    }

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
                val latestInstallation = repository.getLatestInstallation(script.id, activeUserId.value)
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

    private fun buildHeroScriptGroups(items: List<ScriptWithStatus>): List<HeroScriptGroup> {
        if (items.isEmpty()) {
            return emptyList()
        }

        return items
            .groupBy { item -> item.heroName ?: UNCATEGORIZED_SECTION_KEY }
            .map { (key, scriptsForHero) ->
                HeroScriptGroup(
                    key = key,
                    title = scriptsForHero.first().heroName ?: "Uncategorized",
                    scripts = scriptsForHero
                )
            }
    }
}

