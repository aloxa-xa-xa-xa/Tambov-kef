package ru.tambov.promokody

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.jsoup.Jsoup

class PromoSearchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val queries = listOf(
                "промокод Яндекс Про водитель Тамбов",
                "промокод Яндекс Про курьер Тамбов",
                "промокод Яндекс Go пассажир Тамбов"
            )
            val found = linkedMapOf<String, String>()
            for (query in queries) {
                val url = "https://html.duckduckgo.com/html/?q=" +
                    java.net.URLEncoder.encode(query, "UTF-8")
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Android) PromokodyTambov/1.1")
                    .timeout(15000).get()
                doc.select(".result").take(10).forEach { result ->
                    val title = result.selectFirst(".result__title")?.text().orEmpty()
                    val snippet = result.selectFirst(".result__snippet")?.text().orEmpty()
                    val link = result.selectFirst(".result__url")?.text().orEmpty()
                    val text = "$title\n$snippet\n$link"
                    Regex("(?i)(?<![A-ZА-Я0-9])[A-ZА-Я0-9]{4,16}(?![A-ZА-Я0-9])")
                        .findAll(text).forEach { match ->
                            val code = match.value
                            if (code.any { it.isDigit() } && code.any { it.isLetter() }) {
                                found.putIfAbsent(code, text.take(240))
                            }
                        }
                }
            }
            val prefs = applicationContext.getSharedPreferences("promo_codes", Context.MODE_PRIVATE)
            val previous = prefs.getStringSet("known_codes", emptySet()) ?: emptySet()
            val fresh = found.keys - previous
            prefs.edit().putStringSet("known_codes", (previous + found.keys).toSet()).apply()

            if (fresh.isNotEmpty()) {
                val notification = NotificationCompat.Builder(applicationContext, Updates.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Найдены новые возможные промокоды")
                    .setContentText("Новых совпадений: ${fresh.size}. Проверьте условия и срок действия.")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                        fresh.take(5).joinToString(", ") +
                            "\nКоды не проверены; проверьте их в приложении сервиса."
                    ))
                    .setAutoCancel(true)
                    .build()
                val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(2026, notification)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
