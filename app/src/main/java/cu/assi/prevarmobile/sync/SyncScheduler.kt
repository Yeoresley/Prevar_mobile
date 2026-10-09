package cu.assi.prevarmobile.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val WORK_NAME = "prevar-mobile-sync"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }

    fun runNow(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$WORK_NAME-now", ExistingWorkPolicy.REPLACE, request)
    }
}
