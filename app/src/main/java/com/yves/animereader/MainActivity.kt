package com.yves.animereader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    // Capture d'écran (MediaProjection)
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val intent = Intent(this, OverlayService::class.java).apply {
                    putExtra("RESULT_CODE", result.resultCode)
                    putExtra("DATA", data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }
    }

    // Demande de permission pour les notifications (Android 13+)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        checkOverlayAndStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Bienvenue dans AnimeReader", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { checkPermissionsAndStart() },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Text("Démarrer AnimeReader")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { stopAnimeReaderService() },
                            modifier = Modifier.fillMaxWidth(0.8f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Arrêter l'application")
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkOverlayAndStart()
    }

    private fun checkOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Veuillez autoriser l'affichage par-dessus les autres applications.", Toast.LENGTH_LONG).show()
        } else {
            startScreenCapture()
        }
    }

    private fun startScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopAnimeReaderService() {
        stopService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Demande d'arrêt envoyée.", Toast.LENGTH_SHORT).show()
    }
}
