package com.example.bluetooth_project.utils

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun hideStatusBar(context: Activity) {
    val windowInsetsController =
        WindowCompat.getInsetsController(context.window, context.window.decorView)
    windowInsetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    context.window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
        // You can hide the caption bar even when the other system bars are visible.
        // To account for this, explicitly check the visibility of navigationBars()
        // and statusBars() rather than checking the visibility of systemBars().
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        view.onApplyWindowInsets(windowInsets)
    }
    // context.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
    // context. actionBar?.hide()
}