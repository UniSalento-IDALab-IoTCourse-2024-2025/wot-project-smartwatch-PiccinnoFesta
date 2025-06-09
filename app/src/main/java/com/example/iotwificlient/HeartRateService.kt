package com.example.iotprojectwatch1.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager


import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException


class HeartRateService : Service(), SensorEventListener {

    private val CHANNEL_ID = "HeartRateServiceChannel"
    private val TAG = "HeartRateService"

    private var sensorManager: SensorManager? = null
    private var heartRateSensor: Sensor? = null

    private var lastHeartRate = 0f
    private var lastSentTimestamp = 0L

    companion object {
        const val ACTION_HEART_RATE_UPDATE = "com.example.iotprojectwatch1.HEART_RATE_UPDATE"
        const val EXTRA_HEART_RATE = "heart_rate"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartRateSensor == null) {
            Log.e(TAG, "Sensore di battito cardiaco non disponibile su questo dispositivo.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Heart Rate Service")
            .setContentText("Il servizio per il monitoraggio battito è attivo")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        startForeground(1, notification)

        heartRateSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Listener del sensore di battito cardiaco registrato.")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        Log.d(TAG, "Listener del sensore di battito cardiaco deregistrato.")
        Log.d(TAG, "Heart Rate Service distrutto.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val currentHeartRate = event.values[0]
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastSentTimestamp >= 2000) {
                lastHeartRate = currentHeartRate
                Log.i(TAG, "Battito cardiaco rilevato: $lastHeartRate BPM")

                sendHeartRateUpdateBroadcast(lastHeartRate)
                sendHeartRateToServer(lastHeartRate)

                lastSentTimestamp = currentTime
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Precisione sensore ${sensor?.name} cambiata a: $accuracy")
    }

    private fun sendHeartRateUpdateBroadcast(heartRate: Float) {
        val intent = Intent(ACTION_HEART_RATE_UPDATE).apply {
            putExtra(EXTRA_HEART_RATE, heartRate)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "Broadcast battito cardiaco inviato: $heartRate BPM")
    }

    private fun sendHeartRateToServer(heartRate: Float) {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val timestamp = java.time.Instant.now().toString()
        val jsonBody = """
        {
            "battito": $heartRate,
            "timestamp": "$timestamp"
        }
    """.trimIndent()

        val request = Request.Builder()
            .url("http://192.168.1.43:3000/api/data")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("API", "Errore di rete dal servizio: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("API", "Risposta dal servizio: ${response.body?.string()}")
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Heart Rate Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}