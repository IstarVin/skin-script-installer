package com.istarvin.skinscriptinstaller.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    val isShizukuAvailable: StateFlow<Boolean> = shizukuManager.isShizukuAvailable
    val isPermissionGranted: StateFlow<Boolean> = shizukuManager.isPermissionGranted
    val isServiceBound: StateFlow<Boolean> = shizukuManager.isServiceBound

    fun requestPermission() {
        shizukuManager.requestPermission()
    }

    fun bindService() {
        shizukuManager.bindService()
    }

    fun refreshStatus() {
        shizukuManager.checkPermission()
    }
}

