package com.istarvin.skinscriptinstaller.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.istarvin.skinscriptinstaller.domain.CheckForUpdateUseCase
import com.istarvin.skinscriptinstaller.domain.ReleaseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpdateAvailable(
        val version: String,
        val releaseNotes: String,
        val releaseUrl: String
    ) : UpdateState()
    data object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@HiltViewModel
class UpdateCheckViewModel @Inject constructor(
    private val checkForUpdateUseCase: CheckForUpdateUseCase
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

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
                            releaseUrl = releaseInfo.releaseUrl
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
}
