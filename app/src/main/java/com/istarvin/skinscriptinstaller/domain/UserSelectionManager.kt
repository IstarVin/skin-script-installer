package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.data.user.ActiveUserStore
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared helper for managing Mobile Legends user selection.
 * Observes Shizuku file service changes and refreshes eligible user IDs.
 * Used by ViewModels that need user-scoped operations.
 */
class UserSelectionManager @Inject constructor(
    private val activeUserStore: ActiveUserStore,
    private val shizukuManager: ShizukuManager
) {
    private val _eligibleUserIds = MutableStateFlow<List<Int>>(emptyList())
    val eligibleUserIds: StateFlow<List<Int>> = _eligibleUserIds.asStateFlow()

    val activeUserId: StateFlow<Int> = activeUserStore.activeUserId

    fun observeFileService(scope: CoroutineScope) {
        scope.launch {
            shizukuManager.fileService.collect {
                refreshEligibleUsers()
            }
        }
    }

    fun refreshEligibleUsers() {
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

        val currentUserId = activeUserStore.activeUserId.value
        val nextActiveUser = when {
            detected.isEmpty() -> 0
            currentUserId in detected -> currentUserId
            0 in detected -> 0
            else -> detected.first()
        }
        activeUserStore.setActiveUser(nextActiveUser)
    }

    fun selectUser(userId: Int) {
        if (userId in _eligibleUserIds.value) {
            activeUserStore.setActiveUser(userId)
        }
    }
}
