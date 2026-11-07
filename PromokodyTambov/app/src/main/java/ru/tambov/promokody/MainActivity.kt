package ru.tambov.promokody

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermission()
        Updates.schedule(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 24)
            gravity = Gravity.TOP
        }
        layout.addView(TextView(this).apply {
            text = "Промокоды Тамбов"
            textSize = 26f
        })
        layout.addView(TextView(this).apply {
            text = "Автоматический поиск по открытым источникам"
            textSize = 15f
            setPadding(0, 12, 0, 28)
        })
        status = TextView(this).apply {
            textSize = 16f
            text = "Автообновление включено: примерно раз в 12 часов."
        }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "Искать сейчас"
            setOnClickListener {
                Updates.runNow(this@MainActivity)
                status.text = "Поиск запущен. Уведомление придёт при обнаружении новых совпадений."
            }
        })
        layout.addView(TextView(this).apply {
            text = "\nКатегории поиска:\n• Водители Яндекс Про\n• Курьеры Яндекс Про\n• Пассажиры Яндекс Go\n\nНайденные коды требуют проверки в приложении сервиса. Поиск выполняется только по публичным сайтам; Tor не используется."
            textSize = 16f
        })
        setContentView(layout)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                Updates.CHANNEL_ID, "Новые промокоды", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Уведомления о новых найденных промокодах" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        }
    }
}
