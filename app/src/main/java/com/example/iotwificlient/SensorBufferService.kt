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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant

class SensorBufferService : Service(), SensorEventListener {

    private val CHANNEL_ID = "SensorServiceChannel"
    private val TAG = "SensorService"

    private lateinit var sensorManager: SensorManager
    private var hrSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var accelSensor: Sensor? = null

    // ultimi valori letti
    @Volatile private var pendingHr: Float = 0f
    @Volatile private var pendingGyro: Triple<Float,Float,Float> = Triple(0f,0f,0f)
    @Volatile private var pendingAccel: Triple<Float,Float,Float> = Triple(0f,0f,0f)

    private val intervalMs = 2000L      // ogni 2 s flush forzato
    private val targetBatchSize = 400   // invia non appena buffer raggiunge 400

    private val handler = Handler(Looper.getMainLooper())

    data class Snapshot(
        val timestamp: String,
        val hr: Float,
        val gyro: Triple<Float,Float,Float>,
        val accel: Triple<Float,Float,Float>
    )

    // Buffer thread-safe
    private val snapshotBuffer = mutableListOf<Snapshot>()
    private val bufferLock = Any()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        hrSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        gyroSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        Log.d(TAG, "Sensori — HR: $hrSensor, GYRO: $gyroSensor, ACCEL: $accelSensor")
        if (hrSensor==null && gyroSensor==null && accelSensor==null) {
            Log.e(TAG, "Nessun sensore disponibile, stop.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground notification
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sensor Service")
            .setContentText("Raccolta dati sensori attiva")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        startForeground(1, notification)

        // Registro listener a 200 Hz
        val usPeriod = (1_000_000.0 / 200.0).toInt()
        hrSensor?.let {
            sensorManager.registerListener(this, it, usPeriod)
            Log.d(TAG, "Listener HEART_RATE registrato")
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, usPeriod)
            Log.d(TAG, "Listener GYROSCOPE registrato")
        }
        accelSensor?.let {
            sensorManager.registerListener(this, it, usPeriod)
            Log.d(TAG, "Listener ACCELEROMETER registrato")
        }

        // Avvio flush periodico
        handler.postDelayed(flushRunnable, intervalMs)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(flushRunnable)
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Servizio distrutto.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        // Aggiorno pending
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE ->
                pendingHr = event.values[0]
            Sensor.TYPE_GYROSCOPE ->
                pendingGyro = Triple(event.values[0], event.values[1], event.values[2])
            Sensor.TYPE_ACCELEROMETER ->
                pendingAccel = Triple(event.values[0], event.values[1], event.values[2])
        }

        // Creo snapshot e lo bufferizzo
        val snap = Snapshot(
            timestamp = Instant.now().toString(),
            hr = pendingHr,
            gyro = pendingGyro,
            accel = pendingAccel
        )
        var toSend: List<Snapshot>? = null

        synchronized(bufferLock) {
            snapshotBuffer.add(snap)
            // Se abbiamo raggiunto 400, estraiamo il blocco completo
            if (snapshotBuffer.size >= targetBatchSize) {
                toSend = snapshotBuffer.subList(0, targetBatchSize).toList()
                snapshotBuffer.subList(0, targetBatchSize).clear()
            }
        }

        // Invia immediatamente se ready
        toSend?.let { sendBatch(it) }
    }

    private val flushRunnable = object : Runnable {
        override fun run() {
            var toSend: List<Snapshot>? = null
            synchronized(bufferLock) {
                if (snapshotBuffer.isNotEmpty()) {
                    toSend = snapshotBuffer.toList()
                    snapshotBuffer.clear()
                }
            }
            toSend?.let { sendBatch(it) }
            handler.postDelayed(this, intervalMs)
        }
    }

    /** Serializza in JSON e invia in sottolotti se >400  */
    private fun sendBatch(batch: List<Snapshot>) {
        // chunk in blocchi di max targetBatchSize
        batch.chunked(targetBatchSize).forEachIndexed { idx, chunk ->
            val jsonArray = chunk.joinToString(prefix = "[", postfix = "]") { s ->
                """{"timestamp":"${s.timestamp}","hr":${s.hr},"gyro":{"x":${s.gyro.first},"y":${s.gyro.second},"z":${s.gyro.third}},"accel":{"x":${s.accel.first},"y":${s.accel.second},"z":${s.accel.third}}}"""
            }
            val payload = """{"samples":$jsonArray}"""
            Log.d(TAG, "Invio batch ${idx+1}/${(batch.size+targetBatchSize-1)/targetBatchSize} di ${chunk.size} snapshot")
            postJson(payload)
        }
    }

    private fun postJson(json: String) {
        val mt = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mt)
        OkHttpClient().newCall(
            Request.Builder()
                .url("http://172.20.10.2:3000/api/data")
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Accuracy ${sensor?.type}: $accuracy")
    }
}