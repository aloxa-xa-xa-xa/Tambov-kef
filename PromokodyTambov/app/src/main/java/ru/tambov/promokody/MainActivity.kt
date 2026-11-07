package ru.tambov.promokody

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var results: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermission()
        Updates.schedule(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(239, 244, 255))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 54, 24, 26)
            setBackgroundColor(Color.rgb(42, 91, 210))
        }
        header.addView(TextView(this).apply {
            text = "❔  Промокоды Тамбов"
            textSize = 27f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Поиск в открытом интернете • без Tor"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 0)
        })
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 28)
        }
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(45, 55, 75))
            text = "Автопоиск включён — примерно раз в 12 часов."
            setPadding(4, 4, 4, 12)
        }
        body.addView(status)
        body.addView(Button(this).apply {
            text = "🔎  Искать сейчас"
            textSize = 16f
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(42, 91, 210))
            setOnClickListener {
                Updates.runNow(this@MainActivity)
                status.text = "Поиск запущен. Результаты появятся здесь после завершения."
            }
        })
        body.addView(TextView(this).apply {
            text = "Результаты поиска"
            textSize = 21f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(28, 43, 72))
            setPadding(2, 22, 2, 10)
        })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results)
        body.addView(TextView(this).apply {
            text = "Категории: водители Яндекс Про • курьеры • пассажиры Яндекс Go. Коды не проверены Яндексом — обязательно проверьте условия и срок действия."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(4, 18, 4, 4)
        })
        val scroll = ScrollView(this).apply { addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        showResults()
    }

    override fun onResume() {
        super.onResume()
        if (::results.isInitialized) showResults()
    }

    private fun showResults() {
        results.removeAllViews()
        val prefs = getSharedPreferences("promo_codes", MODE_PRIVATE)
        val data = try { JSONArray(prefs.getString("search_results", "[]")) } catch (_: Exception) { JSONArray() }
        if (data.length() == 0) {
            results.addView(TextView(this).apply {
                text = "Пока результатов нет. Нажмите «Искать сейчас» — найденные страницы появятся здесь."
                textSize = 15f
                setTextColor(Color.GRAY)
                setPadding(14, 18, 14, 18)
                setBackgroundColor(Color.WHITE)
            })
            return
        }
        status.text = "Найдено страниц: ${data.length()}. Последнее обновление: ${prefs.getString("last_search", "неизвестно")}"
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundColor(Color.WHITE)
            }
            card.addView(TextView(this).apply {
                text = item.optString("code").let { if (it.isNotBlank()) "Возможный код: $it" else "Найденная страница" }
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(42, 91, 210))
            })
            card.addView(TextView(this).apply {
                text = item.optString("title") + "\n" + item.optString("snippet")
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, 8, 0, 8)
            })
            val url = item.optString("url")
            val link = TextView(this).apply {
                text = if (url.isNotBlank()) "Открыть источник ↗\n$url" else "Ссылка не распознана"
                textSize = 14f
                setTextColor(Color.rgb(20, 90, 190))
                if (url.startsWith("http")) setOnClickListener {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
            }
            card.addView(link)
            card.addView(TextView(this).apply {
                text = "Статус: не проверен"
                textSize = 12f
                setTextColor(Color.rgb(170, 105, 20))
                setPadding(0, 8, 0, 0)
            })
            val params = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
            results.addView(card, params)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(Updates.CHANNEL_ID, "Новые промокоды", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        }
    }
}
