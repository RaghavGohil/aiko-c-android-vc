package com.deeplearningtitans.aikocompanionvc

import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AppLifecycleObserver(private val context: Context) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        // When the app is minimized, start the Tamagotchi overlay
        val intent = Intent(context, OverlayService::class.java)
        context.startService(intent)
    }

    override fun onStart(owner: LifecycleOwner) {
        // When the app comes to the foreground, stop the overlay
        val intent = Intent(context, OverlayService::class.java)
        context.stopService(intent)
    }
}
