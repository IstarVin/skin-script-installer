package com.istarvin.skinscriptinstaller.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val importAppDataBackupUseCase: ImportAppDataBackupUseCase
) : ViewModel() {

    val isShizukuAvailable: StateFlow<Boolean> = shizukuManager.isShizukuAvailable
    val isPermissionGranted: StateFlow<Boolean> = shizukuManager.isPermissionGranted
    val isServiceBound: StateFlow<Boolean> = shizukuManager.isServiceBound

    private val _isBackupOperationRunning = MutableStateFlow(false)
    val isBackupOperationRunning: StateFlow<Boolean> = _isBackupOperationRunning.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

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
}

