package ru.tambovkaef.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

class MapActivity : Activity() {
    private lateinit var map: WebView
    private val prefs by lazy { getSharedPreferences("tambov_kaef", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(245, 247, 250)
        window.navigationBarColor = Color.rgb(245, 247, 250)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val root = FrameLayout(this)
        map = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            setBackgroundColor(Color.rgb(235, 239, 243))
            loadDataWithBaseURL("https://tambov-kaef.local/", mapHtml(), "text/html", "UTF-8", null)
        }
        root.addView(map, FrameLayout.LayoutParams(-1, -1))
        val zoom = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14).toFloat() }
            elevation = dp(5).toFloat()
        }
        zoom.addView(control("+") { map.evaluateJavascript("map.zoomIn()", null) })
        zoom.addView(android.view.View(this).apply { setBackgroundColor(0xFFE7EAF0.toInt()) }, android.widget.LinearLayout.LayoutParams(dp(42), dp(1)))
        zoom.addView(control("−") { map.evaluateJavascript("map.zoomOut()", null) })
        root.addView(zoom, FrameLayout.LayoutParams(dp(46), dp(94), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(14) })
        val locate = TextView(this).apply {
            text = "◎"; textSize = 27f; gravity = Gravity.CENTER
            setTextColor(0xFF1E293B.toInt())
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
            elevation = dp(5).toFloat()
            setOnClickListener { locateMe() }
        }
        root.addView(locate, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.END).apply { marginEnd = dp(14); bottomMargin = dp(26) })
        val splash = TextView(this).apply {
            text = "ТАМБОВ КЭФ"; textSize = 27f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(8, 13, 22))
        }
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        handler.postDelayed({ splash.animate().alpha(0f).setDuration(260).withEndAction { root.removeView(splash) }.start() }, 650L)
    }

    private fun control(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 27f; gravity = Gravity.CENTER
        setTextColor(0xFF1E293B.toInt())
        setOnClickListener { action() }
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(44), dp(45))
    }

    private fun locateMe() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 701)
            return
        }
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val loc: Location? = (if (fine) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc == null) {
                Toast.makeText(this, "Местоположение пока недоступно. Включи геолокацию и попробуй ещё раз.", Toast.LENGTH_LONG).show()
                return
            }
            map.evaluateJavascript("window.centerOn(\${loc.latitude},\${loc.longitude})", null)
        } catch (_: SecurityException) {
            Toast.makeText(this, "Нет разрешения на геолокацию", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) locateMe()
    }

    private fun mapHtml(): String {
        val economy = prefs.getString("last_saved_economy", "").orEmpty()
        val comfort = prefs.getString("last_saved_comfort", "").orEmpty()
        val lat = prefs.getString("last_saved_lat", "").orEmpty()
        val lon = prefs.getString("last_saved_lon", "").orEmpty()
        val time = prefs.getLong("last_saved_ocr_time", 0L)
        val hasPoint = lat.isNotBlank() && lon.isNotBlank() && (economy.isNotBlank() || comfort.isNotBlank())
        val safeLat = lat.toDoubleOrNull() ?: 52.7213
        val safeLon = lon.toDoubleOrNull() ?: 41.4523
        val labels = listOfNotNull(economy.takeIf { it.isNotBlank() }?.let { "Эконом x$it" }, comfort.takeIf { it.isNotBlank() }?.let { "Комфорт x$it" }).joinToString(" • ")
        val stamp = if (time > 0L) java.text.SimpleDateFormat("HH:mm", java.util.Locale("ru")).format(java.util.Date(time)) else ""
        val marker = if (hasPoint) """const marker=L.marker([$safeLat,$safeLon]).addTo(map);marker.bindPopup('<b>$labels</b><br>Последнее распознавание: $stamp').openPopup();""" else ""
        return """
            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
            <style>html,body,#map{height:100%;width:100%;margin:0;background:#e9eef3}.leaflet-control-attribution{font-size:9px}</style></head><body><div id="map"></div>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
            const map=L.map('map',{zoomControl:false}).setView([$safeLat,$safeLon],13);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
            $marker
            window.centerOn=(lat,lon)=>{map.setView([lat,lon],17);L.circleMarker([lat,lon],{radius:8,color:'#fff',weight:3,fillColor:'#2878ff',fillOpacity:1}).addTo(map).bindPopup('Вы здесь').openPopup();};
            </script></body></html>
        """.trimIndent()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
