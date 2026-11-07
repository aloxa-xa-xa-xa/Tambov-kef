package ru.tambov.promokody

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object Updates {
    const val CHANNEL_ID = "new_promo_codes"
    private const val UNIQUE_WORK = "promo_codes_periodic_search"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PromoSearchWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PromoSearchWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
