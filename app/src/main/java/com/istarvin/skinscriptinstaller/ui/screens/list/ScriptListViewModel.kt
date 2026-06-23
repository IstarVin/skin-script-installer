package com.istarvin.skinscriptinstaller.ui.screens.list

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.downlink.DownlinkRepositoryDataSource
import com.istarvin.skinscriptinstaller.data.downlink.DownlinkRepositoryEntry
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.domain.DownloadDownlinkScriptUseCase
import com.istarvin.skinscriptinstaller.domain.FetchHeroCatalogUseCase
import com.istarvin.skinscriptinstaller.domain.ImportScriptUseCase
import com.istarvin.skinscriptinstaller.domain.ReinstallReplacedScriptsResult
import com.istarvin.skinscriptinstaller.domain.ReinstallReplacedScriptsUseCase
import com.istarvin.skinscriptinstaller.domain.RestoreScriptUseCase
import com.istarvin.skinscriptinstaller.domain.UserSelectionManager
import com.istarvin.skinscriptinstaller.domain.VerifyInstalledScriptsUseCase
import com.istarvin.skinscriptinstaller.service.InvalidPasswordException
import com.istarvin.skinscriptinstaller.service.PasswordRequiredException
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
private const val UNKNOWN_REPLACEMENT_KEY = "__unknown_replacement__"
private const val REPLACEMENT_KEY_SEPARATOR = "::"

data class ScriptWithStatus(
    val script: SkinScript,
    val latestInstallation: Installation? = null,
    val heroName: String? = null,
    val heroIcon: String? = null,
    val originalSkinName: String? = null,
    val replacementSkinName: String? = null
) {
    val status: String
        get() = latestInstallation?.status ?: InstallationStatus.NOT_INSTALLED

    val isClassified: Boolean
        get() = heroName != null
}

sealed interface ScriptListItem {
    val key: String
    val heroName: String?
    val heroIcon: String?
    val originalSkinName: String?
    val replacementSkinName: String?
    val status: String
    val isClassified: Boolean

    data class Local(val value: ScriptWithStatus) : ScriptListItem {
        override val key: String = "local:${value.script.id}"
        override val heroName: String? = value.heroName
        override val heroIcon: String? = value.heroIcon
        override val originalSkinName: String? = value.originalSkinName
        override val replacementSkinName: String? = value.replacementSkinName
        override val status: String = value.status
        override val isClassified: Boolean = value.isClassified
    }

    data class Remote(
        val entry: DownlinkRepositoryEntry,
        val isDownloading: Boolean = false
    ) : ScriptListItem {
        override val key: String = "remote:${entry.id}"
        override val heroName: String = entry.heroName
        override val heroIcon: String? = entry.heroIcon
        override val originalSkinName: String = entry.originalSkinName
        override val replacementSkinName: String = entry.replacementSkinName
        override val status: String = InstallationStatus.NOT_INSTALLED
        override val isClassified: Boolean = true
    }
}

data class ZipPasswordPrompt(
    val zipUri: Uri,
    val errorMessage: String? = null
)

data class SkinReplacementGroup(
    val key: String,
    val title: String,
    val scripts: List<ScriptListItem>
) {
    val count: Int
        get() = scripts.size
}

data class SkinReplacementSection(
    val key: String,
    val title: String,
    val scripts: List<ScriptListItem>,
    val isExpanded: Boolean
) {
    val count: Int
        get() = scripts.size
}

data class HeroScriptGroup(
    val key: String,
    val title: String,
    val heroIcon: String? = null,
    val skinReplacementGroups: List<SkinReplacementGroup> = emptyList(),
    val flatScripts: List<ScriptListItem> = emptyList(),
    val isFlat: Boolean = false,
    val hasInstalledScript: Boolean = false,
    val hasReplacedScript: Boolean = false
) {
    val count: Int
        get() = if (isFlat) flatScripts.size else skinReplacementGroups.sumOf { it.count }
}

data class HeroScriptSection(
    val key: String,
    val title: String,
    val heroIcon: String? = null,
    val skinReplacementSections: List<SkinReplacementSection> = emptyList(),
    val flatScripts: List<ScriptListItem> = emptyList(),
    val isFlat: Boolean = false,
    val hasInstalledScript: Boolean = false,
    val hasReplacedScript: Boolean = false,
    val isExpanded: Boolean
) {
    val count: Int
        get() = if (isFlat) flatScripts.size else skinReplacementSections.sumOf { it.count }
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScriptListViewModel @Inject constructor(
    private val repository: ScriptRepository,
    private val importScriptUseCase: ImportScriptUseCase,
    private val restoreScriptUseCase: RestoreScriptUseCase,
    private val reinstallReplacedScriptsUseCase: ReinstallReplacedScriptsUseCase,
    private val userSelectionManager: UserSelectionManager,
    private val fetchHeroCatalogUseCase: FetchHeroCatalogUseCase,
    private val verifyInstalledScriptsUseCase: VerifyInstalledScriptsUseCase,
    private val downlinkRepositoryDataSource: DownlinkRepositoryDataSource,
    private val downloadDownlinkScriptUseCase: DownloadDownlinkScriptUseCase
) : ViewModel() {

    private var didInitializeExpandedSections = false

    val eligibleUserIds: StateFlow<List<Int>> = userSelectionManager.eligibleUserIds

    private val _expandedSectionKeys = MutableStateFlow<Set<String>>(emptySet())
    val expandedSectionKeys: StateFlow<Set<String>> = _expandedSectionKeys.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val activeUserId: StateFlow<Int> = userSelectionManager.activeUserId

    private val scripts: StateFlow<List<SkinScript>> = repository.getAllScripts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val heroes: StateFlow<List<Hero>> = repository.getAllHeroes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _downlinkEntries = MutableStateFlow<List<DownlinkRepositoryEntry>>(emptyList())
    private val _downlinkError = MutableStateFlow<String?>(null)
    val downlinkError: StateFlow<String?> = _downlinkError.asStateFlow()

    private val _downloadingDownlinkIds = MutableStateFlow<Set<String>>(emptySet())

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
                        heroIcon = hero?.heroIcon,
                        originalSkinName = originalSkin?.name,
                        replacementSkinName = replacementSkin?.name
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val replacedScriptsCount: StateFlow<Int> = scriptsWithStatus
        .map { items ->
            items.count { it.status == InstallationStatus.REPLACED }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val scriptListItems: StateFlow<List<ScriptListItem>> = combine(
        scriptsWithStatus,
        _downlinkEntries,
        _downloadingDownlinkIds
    ) { localScripts, downlinkEntries, downloadingIds ->
        val localItems = localScripts.map { ScriptListItem.Local(it) }
        val localKeys = localScripts.mapNotNull { it.classificationKey() }.toSet()
        val remoteItems = downlinkEntries
            .filterNot { it.classificationKey() in localKeys }
            .map { entry ->
                ScriptListItem.Remote(
                    entry = entry,
                    isDownloading = entry.id in downloadingIds
                )
            }
        localItems + remoteItems
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val heroScriptGroups: StateFlow<List<HeroScriptGroup>> = scriptListItems
        .map(::buildHeroScriptGroups)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val heroScriptSections: StateFlow<List<HeroScriptSection>> = combine(
        heroScriptGroups,
        expandedSectionKeys,
        searchQuery
    ) { groups, expandedKeys, searchQuery ->
        val normalizedQuery = searchQuery.trim()
        groups
            .asSequence()
            .filter { group ->
                normalizedQuery.isBlank() || group.title.contains(normalizedQuery, ignoreCase = true)
            }
            .map { group ->
            HeroScriptSection(
                key = group.key,
                title = group.title,
                heroIcon = group.heroIcon,
                skinReplacementSections = group.skinReplacementGroups.map { replGroup ->
                    SkinReplacementSection(
                        key = replGroup.key,
                        title = replGroup.title,
                        scripts = replGroup.scripts,
                        isExpanded = replGroup.key in expandedKeys
                    )
                },
                flatScripts = group.flatScripts,
                isFlat = group.isFlat,
                hasInstalledScript = group.hasInstalledScript,
                hasReplacedScript = group.hasReplacedScript,
                isExpanded = group.key in expandedKeys
            )
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isReinstallingReplaced = MutableStateFlow(false)
    val isReinstallingReplaced: StateFlow<Boolean> = _isReinstallingReplaced.asStateFlow()

    private val _reinstallReplacedMessage = MutableStateFlow<String?>(null)
    val reinstallReplacedMessage: StateFlow<String?> = _reinstallReplacedMessage.asStateFlow()

    private val canReinstallReplacedBase: StateFlow<Boolean> = combine(
        replacedScriptsCount,
        activeUserId,
        eligibleUserIds,
        isReinstallingReplaced,
        isImporting
    ) { replacedCount, selectedUserId, userIds, isReinstalling, isImporting ->
        !isReinstalling &&
            !isImporting &&
            replacedCount > 0 &&
            selectedUserId in userIds
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canReinstallReplaced: StateFlow<Boolean> = combine(
        canReinstallReplacedBase,
        isRefreshing
    ) { canRunBase, isRefreshing ->
        canRunBase && !isRefreshing
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _zipPasswordPrompt = MutableStateFlow<ZipPasswordPrompt?>(null)
    val zipPasswordPrompt: StateFlow<ZipPasswordPrompt?> = _zipPasswordPrompt.asStateFlow()

    private val _pendingClassificationScriptId = MutableStateFlow<Long?>(null)
    val pendingClassificationScriptId: StateFlow<Long?> = _pendingClassificationScriptId.asStateFlow()

    private val _pendingOpenScriptId = MutableStateFlow<Long?>(null)
    val pendingOpenScriptId: StateFlow<Long?> = _pendingOpenScriptId.asStateFlow()

    init {
        userSelectionManager.observeFileService(viewModelScope)
        observeHeroSections()
        viewModelScope.launch {
            if (repository.getHeroCount() == 0) {
                fetchHeroCatalogUseCase.execute()
            }
            fetchDownlinkScripts()
        }
    }

    private fun observeHeroSections() {
        viewModelScope.launch {
            heroScriptGroups.collect { groups ->
                val allKeys = linkedSetOf<String>()
                groups.forEach { group ->
                    allKeys.add(group.key)
                    group.skinReplacementGroups.forEach { replGroup ->
                        allKeys.add(replGroup.key)
                    }
                }
                val currentExpandedKeys = _expandedSectionKeys.value
                val nextExpandedKeys = when {
                    allKeys.isEmpty() -> emptySet()
                    !didInitializeExpandedSections -> {
                        didInitializeExpandedSections = true
                        emptySet()
                    }

                    else -> buildSet {
                        currentExpandedKeys.filterTo(this) { it in allKeys }
                    }
                }

                if (nextExpandedKeys != currentExpandedKeys) {
                    _expandedSectionKeys.value = nextExpandedKeys
                }
            }
        }
    }

    fun selectActiveUser(userId: Int) {
        userSelectionManager.selectUser(userId)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSection(sectionKey: String) {
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
            result.onSuccess { script ->
                _pendingClassificationScriptId.value = script.id
            }
            result.onFailure { e ->
                _importError.value = e.message ?: "Import failed"
            }
            _isImporting.value = false
        }
    }

    fun importZip(zipUri: Uri) {
        importZipInternal(zipUri, password = null)
    }

    fun refreshInstalledScripts() {
        if (_isImporting.value || _isRefreshing.value) {
            return
        }

        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                verifyInstalledScriptsUseCase.execute()
                fetchDownlinkScripts()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun downloadDownlinkScript(entry: DownlinkRepositoryEntry) {
        if (entry.id in _downloadingDownlinkIds.value) {
            return
        }

        viewModelScope.launch {
            _importError.value = null
            _downloadingDownlinkIds.value = _downloadingDownlinkIds.value + entry.id
            try {
                val result = downloadDownlinkScriptUseCase.execute(entry)
                result.onSuccess { script ->
                    _pendingOpenScriptId.value = script.id
                }.onFailure { error ->
                    _importError.value = error.message ?: "Downlink download failed"
                }
            } finally {
                _downloadingDownlinkIds.value = _downloadingDownlinkIds.value - entry.id
            }
        }
    }

    fun reinstallAllReplaced() {
        viewModelScope.launch {
            if (_isReinstallingReplaced.value) {
                return@launch
            }

            val selectedUserId = activeUserId.value
            if (selectedUserId !in eligibleUserIds.value) {
                _reinstallReplacedMessage.value =
                    "No active Mobile Legends user is available for reinstall"
                return@launch
            }

            _reinstallReplacedMessage.value = null
            _isReinstallingReplaced.value = true

            try {
                userSelectionManager.selectUser(selectedUserId)
                val result = reinstallReplacedScriptsUseCase.execute(selectedUserId)
                _reinstallReplacedMessage.value = formatReinstallReplacedMessage(result, selectedUserId)
            } catch (e: Exception) {
                _reinstallReplacedMessage.value = e.message ?: "Reinstall failed"
            } finally {
                _isReinstallingReplaced.value = false
            }
        }
    }

    fun retryZipWithPassword(password: String) {
        val prompt = _zipPasswordPrompt.value ?: return
        importZipInternal(prompt.zipUri, password.toCharArray())
    }

    fun dismissZipPasswordPrompt() {
        _zipPasswordPrompt.value = null
    }

    fun clearReinstallReplacedMessage() {
        _reinstallReplacedMessage.value = null
    }

    fun clearDownlinkError() {
        _downlinkError.value = null
    }

    private suspend fun fetchDownlinkScripts() {
        val heroList = heroes.value.ifEmpty { repository.getAllHeroesOnce() }
        if (heroList.isEmpty()) {
            return
        }

        val result = downlinkRepositoryDataSource.fetchScripts(heroList)
        result.onSuccess { entries ->
            _downlinkEntries.value = entries
            _downlinkError.value = null
        }.onFailure { error ->
            _downlinkError.value = error.message ?: "Failed to fetch Downlink repository"
        }
    }

    private fun importZipInternal(zipUri: Uri, password: CharArray?) {
        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null

            val result = importScriptUseCase.executeZip(zipUri, password)
            result.onSuccess { script ->
                _zipPasswordPrompt.value = null
                _pendingClassificationScriptId.value = script.id
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
                val latestInstallation = repository.getLatestInstallation(script.id, userSelectionManager.activeUserId.value)
                if (latestInstallation == null || latestInstallation.status != InstallationStatus.INSTALLED) {
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

    fun dismissPendingClassification() {
        _pendingClassificationScriptId.value = null
    }

    fun dismissPendingOpenScript() {
        _pendingOpenScriptId.value = null
    }

    private fun formatReinstallReplacedMessage(
        result: ReinstallReplacedScriptsResult,
        userId: Int
    ): String {
        if (result.totalCandidates == 0) {
            return "No replaced scripts to reinstall for User $userId"
        }

        if (result.failures.isEmpty()) {
            return "Reinstalled ${result.reinstalledCount} scripts for User $userId"
        }

        val failureSummary = result.failures.joinToString(separator = "; ") { failure ->
            "${failure.scriptName} (${failure.message})"
        }
        return "Reinstalled ${result.reinstalledCount} of ${result.totalCandidates} scripts for User $userId. Failed: $failureSummary"
    }

    private fun buildHeroScriptGroups(items: List<ScriptListItem>): List<HeroScriptGroup> {
        if (items.isEmpty()) {
            return emptyList()
        }

        return items
            .groupBy { item -> item.heroName ?: UNCATEGORIZED_SECTION_KEY }
            .map { (heroKey, scriptsForHero) ->
                if (heroKey == UNCATEGORIZED_SECTION_KEY) {
                    HeroScriptGroup(
                        key = heroKey,
                        title = "Uncategorized",
                        heroIcon = null,
                        flatScripts = scriptsForHero,
                        isFlat = true,
                        hasInstalledScript = scriptsForHero.any {
                            it.status == InstallationStatus.INSTALLED
                        },
                        hasReplacedScript = scriptsForHero.any {
                            it.status == InstallationStatus.REPLACED
                        }
                    )
                } else {
                    val replacementGroups = scriptsForHero
                        .groupBy { it.replacementSkinName ?: UNKNOWN_REPLACEMENT_KEY }
                        .map { (replKey, scriptsForReplacement) ->
                            SkinReplacementGroup(
                                key = "$heroKey$REPLACEMENT_KEY_SEPARATOR$replKey",
                                title = scriptsForReplacement.first().replacementSkinName
                                    ?: "Unknown",
                                scripts = scriptsForReplacement
                            )
                        }
                    HeroScriptGroup(
                        key = heroKey,
                        title = heroKey,
                        heroIcon = scriptsForHero.firstOrNull()?.heroIcon,
                        skinReplacementGroups = replacementGroups,
                        isFlat = false,
                        hasInstalledScript = scriptsForHero.any {
                            it.status == InstallationStatus.INSTALLED
                        },
                        hasReplacedScript = scriptsForHero.any {
                            it.status == InstallationStatus.REPLACED
                        }
                    )
                }
            }
    }

    private fun ScriptWithStatus.classificationKey(): String? {
        val hero = heroName ?: return null
        val original = originalSkinName ?: return null
        val replacement = replacementSkinName ?: return null
        return classificationKey(hero, original, replacement)
    }

    private fun DownlinkRepositoryEntry.classificationKey(): String =
        classificationKey(heroName, originalSkinName, replacementSkinName)

    private fun classificationKey(hero: String, original: String, replacement: String): String =
        listOf(hero, original, replacement)
            .joinToString("|") { value ->
                value.trim().lowercase().replace(Regex("\\s+"), " ")
            }

}
