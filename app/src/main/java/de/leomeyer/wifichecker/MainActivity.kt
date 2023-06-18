package de.leomeyer.wifichecker

import android.app.AlertDialog
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


class MainActivity : AppCompatActivity() {

    enum class Actions {
        START,
        STOP
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WifiCheckerService.BUS.observe(this, { updateServiceState() })

        title = "Wifi Checker"

        findViewById<Button>(R.id.btnStartService).let {
            it.setOnClickListener {
                actionOnService(Actions.START)
            }
        }

        findViewById<Button>(R.id.btnStopService).let {
            it.setOnClickListener {
                actionOnService(Actions.STOP)
            }
        }

        findViewById<Button>(R.id.btnCheckWifi).let {
            it.setOnClickListener {
                // get wifi connection strength
                val wifiManager = getApplicationContext().getSystemService(WIFI_SERVICE) as WifiManager

                // wifi off?
                if (!wifiManager.isWifiEnabled) {
                    Toast.makeText(applicationContext, "Enable wifi and connect to a network to check the signal level.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val manager = getApplicationContext().getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val mWifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)

                // not connected? do nothing
                if (mWifi?.isConnected != true) {
                    Toast.makeText(applicationContext, "Connect to a wifi network to check the signal level.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val wifiInfo = wifiManager.connectionInfo

                val ssid = WifiCheckerService.findSSIDForWifiInfo(wifiManager, wifiInfo)
                if (ssid != null)
                    Toast.makeText(applicationContext, "Wifi signal level of '" + ssid + "' is " + wifiInfo.rssi + " dBm.", Toast.LENGTH_LONG).show()
                else
                    Toast.makeText(applicationContext, "Wifi signal level is " + wifiInfo.rssi + " dBm.", Toast.LENGTH_LONG).show()

                val toggled = if (WifiCheckerService.instance != null)
                    WifiCheckerService.instance!!.checkWifi()
                else
                    WifiCheckerService().checkWifi()

                if (toggled)
                    Toast.makeText(this, "Poor wifi signal detected, toggling...", Toast.LENGTH_SHORT).show()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        WifiCheckerService.BUS.removeObservers(this)
    }

    private fun updateServiceState() : Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val configured = sharedPref.getBoolean(WifiCheckerService.PREF_CONFIGURED, false)

        findViewById<Button>(R.id.btnStartService).isEnabled = configured && WifiCheckerService.instance == null
        findViewById<Button>(R.id.btnStopService).isEnabled = configured && WifiCheckerService.instance != null

        return configured
    }

    override fun onResume() {
        super.onResume()

        val configured = updateServiceState()

        if (!configured) {
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle("Welcome to Android Wifi Checker")
                .setMessage("Please click OK to configure the service.")
                .setCancelable(false)
                .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                        val i = Intent(this@MainActivity, SettingsActivity::class.java)
                        startActivity(i)
                    }
                .show()
            return
        }

        if (configured) {
            // service not running?
            if (WifiCheckerService.instance == null) {
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                if (sharedPref.getBoolean(WifiCheckerService.PREF_START_ON_BOOT, false))
                    actionOnService(Actions.START)
                else
                    Toast.makeText(
                        this,
                        "Click 'Start Wifi Checker' to start the service.",
                        Toast.LENGTH_SHORT
                    ).show()
            }
        }
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
