package com.epsilonmusic.app.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TasteMixWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            TasteMixHelper.generateTasteMix(applicationContext)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
