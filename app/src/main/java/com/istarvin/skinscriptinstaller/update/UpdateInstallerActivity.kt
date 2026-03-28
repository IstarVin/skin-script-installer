package com.istarvin.skinscriptinstaller.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri

class UpdateInstallerActivity : ComponentActivity() {

    private var pendingApkUri: Uri? = null

    private val unknownSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apkUri = pendingApkUri
        if (apkUri != null && packageManager.canRequestPackageInstalls()) {
            launchInstaller(apkUri)
        } else {
            Toast.makeText(
                this,
                "Allow app installs for Skin Script Installer, then tap the notification again.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val apkUri = intent.getStringExtra(ExtraApkUri)?.toUri()
        if (apkUri == null) {
            finish()
            return
        }

        pendingApkUri = apkUri
        if (packageManager.canRequestPackageInstalls()) {
            launchInstaller(apkUri)
        } else {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:$packageName".toUri()
            )
            unknownSourcesLauncher.launch(permissionIntent)
        }
    }

    private fun launchInstaller(apkUri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(installIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No package installer was found", Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Toast.makeText(
                this,
                error.message ?: "Unable to launch package installer",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            finish()
        }
    }

    companion object {
        private const val ExtraApkUri = "extra_apk_uri"

        fun createIntent(context: Context, apkUri: Uri): Intent {
            return Intent(context, UpdateInstallerActivity::class.java).apply {
                putExtra(ExtraApkUri, apkUri.toString())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
}