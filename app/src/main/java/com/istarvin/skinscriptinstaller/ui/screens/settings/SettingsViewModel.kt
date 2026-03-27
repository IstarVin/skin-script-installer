package com.istarvin.skinscriptinstaller.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.domain.FetchHeroCatalogUseCase
import com.istarvin.skinscriptinstaller.domain.RestoreAllScriptsResult
import com.istarvin.skinscriptinstaller.domain.RestoreAllScriptsUseCase
import com.istarvin.skinscriptinstaller.domain.UserSelectionManager
import com.istarvin.skinscriptinstaller.domain.backup.ExportAppDataBackupUseCase
import com.istarvin.skinscriptinstaller.domain.backup.ImportAppDataBackupUseCase
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
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

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModel @Inject constructor(
    private val repository: ScriptRepository,
    private val shizukuManager: ShizukuManager,
    private val userSelectionManager: UserSelectionManager,
    private val restoreAllScriptsUseCase: RestoreAllScriptsUseCase,
    private val exportAppDataBackupUseCase: ExportAppDataBackupUseCase,
    private val importAppDataBackupUseCase: ImportAppDataBackupUseCase,
    private val fetchHeroCatalogUseCase: FetchHeroCatalogUseCase
) : ViewModel() {

    val isShizukuAvailable: StateFlow<Boolean> = shizukuManager.isShizukuAvailable
    val isPermissionGranted: StateFlow<Boolean> = shizukuManager.isPermissionGranted
    val isServiceBound: StateFlow<Boolean> = shizukuManager.isServiceBound
    val activeUserId: StateFlow<Int> = userSelectionManager.activeUserId
    val eligibleUserIds: StateFlow<List<Int>> = userSelectionManager.eligibleUserIds

    private val _isBackupOperationRunning = MutableStateFlow(false)
    val isBackupOperationRunning: StateFlow<Boolean> = _isBackupOperationRunning.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _isRefreshingCatalog = MutableStateFlow(false)
    val isRefreshingCatalog: StateFlow<Boolean> = _isRefreshingCatalog.asStateFlow()

    private val _catalogRefreshMessage = MutableStateFlow<String?>(null)
    val catalogRefreshMessage: StateFlow<String?> = _catalogRefreshMessage.asStateFlow()

    private val _isRestoringAll = MutableStateFlow(false)
    val isRestoringAll: StateFlow<Boolean> = _isRestoringAll.asStateFlow()

    private val _restoreAllMessage = MutableStateFlow<String?>(null)
    val restoreAllMessage: StateFlow<String?> = _restoreAllMessage.asStateFlow()

    val restoreAllCount: StateFlow<Int> = activeUserId
        .flatMapLatest { selectedUserId ->
            repository.getLatestInstallations(selectedUserId)
        }
        .map { installations -> installations.count { it.status == "installed" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val canRestoreAll: StateFlow<Boolean> = combine(
        restoreAllCount,
        activeUserId,
        eligibleUserIds,
        isRestoringAll
    ) { restoreCount, selectedUserId, userIds, isRestoring ->
        !isRestoring && restoreCount > 0 && selectedUserId in userIds
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        userSelectionManager.observeFileService(viewModelScope)
    }

    fun requestPermission() {
        shizukuManager.requestPermission()
    }

    fun bindService() {
        shizukuManager.bindService()
    }

    fun refreshStatus() {
        shizukuManager.checkPermission()
    }

    fun exportBackup(outputUri: Uri) {
        viewModelScope.launch {
            _isBackupOperationRunning.value = true
            _backupMessage.value = null

            val result = exportAppDataBackupUseCase.execute(outputUri)
            _backupMessage.value = result
                .fold(
                    onSuccess = { "Backup export completed" },
                    onFailure = { it.message ?: "Backup export failed" }
                )

            _isBackupOperationRunning.value = false
        }
    }

    fun restoreBackup(inputUri: Uri) {
        viewModelScope.launch {
            _isBackupOperationRunning.value = true
            _backupMessage.value = null

            val result = importAppDataBackupUseCase.execute(inputUri)
            _backupMessage.value = result
                .fold(
                    onSuccess = { "Backup restore completed" },
                    onFailure = { it.message ?: "Backup restore failed" }
                )

            _isBackupOperationRunning.value = false
        }
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    fun restoreAll() {
        viewModelScope.launch {
            if (_isRestoringAll.value) return@launch

            val selectedUserId = activeUserId.value
            if (selectedUserId !in eligibleUserIds.value) {
                _restoreAllMessage.value = "No active Mobile Legends user is available for restore"
                return@launch
            }

            _restoreAllMessage.value = null
            _isRestoringAll.value = true

            try {
                val result = restoreAllScriptsUseCase.execute(selectedUserId)
                _restoreAllMessage.value = formatRestoreAllMessage(result, selectedUserId)
            } catch (e: Exception) {
                _restoreAllMessage.value = e.message ?: "Restore all failed"
            } finally {
                _isRestoringAll.value = false
            }
        }
    }

    fun clearRestoreAllMessage() {
        _restoreAllMessage.value = null
    }

    fun refreshHeroCatalog() {
        viewModelScope.launch {
            _isRefreshingCatalog.value = true
            _catalogRefreshMessage.value = null
            val result = fetchHeroCatalogUseCase.execute()
            _catalogRefreshMessage.value = result.fold(
                onSuccess = { count -> "Synced $count heroes" },
                onFailure = { it.message ?: "Failed to fetch hero catalog" }
            )
            _isRefreshingCatalog.value = false
        }
    }

    fun clearCatalogRefreshMessage() {
        _catalogRefreshMessage.value = null
    }

    private fun formatRestoreAllMessage(
        result: RestoreAllScriptsResult,
        userId: Int
    ): String {
        if (result.totalCandidates == 0) {
            return "No installed scripts to restore for User $userId"
        }

        if (result.failures.isEmpty()) {
            return "Restored ${result.restoredCount} scripts for User $userId"
        }

        val failureSummary = result.failures.joinToString(separator = "; ") { failure ->
            "${failure.scriptName} (${failure.message})"
        }
        return "Restored ${result.restoredCount} of ${result.totalCandidates} scripts for User $userId. Failed: $failureSummary"
    }
}
