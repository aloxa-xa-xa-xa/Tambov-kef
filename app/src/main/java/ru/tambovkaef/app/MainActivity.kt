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
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
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
        try { CredentialVault.initialize() } catch (_: Exception) {}
        window.statusBarColor = Color.rgb(16, 22, 31)
        window.navigationBarColor = Color.rgb(16, 22, 31)
        window.decorView.systemUiVisibility = 0

        // После заставки водитель сразу попадает на карту — без панели меню и настроек.
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(16, 22, 31)) }
        val map = android.webkit.WebView(this).apply {
            setBackgroundColor(Color.rgb(16, 22, 31))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val show = prefs.getBoolean("map_show_zones", true)
                    val style = prefs.getString("map_style", "standard") ?: "standard"
                    view?.evaluateJavascript("window.applyMapSettings && window.applyMapSettings($show,'$style')", null)
                }
            }
            loadDataWithBaseURL("https://tambov-kaef.local/", demandMapHtml(), "text/html", "UTF-8", null)
        }
        root.addView(map, FrameLayout.LayoutParams(-1, -1))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        fun mapControl(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            textSize = 23f
            isAllCaps = false
            setTextColor(Color.rgb(24, 31, 42))
            background = rounded(Color.WHITE, 16)
            setPadding(0, 0, 0, 0)
            setOnClickListener { action() }
        }
        controls.addView(mapControl("+") {
            map.evaluateJavascript("window.zoomMap && window.zoomMap(1)", null)
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        controls.addView(mapControl("−") {
            map.evaluateJavascript("window.zoomMap && window.zoomMap(-1)", null)
        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { topMargin = dp(8) })
        val controlParams = FrameLayout.LayoutParams(dp(52), -2, Gravity.RIGHT or Gravity.BOTTOM).apply {
            rightMargin = dp(16)
            bottomMargin = dp(104)
        }
        root.addView(controls, controlParams)

        val locate = Button(this).apply {
            text = "◎"
            textSize = 27f
            isAllCaps = false
            contentDescription = "Моё местоположение"
            setTextColor(Color.rgb(24, 31, 42))
            background = rounded(Color.WHITE, 18)
            setOnClickListener { locateMe(map) }
        }
        root.addView(locate, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.RIGHT or Gravity.BOTTOM).apply {
            rightMargin = dp(13)
            bottomMargin = dp(28)
        })

        // Аккуратная кнопка настроек карты в верхнем правом углу.
        val mapSettings = Button(this).apply {
            text = "⚙"
            textSize = 23f
            isAllCaps = false
            contentDescription = "Настройки карты"
            setTextColor(Color.rgb(242, 246, 252))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 23, 31, 45))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(93, 151, 255))
            }
            elevation = dp(5).toFloat()
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                showMapSettings(map)
            }
        }
        root.addView(mapSettings, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.RIGHT or Gravity.TOP).apply {
            rightMargin = dp(16)
            topMargin = dp(42)
        })

        // Заставка сохраняется; после неё открывается только карта.
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(26), dp(30), dp(26), dp(30))
            setBackgroundColor(Color.rgb(3, 4, 6))
            alpha = 0f
        }
        splash.addView(TextView(this).apply {
            text = "🚕"
            textSize = 76f
            gravity = Gravity.CENTER
            setShadowLayer(dp(18).toFloat(), 0f, 0f, Color.rgb(255, 190, 0))
        }, LinearLayout.LayoutParams(-1, dp(126)))
        splash.addView(TextView(this).apply {
            text = "ТАМБОВ"
            textSize = 39f
            setTextColor(Color.rgb(245, 246, 248))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2))
        splash.addView(TextView(this).apply {
            text = "Кэф"
            textSize = 58f
            setTextColor(Color.rgb(255, 196, 0))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2))
        splash.addView(TextView(this).apply {
            text = "Коэффициенты спроса на карте"
            textSize = 15f
            setTextColor(Color.rgb(184, 187, 193))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

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

    private fun showMapSettings(map: android.webkit.WebView) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        val zonesCheck = CheckBox(this).apply {
            text = "Показывать коэффициенты и зоны"
            textSize = 15f
            setTextColor(white)
            buttonTintList = android.content.res.ColorStateList.valueOf(blue)
            isChecked = prefs.getBoolean("map_show_zones", true)
        }
        content.addView(zonesCheck)
        content.addView(TextView(this).apply {
            text = "Оформление карты"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            setPadding(0, dp(14), 0, dp(6))
        })
        val styleGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val standard = RadioButton(this).apply {
            id = View.generateViewId()
            text = "Стандартная"
            textSize = 15f
            setTextColor(white)
            buttonTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        val dark = RadioButton(this).apply {
            id = View.generateViewId()
            text = "Тёмная"
            textSize = 15f
            setTextColor(white)
            buttonTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        styleGroup.addView(standard)
        styleGroup.addView(dark)
        if (prefs.getString("map_style", "standard") == "dark") dark.isChecked = true else standard.isChecked = true
        content.addView(styleGroup)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Настройки карты")
            .setView(content)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val show = zonesCheck.isChecked
                val style = if (dark.isChecked) "dark" else "standard"
                prefs.edit().putBoolean("map_show_zones", show).putString("map_style", style).apply()
                map.evaluateJavascript("window.applyMapSettings && window.applyMapSettings($show,'$style')", null)
                dialog.dismiss()
            }
        }
        dialog.show()
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
        .leaflet-control-attribution{font-size:9px}
        #map.dark-map .leaflet-tile{filter:brightness(.62) invert(1) contrast(1.08) hue-rotate(180deg) saturate(.72)}
        </style></head><body><div id="map"></div>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
        const map=L.map('map').setView([52.7213,41.4523],12);
        window.zoomMap=(delta)=>map.setZoom(map.getZoom()+delta);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
          {maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
        const zones=[
          {p:[52.721,41.452],v:'1,2×',c:'#62c995',r:850},
          {p:[52.735,41.430],v:'1,5×',c:'#e7c64b',r:720},
          {p:[52.707,41.477],v:'1,8×',c:'#ed9847',r:780},
          {p:[52.746,41.468],v:'2,0×',c:'#e45d59',r:650},
          {p:[52.694,41.440],v:'1,3×',c:'#e7c64b',r:600}
        ];
        const zoneLayers=[];
        zones.forEach(z=>{
          const circle=L.circle(z.p,{radius:z.r,color:z.c,weight:2,fillColor:z.c,fillOpacity:.22});
          const marker=L.marker(z.p,{icon:L.divIcon({className:'',html:'<div class="kaef">'+z.v+'</div>',iconSize:[62,30],iconAnchor:[31,15]})})
            .bindPopup('Оценка спроса: <b>'+z.v+'</b><br>Демонстрационное значение');
          zoneLayers.push(circle,marker);
          circle.addTo(map);
          marker.addTo(map);
        });
        window.applyMapSettings=(showZones,style)=>{
          zoneLayers.forEach(layer=>{
            if(showZones && !map.hasLayer(layer)) layer.addTo(map);
            if(!showZones && map.hasLayer(layer)) map.removeLayer(layer);
          });
          document.getElementById('map').classList.toggle('dark-map',style==='dark');
        };
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
