package ru.tambovkaef.app

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.net.Uri
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
import android.widget.FrameLayout
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import java.util.concurrent.Executors
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
    private lateinit var apiStatus: TextView
    private lateinit var clidInput: EditText
    private lateinit var apiKeyInput: EditText
    private val apiExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Create the installation-specific encryption key automatically on first launch.
        try { CredentialVault.initialize() } catch (_: Exception) { /* API vault is optional; monitoring remains available. */ }
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

        page.addView(sectionTitle("Дополнительный источник цен (необязательно)"), margin(top = 22))
        val apiCard = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
        }
        apiCard.addView(TextView(this).apply {
            text = "Мониторинг экрана и история работают без ключей. Эти поля нужны только для отдельного запроса ориентировочных цен через API Яндекса."
            textSize = 12f
            setTextColor(muted)
        })
        clidInput = EditText(this).apply {
            hint = "clid"
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackground(rounded(bg, 10))
        }
        apiCard.addView(clidInput, margin(top = 10))
        apiKeyInput = EditText(this).apply {
            hint = "API-ключ"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackground(rounded(bg, 10))
        }
        apiCard.addView(apiKeyInput, margin(top = 8))
        apiCard.addView(Button(this).apply {
            text = "Сохранить ключи"
            isAllCaps = false
            setOnClickListener {
                try {
                    CredentialVault.save(this@MainActivity, clidInput.text.toString().trim(), apiKeyInput.text.toString().trim())
                    apiKeyInput.text.clear()
                    apiStatus.text = "Доступ сохранён на этом устройстве."
                    Toast.makeText(this@MainActivity, "Ключи сохранены", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    apiStatus.text = "Не удалось сохранить доступ: ${e.message ?: "ошибка"}"
                }
            }
        }, margin(top = 8))
        apiCard.addView(Button(this).apply {
            text = "Проверить цены по GPS"
            isAllCaps = false
            setOnClickListener { fetchYandexPrices() }
        }, margin(top = 6))
        apiStatus = TextView(this).apply {
            text = "Без ключей можно пользоваться мониторингом экрана Яндекс Про. API можно подключить позже."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(9), 0, 0)
        }
        apiCard.addView(apiStatus)
        page.addView(apiCard, margin(top = 8))
        try {
            val saved = CredentialVault.load(this)
            clidInput.setText(saved?.first.orEmpty())
            if (saved != null) apiStatus.text = "Ключи уже сохранены. Для повторной проверки нажми кнопку ниже."
        } catch (_: Exception) {
            apiStatus.text = "Не удалось прочитать сохранённый доступ. Введи ключи заново."
        }

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
            loadDataWithBaseURL("https://tambov-kaef.local/", demandMapHtml(), "text/html", "UTF-8", null)
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
            text = "Числа на карте — демонстрационная оценка спроса, не реальные коэффициенты Яндекс Про. Реальные значения появятся после подключения источника данных."
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
            text = "Оценочные значения на карте показаны для демонстрации. Они не являются данными Яндекс Про."
            textSize = 11f
            setTextColor(muted)
            setPadding(0, dp(24), 0, dp(4))
        })
        val screen = FrameLayout(this)
        screen.addView(root, FrameLayout.LayoutParams(-1, -1))

        // Premium black-and-gold branded intro overlay; the dashboard is prepared underneath.
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(26), dp(30), dp(26), dp(30))
            setBackgroundColor(Color.rgb(3, 4, 6))
            alpha = 0f
        }
        val halo = TextView(this).apply {
            text = " "
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.rgb(226, 174, 20))
            }
        }
        val taxi = TextView(this).apply {
            text = "🚕"
            textSize = 76f
            gravity = Gravity.CENTER
            setShadowLayer(dp(18).toFloat(), 0f, 0f, Color.rgb(255, 190, 0))
        }
        splash.addView(taxi, LinearLayout.LayoutParams(-1, dp(126)))
        splash.addView(TextView(this).apply {
            text = "ТАМБОВ"
            textSize = 39f
            setTextColor(Color.rgb(245, 246, 248))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = .015f
            setPadding(0, dp(15), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        splash.addView(TextView(this).apply {
            text = "Кэф"
            textSize = 58f
            setTextColor(Color.rgb(255, 196, 0))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setShadowLayer(dp(12).toFloat(), 0f, dp(2).toFloat(), Color.rgb(130, 85, 0))
        }, LinearLayout.LayoutParams(-1, -2))
        val goldLine = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(255, 190, 0))
                cornerRadius = dp(3).toFloat()
            }
        }
        splash.addView(goldLine, LinearLayout.LayoutParams(dp(190), dp(3)).apply {
            gravity = Gravity.CENTER
            topMargin = dp(8)
        })
        splash.addView(TextView(this).apply {
            text = "Следи за коэффициентами\\nв Яндекс Такси"
            textSize = 16f
            setTextColor(Color.rgb(184, 187, 193))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif", Typeface.ITALIC)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(22), 0, dp(10))
        }, LinearLayout.LayoutParams(-1, -2))
        splash.addView(TextView(this).apply {
            text = "▪  ▪  ▪  ▪  ▪  ▪"
            textSize = 17f
            letterSpacing = .2f
            setTextColor(Color.rgb(255, 196, 0))
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        screen.addView(splash, FrameLayout.LayoutParams(-1, -1))
        setContentView(screen)
        renderHistory()
        renderOcrDebug()

        splash.animate().alpha(1f).setDuration(220L).start()
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && splash.parent != null) {
                splash.animate().alpha(0f).setDuration(320L).withEndAction {
                    (splash.parent as? FrameLayout)?.removeView(splash)
                }.start()
            }
        }, 1050L)
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
        if (!Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
            @Suppress("DEPRECATION")
            startActivityForResult(overlayIntent, 703)
            Toast.makeText(this, "Разреши показ окна поверх других приложений, затем мониторинг продолжится.", Toast.LENGTH_LONG).show()
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), 702)
    }

    @Deprecated("Uses Activity result callback for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 703) {
            if (Settings.canDrawOverlays(this)) requestScreenCapture()
            else Toast.makeText(this, "Без разрешения на окно поверх приложений плавающее окошко не появится.", Toast.LENGTH_LONG).show()
        }
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
    private fun demandMapHtml(): String = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
        <style>html,body,#map{height:100%;width:100%;margin:0;background:#17202e}
        .kaef{background:#101722;border:2px solid #f4c542;border-radius:12px;color:#ffd34d;
        font:bold 14px Arial;text-align:center;padding:5px 7px;box-shadow:0 2px 8px #0009;white-space:nowrap}
        .leaflet-control-attribution{font-size:9px}</style></head><body><div id="map"></div>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
        const map=L.map('map').setView([52.7213,41.4523],12);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
          {maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
        const zones=[
          {p:[52.721,41.452],v:'1,2×',c:'#62c995',r:850},
          {p:[52.735,41.430],v:'1,5×',c:'#e7c64b',r:720},
          {p:[52.707,41.477],v:'1,8×',c:'#ed9847',r:780},
          {p:[52.746,41.468],v:'2,0×',c:'#e45d59',r:650},
          {p:[52.694,41.440],v:'1,3×',c:'#e7c64b',r:600}
        ];
        zones.forEach(z=>{
          L.circle(z.p,{radius:z.r,color:z.c,weight:2,fillColor:z.c,fillOpacity:.22}).addTo(map);
          L.marker(z.p,{icon:L.divIcon({className:'',html:'<div class="kaef">'+z.v+'</div>',iconSize:[62,30],iconAnchor:[31,15]})})
            .bindPopup('Оценка спроса: <b>'+z.v+'</b><br>Демонстрационное значение').addTo(map);
        });
        let driverMarker=null;
        window.setDriverLocation=(lat,lon)=>{
          if(driverMarker) map.removeLayer(driverMarker);
          driverMarker=L.marker([lat,lon]).addTo(map).bindPopup('Ваше местоположение').openPopup();
          map.setView([lat,lon],14);
        };
        </script></body></html>
    """.trimIndent()

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
            map.evaluateJavascript("if(window.setDriverLocation){window.setDriverLocation($lat,$lon);}", null)
            Toast.makeText(this, "Нашёл твоё положение", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Нет разрешения на геолокацию", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchYandexPrices() {
        val clid = clidInput.text.toString().trim()
        val key = apiKeyInput.text.toString().trim()
        if (clid.isNotBlank() && key.isNotBlank()) {
            try {
                CredentialVault.save(this, clid, key)
                apiKeyInput.text.clear()
            } catch (e: Exception) {
                apiStatus.text = "Ошибка сохранения ключей: ${e.message ?: "ошибка"}"
                return
            }
        }
        val credentials = try { CredentialVault.load(this) } catch (_: Exception) { null }
        if (credentials == null) {
            apiStatus.text = "Сначала введи и сохрани clid и API-ключ."
            return
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 701)
            apiStatus.text = "Разреши геолокацию и снова нажми «Проверить цены по GPS»."
            return
        }
        val location = try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) { null }
        if (location == null) {
            apiStatus.text = "Геопозиция пока недоступна. Включи GPS, подожди определения координат и повтори."
            return
        }
        apiStatus.text = "Запрашиваю данные Яндекса…"
        apiExecutor.execute {
            try {
                val prices = YandexPriceClient(credentials.first, credentials.second)
                    .fetchPointPrices(location.longitude, location.latitude)
                runOnUiThread {
                    apiStatus.text = if (prices.isEmpty()) {
                        "Яндекс не вернул тарифы для этой точки. Проверь регион и доступ API."
                    } else {
                        val lines = prices.joinToString("\n") { item ->
                            val ratio = item.multiplier?.let { String.format(Locale.US, " • индекс цены %.2fx", it) } ?: ""
                            "${item.title}: ${String.format(Locale.US, "%.0f", item.price)} ${item.currency}${ratio}"
                        }
                        lines + "\n\nЭто ориентир по цене подачи с надбавкой, а не коэффициент Яндекс Про. Фактическая цена зависит от спроса и доступности машин."
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    apiStatus.text = "Ошибка API: ${e.message ?: "не удалось получить данные"}"
                }
            }
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
