package ru.tambov.promokody

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var results: LinearLayout

    private val blue = Color.rgb(43, 91, 220)
    private val ink = Color.rgb(28, 43, 72)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermission()
        Updates.schedule(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(241, 245, 255))
        }

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(76), dp(20), dp(32))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            background = panel(Color.rgb(43, 91, 220), 26)
        }
        header.addView(TextView(this).apply {
            text = "✨  ПРОМОКОДЫ ТАМБОВ"
            textSize = 23f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Ищем предложения для водителей, курьеров и пассажиров"
            textSize = 14f
            setTextColor(Color.rgb(231, 238, 255))
            setPadding(0, dp(9), 0, 0)
        })
        body.addView(header, margin(bottom = 20))

        val searchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = panel(Color.WHITE, 22)
        }
        searchPanel.addView(TextView(this).apply {
            text = "АВТОМАТИЧЕСКИЙ ПОИСК"
            textSize = 12f
            letterSpacing = .08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(blue)
        })
        status = TextView(this).apply {
            text = "Поиск включён — проверяем открытые источники примерно раз в 12 часов."
            textSize = 15f
            setTextColor(ink)
            setPadding(0, dp(8), 0, dp(16))
        }
        searchPanel.addView(status)

        val searchButton = Button(this).apply {
            text = "🔎   ИСКАТЬ СЕЙЧАС"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = panel(blue, 16)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                Updates.runNow(this@MainActivity)
                status.text = "Поиск запущен. Обновите экран через несколько секунд — результаты появятся ниже."
            }
        }
        searchPanel.addView(searchButton, LinearLayout.LayoutParams(-1, dp(54)))
        body.addView(searchPanel, margin(bottom = 22))

        body.addView(TextView(this).apply {
            text = "Найденные промокоды"
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
            setPadding(dp(2), dp(4), dp(2), dp(12))
        })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results)

        val info = TextView(this).apply {
            text = "Поиск по публичным сайтам • Tor не используется. Найденные коды — только кандидаты: проверьте срок действия и условия в приложении Яндекса."
            textSize = 13f
            setTextColor(Color.rgb(91, 104, 130))
            setPadding(dp(6), dp(12), dp(6), 0)
        }
        body.addView(info)
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
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(28), dp(20), dp(28))
                background = panel(Color.WHITE, 22)
            }
            empty.addView(TextView(this).apply {
                text = "🎁"
                textSize = 34f
                gravity = Gravity.CENTER
            })
            empty.addView(TextView(this).apply {
                text = "Здесь появятся найденные коды"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ink)
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(5))
            })
            empty.addView(TextView(this).apply {
                text = "Нажмите «Искать сейчас», и мы покажем результаты прямо в этом окне."
                textSize = 13f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
            })
            results.addView(empty)
            return
        }

        status.text = "Найдено совпадений: ${data.length()} • обновлено: ${prefs.getString("last_search", "неизвестно")}"
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val code = item.optString("code")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(17), dp(17), dp(17), dp(17))
                background = panel(Color.WHITE, 22)
            }

            val topLine = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topLine.addView(TextView(this).apply {
                text = if (code.isNotBlank()) "🎟  ВОЗМОЖНЫЙ ПРОМОКОД" else "🔎  НАЙДЕН ИСТОЧНИК"
                textSize = 11f
                letterSpacing = .04f
                setTypeface(null, Typeface.BOLD)
                setTextColor(blue)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            topLine.addView(TextView(this).apply {
                text = "НЕ ПРОВЕРЕН"
                textSize = 9f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(166, 103, 22))
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = panel(Color.rgb(255, 243, 218), 30)
            })
            card.addView(topLine)

            if (code.isNotBlank()) {
                val codeBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(17), dp(12), dp(17))
                    background = panel(Color.rgb(238, 243, 255), 16)
                }
                codeBox.addView(TextView(this).apply {
                    text = code
                    textSize = 25f
                    letterSpacing = .06f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ink)
                    gravity = Gravity.CENTER
                })
                val copyButton = Button(this).apply {
                    text = "📋  Скопировать код"
                    isAllCaps = false
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    background = panel(blue, 13)
                    setOnClickListener {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Промокод", code))
                        Toast.makeText(this@MainActivity, "Код скопирован", Toast.LENGTH_SHORT).show()
                    }
                }
                codeBox.addView(copyButton, LinearLayout.LayoutParams(-1, dp(46)).apply {
                    topMargin = dp(12)
                })
                card.addView(codeBox, margin(top = 13, bottom = 10))
            }

            val title = item.optString("title")
            val snippet = item.optString("snippet")
            card.addView(TextView(this).apply {
                text = listOf(title, snippet).filter { it.isNotBlank() }.joinToString("\n")
                textSize = 14f
                setTextColor(Color.rgb(73, 84, 108))
                setPadding(0, dp(7), 0, dp(9))
            })

            val url = item.optString("url")
            val sourceButton = Button(this).apply {
                text = "Открыть источник  ↗"
                isAllCaps = false
                textSize = 13f
                setTextColor(blue)
                background = panel(Color.rgb(232, 239, 255), 13)
                isEnabled = url.startsWith("http")
                alpha = if (isEnabled) 1f else .5f
                setOnClickListener {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
            }
            card.addView(sourceButton, LinearLayout.LayoutParams(-1, dp(46)))
            results.addView(card, margin(bottom = 14))
        }
    }

    private fun panel(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun margin(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            if (top > 0) topMargin = dp(top)
            if (bottom > 0) bottomMargin = dp(bottom)
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
