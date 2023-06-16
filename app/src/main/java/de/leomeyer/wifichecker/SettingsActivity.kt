package de.leomeyer.wifichecker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar!!.title = "Wifi Checker Settings"
        val settingsFragment = SettingsFragment()

        if (findViewById<View>(R.id.button_done) != null) {
            findViewById<View>(R.id.button_done).setOnClickListener({
                v -> settingsFragment.save()
                finish()
            })
        }

        if (findViewById<View>(R.id.idFrameLayout) != null) {
            if (savedInstanceState != null) {
                return
            }
            fragmentManager.beginTransaction().add(R.id.idFrameLayout, settingsFragment).commit()
        }
    }
}