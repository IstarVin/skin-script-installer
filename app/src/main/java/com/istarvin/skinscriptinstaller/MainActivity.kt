package com.istarvin.skinscriptinstaller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.istarvin.skinscriptinstaller.domain.VerifyInstalledScriptsUseCase
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import com.istarvin.skinscriptinstaller.ui.navigation.AppNavHost
import com.istarvin.skinscriptinstaller.ui.theme.SkinScriptInstallerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shizukuManager: ShizukuManager

    @Inject
    lateinit var verifyInstalledScriptsUseCase: VerifyInstalledScriptsUseCase

    private var verifyInstalledScriptsJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        shizukuManager.init()

        setContent {
            SkinScriptInstallerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavHost(navController = navController)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        verifyInstalledScriptsJob?.cancel()
        verifyInstalledScriptsJob = lifecycleScope.launch {
            shizukuManager.fileService.filterNotNull().first()
            verifyInstalledScriptsUseCase.execute()
        }
    }

    override fun onStop() {
        verifyInstalledScriptsJob?.cancel()
        verifyInstalledScriptsJob = null
        super.onStop()
    }

    override fun onDestroy() {
        verifyInstalledScriptsJob?.cancel()
        super.onDestroy()
        shizukuManager.destroy()
    }
}