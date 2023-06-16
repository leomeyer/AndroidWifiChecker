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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.*
import android.preference.PreferenceManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import java.text.DateFormat
import java.util.Date


class WifiCheckerService : Service(), SensorEventListener {

    companion object {
        const val SERVICE_TAG = "WifiCheckerService"
        
        const val MOVEMENT_CHECK_DELAY: Int = 5000   // ms

        const val PREF_CONFIGURED = "pref_configured"
        const val PREF_START_ON_BOOT = "pref_start_on_boot"
        const val PREF_WIFI_DBM_LEVEL = "pref_wifi_db_level"
        const val PREF_PERIODIC_CHECK = "pref_periodic_check"
        const val PREF_CHECK_WHEN_MOVED = "pref_check_when_moved"
        const val PREF_TOGGLE_IF_NO_INTERNET = "pref_toggle_if_no_internet"
        const val PREF_NOTIFY_TOGGLE = "pref_notify_toggle"

        const val NOTIFICATION_CHANNEL_ID = "WIFI CHECKER SERVICE CHANNEL"
        const val NOTIFICATION_ID = 1

        val BUS = MutableLiveData<Any>()

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
                // Toast.makeText(context, "CheckJobService ran " + CheckJobService.runCounter + " times since last ScreenOn", Toast.LENGTH_SHORT).show()
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

    inner class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager!!.getNetworkCapabilities(network)
            wifiNetworkAvailable =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Log.d(SERVICE_TAG, "Wifi is available")
        }

        override fun onLost(network: Network?) {
            wifiNetworkAvailable = false
            Log.d(SERVICE_TAG, "Wifi is not available")
        }
    }

    private var notificationManager: NotificationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private val screenOn = ScreenOnReceiver()
    private var timeOfLastSignificantMotion : Date? = null
    private var sensorManager: SensorManager? = null
//    private var motionSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var telephonyManager: TelephonyManager? = null
    private var wifiManager: WifiManager? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback = NetworkCallback()
    private var wifiNetworkAvailable: Boolean? = null
    private var ongoingCall = false

    fun onDeviceMoved(context: Context) {
        // do not check too often
        if (timeOfLastSignificantMotion != null)
            if (Date().time - timeOfLastSignificantMotion!!.time < MOVEMENT_CHECK_DELAY) {
                // Log.d(SERVICE_TAG, "Device movement detected but delay has not yet passed")
                return
            }

        Log.d(SERVICE_TAG, "Device movement detected")
        //Toast.makeText(context, "Device movement detected", Toast.LENGTH_SHORT).show()
        timeOfLastSignificantMotion = Date()
        checkWifi();
    }

    fun checkWifi(): Boolean {
        try {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            if (!sharedPref.getBoolean(PREF_CONFIGURED, false))
                return false

            if (wifiManager == null)
                wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

            // check signal strength
            val wifiInfo = wifiManager!!.connectionInfo

            val rssiThreshold = sharedPref.getString(PREF_WIFI_DBM_LEVEL, "0")?.toInt()
            // signal check disabled?
            if (rssiThreshold == null || rssiThreshold >= 0) {
                Log.d(SERVICE_TAG, "Wifi signal strength check disabled ($rssiThreshold)")
                return false
            }

            // wifi off? do nothing
            if (!wifiManager!!.isWifiEnabled) {
                Log.d(SERVICE_TAG, "Wifi is disabled")
                return false
            } else {
                Log.d(SERVICE_TAG, "Wifi is enabled")
            }


            if (connectivityManager == null)
                connectivityManager = applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager!!.activeNetwork

            // not connected? do nothing
            if (activeNetwork == null) {
                Log.d(SERVICE_TAG, "No active network")
                return false
            }

            val capabilities = connectivityManager!!.getNetworkCapabilities(activeNetwork) // needs ACCESS_NETWORK_STATE permission

            // connected to mobile data?
            if (capabilities == null || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                Log.d(SERVICE_TAG, "Network is inactive or not a Wifi network")
                return false
            }
/*
            // setup listener if check on movement is enabled
            if (sharedPref.getBoolean(PREF_CHECK_WHEN_MOVED, false)) {
                val motionEventListener = object : TriggerEventListener() {
                    override fun onTrigger(event: TriggerEvent?) {
                        onDeviceMoved(applicationContext)
                    }
                }
                // request significant movement events
                motionSensor?.also { sensor ->
                    val ok = sensorManager?.requestTriggerSensor(motionEventListener, sensor)
                    if (!ok!!)
                        Log.d(SERVICE_TAG, "Unable to request movement trigger sensor")
                }
            }
 */
            // register periodic check job if configured
            CheckJobService.checkPeriodicJob(applicationContext, sharedPref)

            // do not check during phone calls to avoid interrupting connections
            if (ongoingCall) {
                Log.d(SERVICE_TAG, "Skipping wifi check due to ongoing call")
                return false
            }

            // determine whether to toggle
            var toggle = false

            if (sharedPref.getBoolean(PREF_TOGGLE_IF_NO_INTERNET, true))
                // toggle if no network or no internet connectivity
                toggle = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (!toggle) {
                // check signal strength
                if (wifiInfo.rssi < rssiThreshold) {
                    toggle = true
                }
            }

            if (toggle) {
                // notify?
                if (sharedPref.getBoolean(PREF_NOTIFY_TOGGLE, true)) {
                    val ssid = findSSIDForWifiInfo(wifiManager!!, wifiInfo)
                    if (ssid != null) {
                        Log.d(SERVICE_TAG, "Wifi signal strength of '" + ssid + "' is poor (${wifiInfo.rssi} dBm), toggling...")
                    } else {
                        Log.d(SERVICE_TAG, "Wifi signal strength is poor (${wifiInfo.rssi} dBm), toggling...")
                    }

                    notificationManager?.notify(NOTIFICATION_ID, createNotification(DateFormat.getTimeInstance().format(Date()) + ": Wifi toggled due to poor connectivity"))
                }

                wifiManager!!.setWifiEnabled(false)
                Thread.sleep(100)
                wifiManager!!.setWifiEnabled(true)
            } else
                Log.d(SERVICE_TAG, "Wifi signal strength is ok (${wifiInfo.rssi} dBm).")

            return toggle
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return false
        }
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
        if (BUS.hasActiveObservers()) {
            BUS.postValue(SERVICE_TAG)
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = createNotification("Toggles wifi if network connection is poor")
        startForeground(NOTIFICATION_ID, notification)

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenOn, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenOn)
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
        isServiceStarted = true
        instance = this

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Wifi Checker Service notifications channel",
                NotificationManager.IMPORTANCE_LOW
            ).let {
                it.description = "Wifi Checker Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it
            }
            notificationManager?.createNotificationChannel(channel)
        }

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, SERVICE_TAG + "::lock").apply {
                    acquire(10*60*1000L /*10 minutes*/)
                }
            }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
//        motionSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (sharedPref.getBoolean(PREF_CHECK_WHEN_MOVED, false)) {
            sensorManager!!.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        telephonyManager =
            applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        if (telephonyManager == null)
            Log.d(SERVICE_TAG, "Unable to listen for phone state; TelephonyManager not available")
/*
        // for SDK version >= 31
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(
                context.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                    }
                })
        } else {
 */
        telephonyManager!!.listen(object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                ongoingCall = state != TelephonyManager.CALL_STATE_IDLE
                if (ongoingCall)
                    Log.d(SERVICE_TAG, "Ongoing call detected")
                else
                    Log.d(SERVICE_TAG, "No ongoing call detected")
            }
        }, PhoneStateListener.LISTEN_CALL_STATE)

        if (connectivityManager == null)
            connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        connectivityManager?.registerDefaultNetworkCallback(networkCallback)

        Log.d(SERVICE_TAG, "------------------------")
        Log.d(SERVICE_TAG, "Service has been started")
        Toast.makeText(this, "Wifi Checker service started.", Toast.LENGTH_SHORT).show()

        // check signal immediately; also initializes jobs and other notifications
        checkWifi()
    }

    private fun stopService() {
        instance = null
        try {
            sensorManager!!.unregisterListener(this)
            connectivityManager?.unregisterNetworkCallback(networkCallback)

            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()

            Log.d(SERVICE_TAG, "Service has been stopped")
            Log.d(SERVICE_TAG, "------------------------")
            Toast.makeText(this, "Wifi Checker service stopped.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
        isServiceStarted = false
    }

    private fun createNotification(message: String): Notification {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            NOTIFICATION_CHANNEL_ID
        ) else Notification.Builder(this)

        return builder
            .setContentTitle("Wifi Checker")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_wifi)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

                val x = event.values?.get(0)
                val y = event.values?.get(1)
                val z = event.values?.get(2)
                val diff = Math.sqrt((x?.times(x) ?: 0).toDouble() + (y?.times(y) ?: 0).toDouble() + (z?.times(z) ?: 0).toDouble())

                if (diff > 2.0) // experimental threshold
                    onDeviceMoved(this)
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }
}

