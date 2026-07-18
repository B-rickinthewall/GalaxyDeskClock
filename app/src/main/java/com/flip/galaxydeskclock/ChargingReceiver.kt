package com.flip.galaxydeskclock

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = AppSettings(context)
        if (!settings.chargingControlEnabled) return

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                context.sendBroadcast(
                    Intent(ACTION_POWER_STATE_CHANGED)
                        .setPackage(context.packageName)
                        .putExtra(EXTRA_CONNECTED, true)
                )
                if (settings.wakeOnCharge) {
                    wakeScreenBriefly(context)
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }
                    runCatching { context.startActivity(launchIntent) }
                }
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                context.sendBroadcast(
                    Intent(ACTION_POWER_STATE_CHANGED)
                        .setPackage(context.packageName)
                        .putExtra(EXTRA_CONNECTED, false)
                )
                if (settings.immediateLockOnDisconnect) {
                    val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val component = ComponentName(context, DeskClockAdminReceiver::class.java)
                    if (manager.isAdminActive(component)) {
                        runCatching { manager.lockNow() }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "GalaxyDeskClock:ChargeWake"
        )
        runCatching { wakeLock.acquire(5_000L) }
    }

    companion object {
        const val ACTION_POWER_STATE_CHANGED = "com.flip.galaxydeskclock.POWER_STATE_CHANGED"
        const val EXTRA_CONNECTED = "connected"
    }
}
