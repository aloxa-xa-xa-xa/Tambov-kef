package ru.tambovkaef.app

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(12, 17, 27)
    private val panel = Color.rgb(23, 31, 45)
    private val muted = Color.rgb(151, 164, 183)
    private val white = Color.rgb(242, 246, 252)
    private val green = Color.rgb(67, 211, 145)
    private val blue = Color.rgb(93, 151, 255)
    private val prefs by lazy { getSharedPreferences("tambov_kaef", MODE_PRIVATE) }
    private lateinit var historyBox: LinearLayout
    private lateinit var emptyHistory: TextView
    private lateinit var ocrDebug: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            // Android 15 may draw app content behind system bars.
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }
        }
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        page.addView(TextView(this).apply {
            text = "ТАМБОВ  •  ДЛЯ ВОДИТЕЛЕЙ"
            textSize = 11f
            letterSpacing = .13f
            setTextColor(green)
            typeface = Typeface.DEFAULT_BOLD
        })
        page.addView(TextView(this).apply {
            text = "Тамбов Кэф"
            textSize = 31f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(7), 0, dp(3))
        })
        page.addView(TextView(this).apply {
            text = "Автоматический мониторинг спроса в разработке"
            textSize = 14f
            setTextColor(muted)
        })

        val statusCard = card()
        statusCard.orientation = LinearLayout.VERTICAL
        statusCard.setPadding(dp(16), dp(15), dp(16), dp(15))
        statusCard.addView(TextView(this).apply {
            text = "ТЕКУЩИЙ СТАТУС"
            textSize = 10f
            letterSpacing = .12f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
        })
        statusCard.addView(TextView(this).apply {
            text = "Автоматизация в разработке"
            textSize = 19f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(5), 0, dp(3))
        })
        statusCard.addView(TextView(this).apply {
            text = "Ручной ввод отключён. Мониторинг читает экран только после разрешения Android; точность распознавания ещё проверяется."
            textSize = 12f
            setTextColor(muted)
        })
        val monitorButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        monitorButtons.addView(Button(this).apply {
            text = "Запустить мониторинг"
            isAllCaps = false
            setOnClickListener { requestScreenCapture() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        monitorButtons.addView(Button(this).apply {
            text = "Остановить"
            isAllCaps = false
            setOnClickListener { stopService(Intent(this@MainActivity, ScreenCaptureService::class.java)) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        statusCard.addView(monitorButtons, margin(top = 12))
        page.addView(statusCard, margin(top = 22))

        val debugCard = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
        }
        debugCard.addView(TextView(this).apply {
            text = "ЧТО РАСПОЗНАЛ МОНИТОРИНГ"
            textSize = 10f
            letterSpacing = .1f
            setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD
        })
        ocrDebug = TextView(this).apply {
            text = "Пока нет данных. Запусти мониторинг и открой экран коэффициентов в Яндекс Про."
            textSize = 12f
            setTextColor(white)
            setPadding(0, dp(8), 0, dp(8))
        }
        debugCard.addView(ocrDebug)
        debugCard.addView(Button(this).apply {
            text = "Обновить распознанный текст"
            isAllCaps = false
            setOnClickListener { renderOcrDebug(); renderHistory() }
        })
        page.addView(debugCard, margin(top = 12))

        page.addView(sectionTitle("Карта Тамбова"), margin(top = 20))
        val mapCard = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val map = android.webkit.WebView(this).apply {
            setBackgroundColor(panel)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webViewClient = android.webkit.WebViewClient()
            loadUrl("https://www.openstreetmap.org/export/embed.html?bbox=41.38%2C52.68%2C41.53%2C52.77&layer=mapnik&marker=52.7213%2C41.4523")
        }
        mapCard.addView(map, LinearLayout.LayoutParams(-1, dp(250)))
        val gpsButton = Button(this).apply {
            text = "◎  Где я нахожусь"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.rgb(8, 26, 22))
            background = rounded(green, 14)
            setOnClickListener { locateMe(map) }
        }
        mapCard.addView(gpsButton, margin(top = 10))
        mapCard.addView(TextView(this).apply {
            text = "Карта города. Слой спроса появится после подключения обмена наблюдениями между водителями."
            textSize = 11f
            setTextColor(muted)
            setPadding(dp(4), dp(9), dp(4), dp(2))
        })
        page.addView(mapCard, margin(top = 8))

        page.addView(sectionTitle("История наблюдений"), margin(top = 24))
        emptyHistory = TextView(this).apply {
            text = "История заполнится после подключения автоматического считывания коэффициентов."
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(2), dp(8), dp(2), dp(8))
        }
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(emptyHistory)
        page.addView(historyBox)

        page.addView(TextView(this).apply {
            text = "Карта использует OpenStreetMap. Слой спроса появится после подключения обмена наблюдениями."
            textSize = 11f
            setTextColor(muted)
            setPadding(0, dp(24), 0, dp(4))
        })
        setContentView(root)
        renderHistory()
        renderOcrDebug()
    }

    override fun onResume() {
        super.onResume()
        if (::historyBox.isInitialized) renderHistory()
        if (::ocrDebug.isInitialized) renderOcrDebug()
    }

    private fun renderOcrDebug() {
        if (!::ocrDebug.isInitialized) return
        val text = prefs.getString("last_ocr_text", "").orEmpty()
        val time = prefs.getLong("last_ocr_time", 0L)
        ocrDebug.text = if (text.isBlank()) {
            "Пока нет распознанного текста. Запусти мониторинг, подтверди системное разрешение и открой экран коэффициентов в Яндекс Про."
        } else {
            val stamp = SimpleDateFormat("HH:mm:ss", Locale("ru")).format(Date(time))
            "Последнее чтение: $stamp\n\n" + text.take(1800)
        }
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), 702)
    }

    @Deprecated("Uses Activity result callback for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 702) {
            if (resultCode == RESULT_OK && data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenCaptureService.EXTRA_DATA, data)
                }
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
                Toast.makeText(this, "Мониторинг экрана запущен", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Без разрешения Android мониторинг не запустится", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun locateMe(map: android.webkit.WebView) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 701)
            Toast.makeText(this, "Разреши доступ к местоположению", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            val location: Location? = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location == null) {
                Toast.makeText(this, "Положение пока недоступно. Включи GPS и нажми ещё раз.", Toast.LENGTH_LONG).show()
                return
            }
            val lat = location.latitude
            val lon = location.longitude
            val delta = 0.025
            val bbox = "${lon-delta},${lat-delta},${lon+delta},${lat+delta}"
            map.loadUrl("https://www.openstreetmap.org/export/embed.html?bbox=${android.net.Uri.encode(bbox)}&layer=mapnik&marker=$lat%2C$lon")
            Toast.makeText(this, "Нашёл твоё положение", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Нет разрешения на геолокацию", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readHistory(): JSONArray = try {
        JSONArray(prefs.getString("history", "[]"))
    } catch (_: Exception) { JSONArray() }

    private fun renderHistory() {
        historyBox.removeAllViews()
        val items = readHistory()
        emptyHistory.visibility = if (items.length() == 0) View.VISIBLE else View.GONE
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val row = card().apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(12), dp(15), dp(12))
            }
            val date = SimpleDateFormat("dd.MM.yyyy  •  HH:mm", Locale("ru")).format(Date(item.optLong("time")))
            row.addView(TextView(this).apply {
                text = date
                textSize = 11f
                setTextColor(muted)
            })
            val values = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(7), 0, 0)
            }
            values.addView(valueText("Эконом", item.optString("economy").ifEmpty { "—" }, green), LinearLayout.LayoutParams(0, -2, 1f))
            values.addView(valueText("Комфорт", item.optString("comfort").ifEmpty { "—" }, blue), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(values)
            historyBox.addView(row, margin(top = 8))
        }
    }

    private fun valueText(label: String, value: String, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 11f
            setTextColor(muted)
        })
        addView(TextView(this@MainActivity).apply {
            text = value
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
        })
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 19f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
        setPadding(0, dp(4), 0, dp(3))
    }

    private fun card() = LinearLayout(this).apply {
        background = rounded(panel, 18)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun margin(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
