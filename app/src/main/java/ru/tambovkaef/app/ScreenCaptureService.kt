package ru.tambovkaef.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    @Volatile private var processing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Ожидание первого распознавания экрана"))
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            startCapture()
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ source ->
            if (processing) {
                source.acquireLatestImage()?.close()
            } else {
                val image = source.acquireLatestImage()
                if (image != null) {
                    processing = true
                    recognize(image)
                }
            }
        }, null)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, null)
        display = projection?.createVirtualDisplay(
            "TambovKaefCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, null
        )
    }

    private fun recognize(image: Image) {
        try {
            val input = InputImage.fromMediaImage(image, 0)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    val text = result.text
                    if (text.isNotBlank()) {
                        getSharedPreferences("tambov_kaef", MODE_PRIVATE).edit()
                            .putString("last_ocr_text", text)
                            .putLong("last_ocr_time", System.currentTimeMillis())
                            .apply()
                        val economy = findTariffMultiplier(text, "эконом")
                        val comfort = findTariffMultiplier(text, "комфорт")
                        if (economy != null || comfort != null) {
                            val prefs = getSharedPreferences("tambov_kaef", MODE_PRIVATE)
                            val now = System.currentTimeMillis()
                            val previousTime = prefs.getLong("last_saved_ocr_time", 0L)
                            val previousEconomy = prefs.getString("last_saved_economy", "") ?: ""
                            val previousComfort = prefs.getString("last_saved_comfort", "") ?: ""
                            if (now - previousTime > 60_000L || economy.orEmpty() != previousEconomy || comfort.orEmpty() != previousComfort) {
                                val history = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
                                val item = JSONObject()
                                    .put("time", now)
                                    .put("economy", economy ?: "")
                                    .put("comfort", comfort ?: "")
                                history.put(item)
                                while (history.length() > 50) history.remove(0)
                                prefs.edit()
                                    .putString("history", history.toString())
                                    .putLong("last_saved_ocr_time", now)
                                    .putString("last_saved_economy", economy ?: previousEconomy)
                                    .putString("last_saved_comfort", comfort ?: previousComfort)
                                    .apply()
                            }
                            val summary = listOfNotNull(
                                economy?.let { "Эконом x$it" },
                                comfort?.let { "Комфорт x$it" }
                            ).joinToString(" • ")
                            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            manager.notify(NOTIFICATION_ID, notification(summary))
                        }
                    }
                }
                .addOnFailureListener { }
                .addOnCompleteListener {
                    image.close()
                    processing = false
                }
        } catch (_: Exception) {
            image.close()
            processing = false
        }
    }

    private fun findTariffMultiplier(fullText: String, tariff: String): String? {
        val lines = fullText.lines()
        val numberPattern = Pattern.compile("(?i)(?:x|х)\\s*([0-9]+[.,]?[0-9]*)|([0-9]+[.,]?[0-9]*)\\s*(?:x|х)")
        for (index in lines.indices) {
            val normalized = lines[index].lowercase(Locale.ROOT)
            val matchesTariff = if (tariff == "эконом") {
                normalized.contains("эконом") || normalized.contains("econom")
            } else {
                normalized.contains("комфорт") || normalized.contains("comfort")
            }
            if (!matchesTariff) continue
            val from = maxOf(0, index - 1)
            val to = minOf(lines.lastIndex, index + 2)
            for (lineIndex in from..to) {
                val matcher = numberPattern.matcher(lines[lineIndex])
                if (matcher.find()) return (matcher.group(1) ?: matcher.group(2))?.replace(',', '.')
            }
        }
        return null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Мониторинг Тамбов Кэф", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun notification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("Тамбов Кэф • мониторинг")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        recognizer.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        private const val CHANNEL_ID = "tambov_screen_monitor"
        private const val NOTIFICATION_ID = 5102
    }
}
