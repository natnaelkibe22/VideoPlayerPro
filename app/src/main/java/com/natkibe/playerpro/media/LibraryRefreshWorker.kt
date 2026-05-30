package com.natkibe.playerpro.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class LibraryRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        VideoLibraryRepository(applicationContext).refreshNow()
        Result.success()
    } catch (t: Throwable) {
        // Keep the UI stable. A failed USB/media refresh should never crash the app.
        Result.retry()
    }
}
