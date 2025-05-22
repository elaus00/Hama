package com.example.hama

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HamaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
}