package com.istarvin.skinscriptinstaller.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.istarvin.skinscriptinstaller.BuildConfig
import com.istarvin.skinscriptinstaller.IFileService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ShizukuManager"
        const val REQUEST_CODE_PERMISSION = 1001
    }

    private val _fileService = MutableStateFlow<IFileService?>(null)
    val fileService: StateFlow<IFileService?> = _fileService.asStateFlow()

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        _isShizukuAvailable.value = true
        checkPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        _isShizukuAvailable.value = false
        _isPermissionGranted.value = false
        _fileService.value = null
        _isServiceBound.value = false
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission result: granted=$granted")
            _isPermissionGranted.value = granted
            if (granted) {
                bindService()
            }
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "FileService connected")
            _fileService.value = IFileService.Stub.asInterface(service)
            _isServiceBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "FileService disconnected")
            _fileService.value = null
            _isServiceBound.value = false
        }
    }

    private var userServiceArgs: Shizuku.UserServiceArgs? = null

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        // Sync current state on startup in case Shizuku is already running.
        syncStartupState()
    }

    fun destroy() {
        unbindService()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun checkPermission() {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                _isPermissionGranted.value = false
                return
            }
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            _isPermissionGranted.value = granted

            // Auto-connect user service when permission already exists.
            if (granted && _isShizukuAvailable.value) {
                bindService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission", e)
            _isPermissionGranted.value = false
        }
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permission", e)
        }
    }

    fun bindService() {
        if (_isServiceBound.value) return
        if (!_isShizukuAvailable.value || !_isPermissionGranted.value) {
            Log.d(
                TAG,
                "Skipping bind: shizukuAvailable=${_isShizukuAvailable.value}, permissionGranted=${_isPermissionGranted.value}"
            )
            return
        }
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(
                    BuildConfig.APPLICATION_ID,
                    FileService::class.java.name
                )
            )
                .daemon(false)
                .processNameSuffix("file_service")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)

            userServiceArgs = args
            Shizuku.bindUserService(args, serviceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "Error binding service", e)
        }
    }

    private fun syncStartupState() {
        try {
            val binderAlive = Shizuku.pingBinder()
            _isShizukuAvailable.value = binderAlive
            if (binderAlive) {
                checkPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing startup state", e)
            _isShizukuAvailable.value = false
            _isPermissionGranted.value = false
        }
    }

    fun unbindService() {
        try {
            userServiceArgs?.let {
                Shizuku.unbindUserService(it, serviceConnection, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding service", e)
        }
        _fileService.value = null
        _isServiceBound.value = false
        userServiceArgs = null
    }
}

