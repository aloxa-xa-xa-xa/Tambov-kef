package ru.tambov.promokody

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PromoSearchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val queries = listOf(
                "промокод Яндекс Про водитель Тамбов",
                "промокод Яндекс Про курьер Тамбов",
                "промокод Яндекс Go пассажир Тамбов"
            )
            val found = linkedMapOf<String, JSONObject>()
            for (query in queries) {
                val searchUrl = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
                val doc = Jsoup.connect(searchUrl).userAgent("Mozilla/5.0 (Android) PromokodyTambov/1.2").timeout(15000).get()
                doc.select(".result").take(10).forEach { result ->
                    val title = result.selectFirst(".result__title")?.text().orEmpty()
                    val snippet = result.selectFirst(".result__snippet")?.text().orEmpty()
                    val anchor = result.selectFirst(".result__title a")
                    var link = anchor?.absUrl("href").orEmpty()
                    if (link.contains("uddg=")) {
                        val encoded = link.substringAfter("uddg=").substringBefore("&")
                        link = try { URLDecoder.decode(encoded, "UTF-8") } catch (_: Exception) { link }
                    }
                    val combined = "$title $snippet"
                    val code = Regex("(?i)(?<![A-ZА-Я0-9])[A-ZА-Я0-9]{4,16}(?![A-ZА-Я0-9])")
                        .findAll(combined).map { it.value }
                        .firstOrNull { it.any(Char::isDigit) && it.any(Char::isLetter) }.orEmpty()
                    if (title.isNotBlank() || link.isNotBlank()) {
                        val key = if (link.isNotBlank()) link else title
                        found.putIfAbsent(key, JSONObject().put("code", code).put("title", title)
                            .put("snippet", snippet).put("url", link).put("query", query))
                    }
                }
            }
            val prefs = applicationContext.getSharedPreferences("promo_codes", Context.MODE_PRIVATE)
            val old = try { JSONArray(prefs.getString("search_results", "[]")) } catch (_: Exception) { JSONArray() }
            val merged = linkedMapOf<String, JSONObject>()
            for (i in 0 until old.length()) {
                val item = old.optJSONObject(i) ?: continue
                merged[item.optString("url").ifBlank { item.optString("title") }] = item
            }
            val fresh = found.keys.filter { !merged.containsKey(it) }
            found.forEach { (key, value) -> merged[key] = value }
            val output = JSONArray()
            merged.values.take(100).forEach { output.put(it) }
            val time = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            prefs.edit().putString("search_results", output.toString()).putString("last_search", time).apply()

            if (fresh.isNotEmpty()) {
                val notification = NotificationCompat.Builder(applicationContext, Updates.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Новые результаты поиска")
                    .setContentText("Новых страниц: ${fresh.size}. Откройте приложение, чтобы посмотреть ссылки.")
                    .setAutoCancel(true).build()
                val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(2026, notification)
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
