package com.istarvin.skinscriptinstaller.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.data.update.UpdateDownloadStarter
import com.istarvin.skinscriptinstaller.data.update.UpdateDownloadStatus
import com.istarvin.skinscriptinstaller.data.update.UpdateDownloadTracker
import com.istarvin.skinscriptinstaller.domain.CheckForUpdateUseCase
import com.istarvin.skinscriptinstaller.domain.ReleaseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpdateAvailable(
        val version: String,
        val releaseNotes: String,
        val releaseUrl: String,
        val apkUrl: String?
    ) : UpdateState()
    data class Downloading(
        val version: String,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long? = null
    ) : UpdateState() {
        val progressFraction: Float?
            get() = totalBytes
                ?.takeIf { it > 0L }
                ?.let { bytesDownloaded.toFloat() / it.toFloat() }

        val progressPercent: Int?
            get() = totalBytes
                ?.takeIf { it > 0L }
                ?.let { ((bytesDownloaded * 100) / it).toInt().coerceIn(0, 100) }
    }

    data class Downloaded(val version: String) : UpdateState()
    data object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
}

sealed interface UpdateEvent {
    data class OpenReleasePage(val releaseUrl: String) : UpdateEvent
}

@HiltViewModel
class UpdateCheckViewModel @Inject constructor(
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val updateDownloadStarter: UpdateDownloadStarter,
    private val updateDownloadTracker: UpdateDownloadTracker
) : ViewModel() {

    private val _baseUpdateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = combine(
        _baseUpdateState,
        updateDownloadTracker.status
    ) { baseState, downloadStatus ->
        when (downloadStatus) {
            UpdateDownloadStatus.Idle -> baseState
            is UpdateDownloadStatus.Running -> UpdateState.Downloading(
                version = downloadStatus.version,
                bytesDownloaded = downloadStatus.bytesDownloaded,
                totalBytes = downloadStatus.totalBytes
            )

            is UpdateDownloadStatus.Completed -> UpdateState.Downloaded(downloadStatus.version)
            is UpdateDownloadStatus.Failed -> UpdateState.Error(downloadStatus.message)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UpdateState.Idle)
    private val _events = MutableSharedFlow<UpdateEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UpdateEvent> = _events.asSharedFlow()

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        if (_baseUpdateState.value == UpdateState.Checking) return

        when (updateDownloadTracker.status.value) {
            is UpdateDownloadStatus.Running -> return
            is UpdateDownloadStatus.Completed,
            is UpdateDownloadStatus.Failed -> {
                updateDownloadTracker.clear()
                _baseUpdateState.value = UpdateState.Idle
            }

            UpdateDownloadStatus.Idle -> Unit
        }

        viewModelScope.launch {
            _baseUpdateState.value = UpdateState.Checking
            val result = checkForUpdateUseCase.execute()
            _baseUpdateState.value = result.fold(
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
        updateDownloadTracker.clear()
        _baseUpdateState.value = UpdateState.Idle
    }

    fun startUpdate() {
        val state = updateState.value as? UpdateState.UpdateAvailable ?: return
        val apkUrl = state.apkUrl
        if (apkUrl.isNullOrBlank()) {
            _events.tryEmit(UpdateEvent.OpenReleasePage(state.releaseUrl))
            _baseUpdateState.value = UpdateState.Idle
            return
        }

        _baseUpdateState.value = UpdateState.Downloading(version = state.version)
        val result = updateDownloadStarter.startDownload(apkUrl, state.version)
        result.onFailure {
            _baseUpdateState.value = UpdateState.Error(
                it.message ?: "Failed to start update download"
            )
        }
    }

    fun onNotificationPermissionDenied() {
        _baseUpdateState.value = UpdateState.Error(
            "Allow notifications to show update download progress"
        )
    }

    fun onInstallLaunchFailed(message: String) {
        _baseUpdateState.value = UpdateState.Error(message)
    }
}
