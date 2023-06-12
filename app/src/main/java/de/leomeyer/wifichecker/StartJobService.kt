package de.leomeyer.wifichecker

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build

class StartJobService : JobService() {
    companion object {
        const val JOB_ID: Int = 0x10000
    }
    override fun onStartJob(p0: JobParameters?): Boolean {
        Intent(applicationContext, WifiCheckerService::class.java).also {
            it.action = MainActivity.Actions.START.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(it)
                return true
            }
            applicationContext.startService(it)
            return true
        }
    }

    override fun onStopJob(p0: JobParameters?): Boolean {
        // TODO("Not yet implemented")
        return true
    }
}