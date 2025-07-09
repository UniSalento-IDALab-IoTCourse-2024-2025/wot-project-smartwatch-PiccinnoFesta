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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant

class SensorService : Service(), SensorEventListener {

    private val CHANNEL_ID = "SensorServiceChannel"
    private val TAG = "SensorService"

    private lateinit var sensorManager: SensorManager
    private var hrSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var accelSensor: Sensor? = null

    // ultimi valori letti
    @Volatile private var pendingHr: Float     = 0f
    @Volatile private var pendingGyro: Triple<Float,Float,Float> = Triple(0f,0f,0f)
    @Volatile private var pendingAccel: Triple<Float,Float,Float> = Triple(0f,0f,0f)

    private val intervalMs = 2000L   // periodico

    private val handler = Handler(Looper.getMainLooper())
    private val sampler = object : Runnable {
        override fun run() {
            sendSnapshot()
            handler.postDelayed(this, intervalMs)
        }
    }

    companion object {
        const val ACTION_HR      = "com.example.iotprojectwatch1.HR_UPDATE"
        const val EXTRA_HR       = "heart_rate"
        const val ACTION_GYRO    = "com.example.iotprojectwatch1.GYRO_UPDATE"
        const val EXTRA_GYRO_X   = "gyro_x"
        const val EXTRA_GYRO_Y   = "gyro_y"
        const val EXTRA_GYRO_Z   = "gyro_z"
        const val ACTION_ACCEL   = "com.example.iotprojectwatch1.ACCEL_UPDATE"
        const val EXTRA_ACCEL_X  = "accel_x"
        const val EXTRA_ACCEL_Y  = "accel_y"
        const val EXTRA_ACCEL_Z  = "accel_z"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        hrSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        gyroSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        Log.d(TAG, "Sensori trovati — HR: $hrSensor, GYRO: $gyroSensor, ACCEL: $accelSensor")
        if (hrSensor==null && gyroSensor==null && accelSensor==null) {
            Log.e(TAG, "Nessun sensore disponibile, stop.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // foreground notification
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sensor Service")
            .setContentText("Raccolta dati sensori attiva")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        startForeground(1, notification)

        // registro i listener
        val usPeriod = (1_000_000.0 / 200.0).toInt() // 200 campioni/s → 5 000 µs
        //volessi spingere al massimo potrei usare sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        hrSensor?.let {
            sensorManager.registerListener(this, it, usPeriod)
            Log.d(TAG, "Listener HEART_RATE registrato")
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it,usPeriod)
            Log.d(TAG, "Listener GYROSCOPE registrato")
        }
        accelSensor?.let {
            sensorManager.registerListener(this, it, usPeriod)
            Log.d(TAG, "Listener ACCELEROMETER registrato")
        }

        // avvio il sampler periodico
        handler.postDelayed(sampler, intervalMs)

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(sampler)
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Servizio distrutto.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        // aggiorno solo il valore, senza inviare
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                pendingHr = event.values[0]
                sendHrBroadcast(pendingHr)
            }
            Sensor.TYPE_GYROSCOPE -> {
                pendingGyro = Triple(event.values[0], event.values[1], event.values[2])
                sendGyroBroadcast(pendingGyro.first, pendingGyro.second, pendingGyro.third)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                pendingAccel = Triple(event.values[0], event.values[1], event.values[2])
                sendAccelBroadcast(pendingAccel.first, pendingAccel.second, pendingAccel.third)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Accuracy ${sensor?.type}: $accuracy")
    }

    // invia snapshot periodico con i tre sensori
    private fun sendSnapshot() {
        val nowIso = Instant.now().toString()
        val (gx, gy, gz) = pendingGyro
        val (ax, ay, az) = pendingAccel
        val hrVal        = pendingHr

        val json = """
          {
            "timestamp":"$nowIso",
            "hr":$hrVal,
            "gyro": { "x":$gx, "y":$gy, "z":$gz },
            "accel":{ "x":$ax, "y":$ay, "z":$az }
          }
        """.trimIndent()

        Log.d(TAG, "Snapshot JSON pronto per invio: $json")
        postJson(json)
    }

    // --- BROADCASTS (invariati) ---
    private fun sendHrBroadcast(hr: Float) {
        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(ACTION_HR).putExtra(EXTRA_HR, hr))
    }
    private fun sendGyroBroadcast(x: Float,y: Float,z: Float) {
        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(ACTION_GYRO)
                .putExtra(EXTRA_GYRO_X, x)
                .putExtra(EXTRA_GYRO_Y, y)
                .putExtra(EXTRA_GYRO_Z, z))
    }
    private fun sendAccelBroadcast(x: Float,y: Float,z: Float) {
        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(ACTION_ACCEL)
                .putExtra(EXTRA_ACCEL_X, x)
                .putExtra(EXTRA_ACCEL_Y, y)
                .putExtra(EXTRA_ACCEL_Z, z))
    }

    // --- HTTP POST identico ---
    private fun postJson(json: String) {
        val mt = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mt)
        OkHttpClient().newCall(
            Request.Builder()
                .url("http://192.168.1.39:3000/api/data")
                .post(body)
                .build()
        ).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "HTTP onFailure: ${e.message}", e)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                Log.d(TAG, "HTTP onResponse code=${response.code}")
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                "Sensor Service",
                NotificationManager.IMPORTANCE_LOW
            ).let {
                getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(it)
            }
        }
    }
}