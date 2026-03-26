package com.istarvin.skinscriptinstaller.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.domain.FetchHeroCatalogUseCase
import com.istarvin.skinscriptinstaller.domain.backup.ExportAppDataBackupUseCase
import com.istarvin.skinscriptinstaller.domain.backup.ImportAppDataBackupUseCase
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val shizukuManager: ShizukuManager,
    private val exportAppDataBackupUseCase: ExportAppDataBackupUseCase,
    private val importAppDataBackupUseCase: ImportAppDataBackupUseCase,
    private val fetchHeroCatalogUseCase: FetchHeroCatalogUseCase
) : ViewModel() {

    val isShizukuAvailable: StateFlow<Boolean> = shizukuManager.isShizukuAvailable
    val isPermissionGranted: StateFlow<Boolean> = shizukuManager.isPermissionGranted
    val isServiceBound: StateFlow<Boolean> = shizukuManager.isServiceBound

    private val _isBackupOperationRunning = MutableStateFlow(false)
    val isBackupOperationRunning: StateFlow<Boolean> = _isBackupOperationRunning.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _isRefreshingCatalog = MutableStateFlow(false)
    val isRefreshingCatalog: StateFlow<Boolean> = _isRefreshingCatalog.asStateFlow()

    private val _catalogRefreshMessage = MutableStateFlow<String?>(null)
    val catalogRefreshMessage: StateFlow<String?> = _catalogRefreshMessage.asStateFlow()

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
}

