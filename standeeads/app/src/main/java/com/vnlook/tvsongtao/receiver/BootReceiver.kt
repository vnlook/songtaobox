package com.vnlook.tvsongtao.receiver

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vnlook.tvsongtao.DigitalClockActivity

/**
 * Broadcast receiver that starts the app when the device is booted
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val isAppRunning = activityManager.runningAppProcesses.any {
                it.processName == context.packageName
            }

            if (!isAppRunning) {
                val launchIntent = Intent(context, DigitalClockActivity::class.java)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
