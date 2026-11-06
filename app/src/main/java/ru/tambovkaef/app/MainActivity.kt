package ru.tambovkaef.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Typeface

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (18 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.TOP
        }
        fun label(text: String, size: Float = 18f) = TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, pad / 2, 0, pad / 2)
        }
        val economy = EditText(this).apply {
            hint = "Например, 1.5"
            inputType = 8194
        }
        val comfort = EditText(this).apply {
            hint = "Например, 1.8"
            inputType = 8194
        }
        val status = label("Пока нет наблюдений. Вводимые значения — данные водителя, не официальный поток Яндекса.", 14f)
        layout.addView(label("ТАМБОВ КЭФ", 26f).apply { setTypeface(null, Typeface.BOLD) })
        layout.addView(label("Тарифы", 20f))
        layout.addView(label("Эконом"))
        layout.addView(economy)
        layout.addView(label("Комфорт"))
        layout.addView(comfort)
        layout.addView(Button(this).apply {
            text = "Сохранить наблюдение"
            setOnClickListener {
                val e = economy.text.toString().trim()
                val c = comfort.text.toString().trim()
                status.text = "Последнее локальное наблюдение:\nЭконом: ${e.ifEmpty { "—" }}\nКомфорт: ${c.ifEmpty { "—" }}\n\nЭто ручной ввод. Серверный обмен, карта и OCR ещё не реализованы."
            }
        })
        layout.addView(label("Карта Тамбова и обмен наблюдениями появятся в следующих версиях.", 14f))
        layout.addView(status)
        setContentView(layout)
    }
}
