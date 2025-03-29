package com.deeplearningtitans.aikocompanionvc

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ProcessLifecycleOwner
import com.unity3d.player.UnityPlayerActivity

class MainActivity : ComponentActivity() {

    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register overlay permission launcher
        overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                startOverlayService()
                launchUnity()
            } else {
                Toast.makeText(this, "Overlay permission is required to run the app", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        // Request overlay permission if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            launchUnity()
        }

        // Register lifecycle observer
        val lifecycleObserver = AppLifecycleObserver(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    private fun launchUnity() {
        val intent = Intent(this, UnityPlayerActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, "Grant overlay permission for floating Tamagotchi", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlayPermissionLauncher.launch(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            startOverlayService() // Start overlay when app is minimized
        }
    }

    override fun onResume() {
        super.onResume()
        stopOverlayService() // Stop overlay when app is in foreground
    }

    private fun startOverlayService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FloatingTamagotchiService::class.java)
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, FloatingTamagotchiService::class.java)
        stopService(intent)
    }
}
