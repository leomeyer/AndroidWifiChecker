package de.leomeyer.wifichecker

import android.app.*
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.*
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast


class WifiCheckerService : Service() {

    companion object {
        const val DEFAULT_THRESHOLD: Int = -80        // dBm

        const val PREF_START_ON_BOOT = "pref_start_on_boot"
        const val PREF_TOGGLE_IF_NO_INTERNET = "pref_toggle_if_no_internet"
        const val PREF_WIFI_DBM_LEVEL = "pref_wifi_db_level"
        const val PREF_NOTIFY_TOGGLE = "pref_notify_toggle"
        const val PREF_PERIODIC_CHECK = "pref_periodic_check"

        var instance: WifiCheckerService? = null

        fun findSSIDForWifiInfo(manager: WifiManager, wifiInfo: WifiInfo): String? {
            val listOfConfigurations = manager.configuredNetworks ?: return null
            for (index in listOfConfigurations.indices) {
                val configuration = listOfConfigurations[index]
                if (configuration.networkId == wifiInfo.networkId) {
                    return configuration.SSID
                }
            }
            return null
        }
    }

    inner class ScreenOnReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (Intent.ACTION_SCREEN_ON == intent!!.action) {
                Toast.makeText(context, "CheckJobService ran " + CheckJobService.runCounter + " times since last ScreenOn", Toast.LENGTH_SHORT)
                    .show()
                // reschedule next job execution
                val jobInfo = JobInfo.Builder(CheckJobService.JOB_ID, ComponentName(context, CheckJobService::class.java))
                    .setMinimumLatency(100)
                    .setOverrideDeadline(1000)
                    .build()
                var jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.schedule(jobInfo)

                CheckJobService.runCounter = 0
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private var screenOn = ScreenOnReceiver()

    fun checkWifi(context: Context) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)

        CheckJobService.checkPeriodicJob(context, sharedPref)

        val wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        // wifi off? do nothing
        if (!wifiManager.isWifiEnabled) {
            Log.d("WifiCheckerService", "Wifi is disabled")
            return
        } else {
            Log.d("WifiCheckerService", "Wifi is enabled")
        }

        val manager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = manager.activeNetwork

        // not connected? do nothing
        if (activeNetwork == null) {
            Log.d("WifiCheckerService", "No active network")
            return
        }

        // connected to mobile data?
        if (manager.getNetworkCapabilities(manager.activeNetwork).hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            Log.d("WifiCheckerService", "Network is active but not a Wifi network")
            return
        }

        // determine whether to toggle
        var toggle = false

        if (sharedPref.getBoolean(PREF_TOGGLE_IF_NO_INTERNET, true)) {
            // check whether the connection has connectivity
            try {
                val capabilities =
                    manager.getNetworkCapabilities(manager.activeNetwork) // need ACCESS_NETWORK_STATE permission
                // toggle if no network or no internet connectivity
                toggle =
                    capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        // no toggle necessary so far?
        if (!toggle) {
            // check signal strength
            val wifiInfo = wifiManager.connectionInfo

            val value = sharedPref.getString(PREF_WIFI_DBM_LEVEL, "0")?.toInt()
            // signal check disabled?
            if (value != null) {
                if (value >= 0) {
                    Log.d("WifiCheckerService", "Wifi signal strength check disabled ($value)")
                    return
                }

                if (wifiInfo.rssi < value) {
                    toggle = true
                }
            }
        }

        if (toggle) {
            // notify?
            if (sharedPref.getBoolean(PREF_NOTIFY_TOGGLE, true)) {
                val wifiInfo = wifiManager.connectionInfo
                val ssid = findSSIDForWifiInfo(wifiManager, wifiInfo)
                if (ssid != null)
                    Toast.makeText(context, "Wifi signal of '" + ssid + "' is low. Toggling...", Toast.LENGTH_SHORT).show()
                else {
                    Log.d("WifiCheckerService", "Wifi signal strength is low (${wifiInfo.rssi}), toggling...")
                    Toast.makeText(context, "Wifi signal is low. Toggling...", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            wifiManager.setWifiEnabled(false)
            Thread.sleep(100)
            wifiManager.setWifiEnabled(true)
        } else
            Log.d("WifiCheckerService", "Wifi signal strength is ok.")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            when (action) {
                MainActivity.Actions.START.name -> startService()
                else -> stopService()
            }
        } else {
            startService()
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = createNotification()
        startForeground(1, notification)

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenOn, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenOn)
        Toast.makeText(this, "Wifi Checker stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        val restartServiceIntent = Intent(applicationContext, WifiCheckerService::class.java).also {
            it.setPackage(packageName)
            it.action = MainActivity.Actions.START.name
        }
        val restartServicePendingIntent: PendingIntent = PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
        applicationContext.getSystemService(Context.ALARM_SERVICE)
        val alarmService: AlarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent)
    }
    
    private fun startService() {
        if (isServiceStarted) return
        Toast.makeText(this, "Wifi Checker started", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        instance = this

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiCheckerService::lock").apply {
                    acquire(10*60*1000L /*10 minutes*/)
                }
            }

        CheckJobService.checkPeriodicJob(this, PreferenceManager.getDefaultSharedPreferences(this))
    }

    private fun stopService() {
        instance = null
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
        }
        isServiceStarted = false
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "WIFI CHECKER SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Wifi Checker Service notifications channel",
                NotificationManager.IMPORTANCE_LOW
            ).let {
                it.description = "Wifi Checker Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

        return builder
            .setContentTitle("Wifi Checker")
            .setContentText("Toggles weak wifi when the display is switched on")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_wifi)
            .build()
    }
}

