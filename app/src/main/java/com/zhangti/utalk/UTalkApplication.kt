package com.zhangti.utalk

import android.app.Application
import com.zhangti.utalk.settings.SettingsRepository

class UTalkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRepository.initialize(this)
    }
}
