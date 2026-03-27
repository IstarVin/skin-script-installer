package com.istarvin.skinscriptinstaller.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.data.update.AppUpdateManager
import com.istarvin.skinscriptinstaller.domain.CheckForUpdateUseCase
import com.istarvin.skinscriptinstaller.domain.ReleaseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpdateAvailable(
        val version: String,
        val releaseNotes: String,
        val releaseUrl: String,
        val apkUrl: String?
    ) : UpdateState()
    data class Downloading(val version: String) : UpdateState()
    data object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
}

sealed interface UpdateEvent {
    data class OpenReleasePage(val releaseUrl: String) : UpdateEvent
    data object OpenUnknownAppSourcesSettings : UpdateEvent
    data class LaunchInstaller(val apkUri: Uri) : UpdateEvent
}

@HiltViewModel
class UpdateCheckViewModel @Inject constructor(
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    private val _events = MutableSharedFlow<UpdateEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UpdateEvent> = _events.asSharedFlow()
    private var pendingInstallUri: Uri? = null

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        if (_updateState.value == UpdateState.Checking) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val result = checkForUpdateUseCase.execute()
            _updateState.value = result.fold(
                onSuccess = { releaseInfo: ReleaseInfo? ->
                    if (releaseInfo != null) {
                        UpdateState.UpdateAvailable(
                            version = releaseInfo.version,
                            releaseNotes = releaseInfo.releaseNotes,
                            releaseUrl = releaseInfo.releaseUrl,
                            apkUrl = releaseInfo.apkUrl
                        )
                    } else {
                        UpdateState.UpToDate
                    }
                },
                onFailure = { UpdateState.Error(it.message ?: "Update check failed") }
            )
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun startUpdate() {
        val state = _updateState.value as? UpdateState.UpdateAvailable ?: return
        val apkUrl = state.apkUrl
        if (apkUrl.isNullOrBlank()) {
            _events.tryEmit(UpdateEvent.OpenReleasePage(state.releaseUrl))
            _updateState.value = UpdateState.Idle
            return
        }

        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(version = state.version)
            val result = appUpdateManager.downloadUpdate(apkUrl, state.version)
            result.fold(
                onSuccess = { apkUri ->
                    pendingInstallUri = apkUri
                    launchInstallerOrRequestPermission(apkUri)
                },
                onFailure = {
                    _updateState.value = UpdateState.Error(it.message ?: "Failed to download update")
                }
            )
        }
    }

    fun resumeInstallAfterPermissionCheck() {
        val apkUri = pendingInstallUri ?: return
        if (appUpdateManager.canRequestPackageInstalls()) {
            _events.tryEmit(UpdateEvent.LaunchInstaller(apkUri))
            clearPendingInstall()
        } else {
            _updateState.value = UpdateState.Error(
                "Allow app installs for Skin Script Installer, then try again"
            )
        }
    }

    fun onInstallLaunchFailed(message: String) {
        _updateState.value = UpdateState.Error(message)
    }

    private fun launchInstallerOrRequestPermission(apkUri: Uri) {
        if (appUpdateManager.canRequestPackageInstalls()) {
            _events.tryEmit(UpdateEvent.LaunchInstaller(apkUri))
            clearPendingInstall()
        } else {
            _updateState.value = UpdateState.Idle
            _events.tryEmit(UpdateEvent.OpenUnknownAppSourcesSettings)
        }
    }

    private fun clearPendingInstall() {
        pendingInstallUri = null
        _updateState.value = UpdateState.Idle
    }
}
