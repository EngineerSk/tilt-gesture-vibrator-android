package com.oriadesoftdev.tiltgesturevibrator

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.oriadesoftdev.tiltgesturevibrator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private lateinit var vibrator: Vibrator
    private val vibratePattern = longArrayOf(DELAY, VIBRATE, SLEEP)
    private var menuVibrationEventType: Int = 1
    private var vibrationEffect = 0

    private val broadcastReceiver by lazy {
        object : BroadcastReceiver() {
            @RequiresApi(Build.VERSION_CODES.Q)
            override fun onReceive(context: Context?, intent: Intent?) {
                val direction = intent?.getStringExtra(TiltGestureService.KEY_DIRECTION)
                val angle = intent?.getDoubleExtra(TiltGestureService.KEY_ANGLE, 0.0) ?: 0.0
                val orientation = intent?.getFloatArrayExtra(TiltGestureService.KEY_ORIENTATION)
                val angleWithDirection = angle.toString() + "  " + direction as Any?
                when (direction) {
                    "NW", "SE", "NE", "SW" -> vibrateDeviceByVersionCodeAndVibeEffect(
                        Build.VERSION_CODES.Q,
                        VibrationEffect.EFFECT_HEAVY_CLICK
                    )
                }
                initUI(orientation, angleWithDirection, angle)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_TiltGestureVibrator)
        setContentView(binding.root)
        requestPermission()
        initMenuProvider()
        @Suppress("DEPRECATION")
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as VibratorManager
            vibratorManager.defaultVibrator.apply {
                cancel()
            }
        } else getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver,
            IntentFilter(TiltGestureService.KEY_ON_SENSOR_CHANGED_ACTION)
        )
    }

    private fun initMenuProvider() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_vibration_effect, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                menuVibrationEventType = when (menuItem.itemId) {
                    R.id.action_one_shot -> 1
                    R.id.action_waveform -> 2
                    else -> 1
                }
                return true
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        startForegroundServiceForSensors(false)
    }

    private val hasForegroundServicePermission: () -> Boolean = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            checkIfPermissionGranted(android.Manifest.permission.FOREGROUND_SERVICE)
        true
    }

    private val hasVibrateServicePermission: () -> Boolean = {
        checkIfPermissionGranted(android.Manifest.permission.VIBRATE)
    }

    private val hasFineLocationPermission: () -> Boolean = {
        checkIfPermissionGranted(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val hasCoarseLocationPermission: () -> Boolean = {
        checkIfPermissionGranted(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private val checkIfPermissionGranted: (String) -> Boolean = {
        ActivityCompat.checkSelfPermission(
            applicationContext,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        val permissionsToRequest = mutableListOf<String>()

        if (!hasForegroundServicePermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                permissionsToRequest.add(android.Manifest.permission.FOREGROUND_SERVICE)
        }
        if (!hasVibrateServicePermission()) {
            permissionsToRequest.add(android.Manifest.permission.VIBRATE)
        }

        if (!hasFineLocationPermission()) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if(!hasCoarseLocationPermission()){
            permissionsToRequest.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty())
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                0
            )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0 && grantResults.isNotEmpty()) {
            for (i in grantResults.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                    Log.d(TAG, "onRequestPermissionsResult: ${permissions[i]} granted...")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundServiceForSensors(background: Boolean) {
        val serviceIntent = Intent(this, TiltGestureService::class.java)
        serviceIntent.putExtra(TiltGestureService.KEY_BACKGROUND, background)
        startForegroundService(serviceIntent)
    }

    @SuppressLint("InlinedApi")
    private fun initUI(orientation: FloatArray?, angleWithDirection: String, angle: Double) {
        binding.apply {
            compassImageView.rotation = (angle * (-1)).toFloat()
            directionTextView.text = angleWithDirection
            xCoordinateValue.text =
                getString(R.string.axis_info, "x", orientation?.get(0))
            yCoordinateValue.text =
                getString(R.string.axis_info, "y", orientation?.get(1))
            zCoordinateValue.text =
                getString(R.string.axis_info, "z", orientation?.get(2))
        }
    }

    private fun vibrateDeviceByVersionCodeAndVibeEffect(versionCode: Int, vibrationEffect: Int) {
        if (Build.VERSION.SDK_INT >= versionCode) {
            this.vibrationEffect = vibrationEffect
        }
        vibrator.cancel()
        vibrate()
    }

    private fun vibrate() {
        if (menuVibrationEventType == 1)
            createOneShot()
        else
            createWaveform()
    }

    private fun createOneShot(vibrationEffect: Int = 1) {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    VIBRATE,
                    vibrationEffect
                )
            )
        } else
            vibrator.vibrate(vibratePattern, START)
    }

    private fun createWaveform() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(vibratePattern, 2)
            )
        } else
            vibrator.vibrate(vibratePattern, START)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPause() {
        super.onPause()
        startForegroundServiceForSensors(true)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
        const val DELAY = 500L
        const val VIBRATE = 1000L
        const val SLEEP = 500L
        const val START = 0
    }
}