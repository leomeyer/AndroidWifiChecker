package de.leomeyer.wifichecker

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import de.leomeyer.wifichecker.WifiCheckerService.Companion.PREF_START_ON_BOOT

class StartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // check whether to start on boot
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sharedPref.getBoolean(PREF_START_ON_BOOT, false))
            return

        // schedule job for service start
        val jobInfo = JobInfo.Builder(StartJobService.JOB_ID, ComponentName(context, StartJobService::class.java))
            .setMinimumLatency(5000)
            .setBackoffCriteria(5000, JobInfo.BACKOFF_POLICY_LINEAR)
            .build()
        var jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.schedule(jobInfo)
    }
}
