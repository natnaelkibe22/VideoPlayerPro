package com.natkibe.playerpro.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker that refreshes the video library in the background.
 * Runs on [Dispatchers.Default] by default (CoroutineWorker base); the
 * repository internally switches to [Dispatchers.IO] for all I/O work.
 */
class LibraryRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        VideoLibraryRepository(applicationContext).refreshNow()
        Result.success()
    } catch (t: Throwable) {
        // Keep the UI stable. A failed USB/media refresh should never crash the app.
        // Use retry() so WorkManager re-attempts with backoff when a USB device reappears.
        Result.retry()
    }
}
