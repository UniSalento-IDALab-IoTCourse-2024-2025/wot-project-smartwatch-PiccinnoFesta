package com.example.iotwificlient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.iotprojectwatch1.presentation.HeartRateService
import com.example.iotwificlient.ui.theme.WearAppTheme
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

import android.Manifest


class MainActivity : AppCompatActivity() {

    private val REQUEST_BODY_SENSORS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Qui controlli e chiedi permessi
        checkAndStartHeartRateService()
    }

    private fun checkAndStartHeartRateService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), REQUEST_BODY_SENSORS)
        } else {
            startHeartRateService()
        }
    }

    private fun startHeartRateService() {
        val intent = Intent(this, HeartRateService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    // Qui gestisci la risposta della richiesta permessi
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BODY_SENSORS) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permesso concesso, avvia servizio
                startHeartRateService()
            } else {
                // Permesso negato, mostra messaggio o disabilita funzionalità
                Toast.makeText(this, "Permesso sensori necessario per il servizio", Toast.LENGTH_LONG).show()
            }
        }
    }
}