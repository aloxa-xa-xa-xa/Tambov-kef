package ru.tambovkaef.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
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
    private lateinit var economy: EditText
    private lateinit var comfort: EditText
    private lateinit var emptyHistory: TextView

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
            text = "Наблюдай за коэффициентами и сохраняй изменения"
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
            text = "Ручной режим"
            textSize = 19f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(5), 0, dp(3))
        })
        statusCard.addView(TextView(this).apply {
            text = "Пока вводишь значения сам. Автоматическое чтение Яндекс Про и общая карта ещё в разработке."
            textSize = 12f
            setTextColor(muted)
        })
        page.addView(statusCard, margin(top = 22))

        page.addView(sectionTitle("Коэффициенты"))
        val inputCard = card()
        inputCard.orientation = LinearLayout.VERTICAL
        inputCard.setPadding(dp(16), dp(15), dp(16), dp(16))
        inputCard.addView(TextView(this).apply {
            text = "Введи значения, которые видишь в Яндекс Про"
            textSize = 13f
            setTextColor(muted)
        })
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        economy = numberField("Эконом", "1,5")
        comfort = numberField("Комфорт", "1,8")
        fields.addView(fieldColumn("ЭКОНОМ", economy), LinearLayout.LayoutParams(0, -2, 1f))
        val gap = View(this)
        fields.addView(gap, LinearLayout.LayoutParams(dp(12), 1))
        fields.addView(fieldColumn("КОМФОРТ", comfort), LinearLayout.LayoutParams(0, -2, 1f))
        inputCard.addView(fields, margin(top = 14))
        val save = Button(this).apply {
            text = "Сохранить наблюдение"
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(8, 26, 22))
            background = rounded(green, 14)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { saveObservation() }
        }
        inputCard.addView(save, margin(top = 16))
        page.addView(inputCard, margin(top = 10))

        page.addView(sectionTitle("История наблюдений"), margin(top = 24))
        emptyHistory = TextView(this).apply {
            text = "Здесь появятся сохранённые значения. Добавь первое наблюдение выше."
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(2), dp(8), dp(2), dp(8))
        }
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(emptyHistory)
        page.addView(historyBox)

        page.addView(TextView(this).apply {
            text = "Данные вводятся вручную и хранятся на этом устройстве. Это не официальный поток данных Яндекса."
            textSize = 11f
            setTextColor(muted)
            setPadding(0, dp(24), 0, dp(4))
        })
        setContentView(root)
        renderHistory()
    }

    private fun saveObservation() {
        val e = economy.text.toString().trim().replace(',', '.')
        val c = comfort.text.toString().trim().replace(',', '.')
        if (e.isEmpty() && c.isEmpty()) {
            Toast.makeText(this, "Введи хотя бы один коэффициент", Toast.LENGTH_SHORT).show()
            return
        }
        val item = JSONObject().put("economy", e).put("comfort", c)
            .put("time", System.currentTimeMillis())
        val array = readHistory()
        array.put(0, item)
        while (array.length() > 50) array.remove(array.length() - 1)
        prefs.edit().putString("history", array.toString()).apply()
        economy.text.clear()
        comfort.text.clear()
        renderHistory()
        Toast.makeText(this, "Наблюдение сохранено", Toast.LENGTH_SHORT).show()
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

    private fun numberField(hintText: String, example: String) = EditText(this).apply {
        hint = example
        textSize = 19f
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setTextColor(white)
        setHintTextColor(Color.rgb(104, 117, 137))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = rounded(Color.rgb(34, 45, 62), 12, Color.rgb(48, 63, 84))
    }

    private fun fieldColumn(label: String, field: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 10f
            letterSpacing = .1f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(muted)
            setPadding(0, 0, 0, dp(6))
        })
        addView(field, LinearLayout.LayoutParams(-1, dp(54)))
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
