package com.example.bluetooth_project

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@HiltAndroidApp
class BluetoothApplication : Application() {

    companion object {
        lateinit var bluetoothApplication: BluetoothApplication
            private set

        fun isDebug(): Boolean {
            return BuildConfig.DEBUG
        }
    }

    override fun onCreate() {
        super.onCreate()
        bluetoothApplication = this
    }
}