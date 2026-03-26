package com.istarvin.skinscriptinstaller.data.user

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class ActiveUserStore @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "active_user_prefs"
        private const val KEY_ACTIVE_USER_ID = "active_user_id"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeUserId = MutableStateFlow(prefs.getInt(KEY_ACTIVE_USER_ID, 0))
    val activeUserId: StateFlow<Int> = _activeUserId.asStateFlow()

    fun setActiveUser(userId: Int) {
        if (_activeUserId.value == userId) {
            return
        }
        _activeUserId.value = userId
        prefs.edit { putInt(KEY_ACTIVE_USER_ID, userId) }
    }
}
