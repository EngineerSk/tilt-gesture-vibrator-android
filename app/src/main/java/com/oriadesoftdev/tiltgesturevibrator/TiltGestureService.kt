package com.oriadesoftdev.tiltgesturevibrator

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlin.math.round

class TiltGestureService : Service() {

    private val sensorManager: SensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val accelerometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    private val magnetometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var background = false

    private var angle = 0.0
    private var direction = "N"

    private val sensorEventListener: SensorEventListener by lazy {
        object : SensorEventListener {
            @RequiresApi(Build.VERSION_CODES.Q)
            override fun onSensorChanged(sensorEvent: SensorEvent?) {
                if (sensorEvent == null) {
                    return
                }
                if (sensorEvent.sensor.type == 1) {
                    val fArr: FloatArray = sensorEvent.values
                    val fArr2: FloatArray = accelerometerReading
                    System.arraycopy(fArr, 0, fArr2, 0, fArr2.size)
                } else if (sensorEvent.sensor.type == 2) {
                    val fArr3: FloatArray = sensorEvent.values
                    val fArr4: FloatArray = magnetometerReading
                    System.arraycopy(fArr3, 0, fArr4, 0, fArr4.size)
                }
                updateOrientationAngles()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerAccelerometer()
        registerMagnetometer()
    }


    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null)
            return START_NOT_STICKY
        background = intent.getBooleanExtra(
            KEY_BACKGROUND,
            false
        )
        val notification = createNotification(direction, angle)
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateOrientationAngles() {
        SensorManager.getRotationMatrix(
            rotationMatrix, null, accelerometerReading,
            magnetometerReading
        )
        val orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val degrees = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
        val d = 100.0
        angle = round(degrees * d) / d
        direction = getDirection(angle)
        val intent = Intent()
        intent.putExtra(KEY_ANGLE, angle)
        intent.putExtra(KEY_DIRECTION, direction)
        intent.putExtra(KEY_ORIENTATION, orientation)
        intent.action = KEY_ON_SENSOR_CHANGED_ACTION
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun getDirection(angle: Double): String {
        return if (angle >= 350.0 || angle <= 10.0) "N"
        else if (angle < 350.0 && angle > 280.0) {
            "NW"
        } else if (angle <= 280.0 && angle > 260.0) {
            "W"
        } else if (angle <= 260.0 && angle > 190.0) {
            "SW"
        } else if (angle <= 190.0 && angle > 170.0) {
            "S"
        } else if (angle <= 170.0 && angle > 100.0) {
            "SE"
        } else if (angle <= 100.0 && angle > 80.0) {
            "E"
        } else {
            "NE"
        }
    }


    private fun registerAccelerometer() {
        accelerometer?.let { registerSensor(it) }
    }

    private fun registerMagnetometer() {
        magnetometer?.let { registerSensor(it) }
    }

    private fun registerSensor(sensor: Sensor) {
        sensorManager.registerListener(
            sensorEventListener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            2
        )
    }

    private fun unregisterSensorManager() {
        sensorManager.unregisterListener(sensorEventListener)
    }

    private fun createNotification(direction: String, angle: Double): Notification {
        val systemService = getSystemService(Context.NOTIFICATION_SERVICE)
        if (systemService != null) {
            val notificationManager = systemService as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationChannel =
                    NotificationChannel(
                        application.packageName,
                        "Notifications",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                notificationChannel.enableLights(false)
                notificationChannel.setSound(null, null)
                notificationChannel.enableVibration(false)
                notificationChannel.vibrationPattern = longArrayOf(0)
                notificationChannel.setShowBadge(false)
                notificationManager.createNotificationChannel(notificationChannel)
            }
            val notificationBuilder = NotificationCompat.Builder(
                baseContext, application.packageName
            )
            val contentIntent = PendingIntent.getActivity(
                this, NOTIFICATION_STOP_REQUEST_CODE, Intent(
                    this,
                    MainActivity::class.java
                ), PendingIntent.FLAG_UPDATE_CURRENT
            )
            val stopNotificationIntent = Intent(this, ActionListener::class.java)
            stopNotificationIntent.action = KEY_NOTIFICATION_STOP_ACTION
            stopNotificationIntent.putExtra(
                KEY_NOTIFICATION_ID,
                NOTIFICATION_ID
            )
            val pendingStopNotificationIntent = PendingIntent.getBroadcast(
                this,
                NOTIFICATION_STOP_REQUEST_CODE,
                stopNotificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            val contentTitle =
                notificationBuilder.setAutoCancel(true).setDefaults(-1).setContentTitle(
                    resources.getString(R.string.app_name)
                )
            contentTitle.setContentText("You're currently facing $direction at an angle of $angle$direction")
                .setWhen(
                    System.currentTimeMillis()
                ).setDefaults(0).setVibrate(longArrayOf(0)).setSound(null)
                .setSmallIcon(R.mipmap.ic_launcher_round).setContentIntent(contentIntent).addAction(
                    R.mipmap.ic_launcher_round,
                    getString(R.string.stop_notifications),
                    pendingStopNotificationIntent
                )
            return notificationBuilder.build()
        }
        throw NullPointerException("null cannot be cast to non-null type android.app.NotificationManager")
    }


    override fun onDestroy() {
        unregisterSensorManager()
        super.onDestroy()
    }

    class ActionListener : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != null) {
                val systemService = context.getSystemService(Context.NOTIFICATION_SERVICE)
                val notificationManager = systemService as NotificationManager
                val serviceIntent = Intent(context, TiltGestureService::class.java)
                context.stopService(serviceIntent)
                val notificationId = intent.getIntExtra(
                    KEY_NOTIFICATION_ID,
                    -1
                )
                if (notificationId != -1) {
                    notificationManager.cancel(notificationId)
                }
            }
        }
    }

    companion object {
        const val KEY_ANGLE = "angle"
        const val KEY_BACKGROUND = "background"
        const val KEY_DIRECTION = "direction"
        const val KEY_NOTIFICATION_ID = "notificationId"
        const val KEY_NOTIFICATION_STOP_ACTION =
            "com.oriadesoftdev.tiltgesturevibrator.NOTIFICATION_STOP"
        const val KEY_ON_SENSOR_CHANGED_ACTION =
            "com.oriadesoftdev.tiltgesturevibrator.ON_SENSOR_CHANGED"
        const val KEY_ORIENTATION = "orientation"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_STOP_REQUEST_CODE = 2
    }
}