package de.leomeyer.wifichecker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.preference.PreferenceManager
import de.leomeyer.wifichecker.WifiCheckerService.Companion.PREF_START_ON_BOOT

class StartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // check whether to start on boot
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sharedPref.getBoolean(PREF_START_ON_BOOT, false))
            return

        Intent(context, WifiCheckerService::class.java).also {
            it.action = MainActivity.Actions.START.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(it)
                return
            }
            context.startService(it)
        }
    }
}
