package de.leomeyer.wifichecker

import android.app.job.JobScheduler
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceFragment

class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.preferences)
    }

    override fun onResume() {
        super.onResume()
        // Set up a listener whenever a key changes
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        // Set up a listener whenever a key changes
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key.equals(WifiCheckerService.PREF_PERIODIC_CHECK)) {
            // stop a running CheckJobService instance
            with (context.applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler) {
                cancel(CheckJobService.JOB_ID)
            }
            // start again (in case it's not disabled)
            if (sharedPreferences != null) {
                CheckJobService.checkPeriodicJob(activity, sharedPreferences)
            }
        }
    }

    fun save() {
        val editor: SharedPreferences.Editor = preferenceScreen.sharedPreferences.edit()
        editor.putBoolean(WifiCheckerService.PREF_CONFIGURED, true)
        editor.apply()
    }
}