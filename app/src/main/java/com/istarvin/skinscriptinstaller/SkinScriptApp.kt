package com.istarvin.skinscriptinstaller

import android.app.Application
import com.istarvin.skinscriptinstaller.data.update.UpdateNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SkinScriptApp : Application() {

	override fun onCreate() {
		super.onCreate()
		UpdateNotifications.createChannel(this)
	}
}

