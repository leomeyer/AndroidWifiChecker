package de.leomeyer.wifichecker

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class CheckJobService : JobService() {
    companion object {
        const val JOB_ID: Int = 0x20000
        var runCounter: Int = 0

        fun checkPeriodicJob(context: Context, sharedPref: SharedPreferences) {
            // periodic check enabled?
            val period = sharedPref.getString(WifiCheckerService.PREF_PERIODIC_CHECK, "0")?.toInt()
            if (period == null)
                return
            if (period > 0) {
                // reschedule next job execution
                val jobInfo = JobInfo.Builder(CheckJobService.JOB_ID, ComponentName(context, CheckJobService::class.java))
                    .setMinimumLatency(period.toLong())
                    .setOverrideDeadline(period.toLong() + 1000)
                    .build()
                var jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.schedule(jobInfo)
                Log.d(WifiCheckerService.SERVICE_TAG, "Periodic check job scheduled to run in $period milliseconds")
            } else {
                Log.d(WifiCheckerService.SERVICE_TAG, "Periodic check job is disabled")
            }
        }
    }
    override fun onStartJob(p0: JobParameters?): Boolean {
        runCounter++
        if (WifiCheckerService.instance != null) {
            WifiCheckerService.instance?.checkWifi()
            Log.d(WifiCheckerService.SERVICE_TAG, "Check completed")
        } else {
            Log.d(WifiCheckerService.SERVICE_TAG, "Check failed: WifiCheckerService instance was null")
        }
        return true
    }

    override fun onStopJob(p0: JobParameters?): Boolean {
        // TODO("Not yet implemented")
        return true
    }
}