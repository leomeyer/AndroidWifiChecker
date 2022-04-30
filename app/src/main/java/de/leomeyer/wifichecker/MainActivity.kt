package de.leomeyer.wifichecker

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.leomeyer.wifichecker.WifiCheckerService.Companion.DEFAULT_THRESHOLD
import de.leomeyer.wifichecker.WifiCheckerService.Companion.PREF_WIFI_DBM_LEVEL

class MainActivity : AppCompatActivity() {

    enum class Actions {
        START,
        STOP
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        title = "Wifi Checker"

        findViewById<Button>(R.id.btnStartService).let {
            it.setOnClickListener {
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                sharedPref.edit().putBoolean(WifiCheckerService.PREF_START_ON_BOOT, true).apply()
                actionOnService(Actions.START)
            }
        }

        findViewById<Button>(R.id.btnStopService).let {
            it.setOnClickListener {
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                sharedPref.edit().putBoolean(WifiCheckerService.PREF_START_ON_BOOT, false).apply()
                actionOnService(Actions.STOP)
            }
        }

        findViewById<Button>(R.id.btnCheckLevel).let {
            it.setOnClickListener {
                // get wifi connection strength
                val wifiManager = getApplicationContext().getSystemService(WIFI_SERVICE) as WifiManager

                // wifi off?
                if (!wifiManager.isWifiEnabled) {
                    Toast.makeText(applicationContext, "Enable wifi and connect to a network to check the signal level", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val manager = getApplicationContext().getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val mWifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)

                // not connected? do nothing
                if (mWifi?.isConnected != true) {
                    Toast.makeText(applicationContext, "Connect to a wifi network to check the signal level", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val wifiInfo = wifiManager.connectionInfo

                Toast.makeText(applicationContext, "Current wifi level is " + wifiInfo.rssi + " dBm", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<Button>(R.id.btnSettings).let {
            it.setOnClickListener {
                val i = Intent(this@MainActivity, SettingsActivity::class.java)
                startActivity(i)
            }
        }

        findViewById<Button>(R.id.btnInfo).let {
            it.setOnClickListener {
                val uriUrl: Uri = Uri.parse("https://github.com/leomeyer/AndroidWifiChecker")
                val launchBrowser = Intent(Intent.ACTION_VIEW, uriUrl)
                startActivity(launchBrowser)
            }
        }

        // initialize preferences if not set
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        var value = 0
        try {
            value = sharedPref.getString(PREF_WIFI_DBM_LEVEL, "0")?.toInt()!!
        } catch (e: Exception) {}

        if (value >= 0) {
            value = DEFAULT_THRESHOLD
            sharedPref.edit().putString(PREF_WIFI_DBM_LEVEL, value.toString()).apply()
        }

        // check whether to start the service
        if (sharedPref.getBoolean(WifiCheckerService.PREF_START_ON_BOOT, false))
            actionOnService(Actions.START)
    }

    private fun actionOnService(action: Actions) {
        Intent(this, WifiCheckerService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(it)
                return
            }
            startService(it)
        }
    }
}
