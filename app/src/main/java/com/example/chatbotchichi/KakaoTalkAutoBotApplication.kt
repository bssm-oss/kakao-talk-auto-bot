package com.example.kakaotalkautobot

import android.app.Application

class KakaoTalkAutoBotApplication : Application() {
    override fun onCreate() {
        AppSettings.applySavedThemeMode(this)
        super.onCreate()
    }
}
