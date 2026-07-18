package com.flip.galaxydeskclock

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import java.time.ZonedDateTime

class MainActivity : Activity() {
    private lateinit var clockView: ClockView
    private lateinit var settings: AppSettings
    private val handler = Handler(Looper.getMainLooper())
    private val brightnessRefresh = object : Runnable {
        override fun run() {
            if (!settings.chargingControlEnabled || isCurrentlyCharging()) applyBrightness()
            handler.postDelayed(this, 60_000L)
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> updateChargingState(true)
                Intent.ACTION_POWER_DISCONNECTED -> updateChargingState(false)
                ChargingReceiver.ACTION_POWER_STATE_CHANGED -> {
                    updateChargingState(intent.getBooleanExtra(ChargingReceiver.EXTRA_CONNECTED, false))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        clockView = ClockView(this).apply {
            onOpenSettings = {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
        setContentView(clockView)
        enterImmersiveMode()
        registerPowerReceiver()
    }

    override fun onResume() {
        super.onResume()
        clockView.reload()
        enterImmersiveMode()
        applyBrightness()
        updateChargingState(isCurrentlyCharging())
        handler.removeCallbacks(brightnessRefresh)
        handler.post(brightnessRefresh)
    }

    override fun onPause() {
        handler.removeCallbacks(brightnessRefresh)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        handler.removeCallbacks(brightnessRefresh)
        runCatching { unregisterReceiver(powerReceiver) }
        super.onDestroy()
    }

    private fun registerPowerReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(ChargingReceiver.ACTION_POWER_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(powerReceiver, filter)
        }
    }

    private fun updateChargingState(connected: Boolean) {
        clockView.setPowerConnected(connected)
        if (!settings.chargingControlEnabled || connected) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (connected) applyBrightness()
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = 0.01f }
        }
    }

    private fun applyBrightness() {
        val hour = ZonedDateTime.now().hour
        window.attributes = window.attributes.apply {
            screenBrightness = settings.brightnessForHour(hour)
        }
    }

    private fun isCurrentlyCharging(): Boolean {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun enterImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
