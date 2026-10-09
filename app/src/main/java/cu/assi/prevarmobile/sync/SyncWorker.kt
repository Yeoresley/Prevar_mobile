package cu.assi.prevarmobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cu.assi.prevarmobile.PrevarMobileApp

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PrevarMobileApp
        if (!app.repository.isPaired()) return Result.success()
        val result = app.repository.synchronize()
        return if (result.success) Result.success() else Result.retry()
    }
}
