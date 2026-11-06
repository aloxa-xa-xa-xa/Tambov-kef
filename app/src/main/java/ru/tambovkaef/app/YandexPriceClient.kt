package ru.tambovkaef.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Client for Yandex Go public partner trip-info endpoint.
 * Credentials must be issued by Yandex; never hard-code them in the app.
 * A single-point request returns pickup price with surge included.
 */
data class PointTariffPrice(
    val tariffClass: String,
    val title: String,
    val price: Double,
    val basePrice: Double?,
    val multiplier: Double?,
    val currency: String
)

class YandexPriceClient(
    private val clid: String,
    private val apiKey: String,
    private val connectTimeoutMillis: Int = 8_000,
    private val readTimeoutMillis: Int = 8_000
) {
    fun fetchPointPrices(longitude: Double, latitude: Double): List<PointTariffPrice> {
        require(longitude in -180.0..180.0 && latitude in -90.0..90.0)
        require(clid.isNotBlank() && apiKey.isNotBlank())

        val query = listOf(
            "clid=" + enc(clid),
            "rll=" + enc(String.format(Locale.US, "%.6f,%.6f", longitude, latitude)),
            "class=" + enc("econom,business")
        ).joinToString("&")
        val connection = URL("https://taxi-routeinfo.taxi.yandex.net/taxi_info?$query")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("YaTaxi-Api-Key", apiKey)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (status !in 200..299) {
                throw IllegalStateException("Yandex API HTTP $status: ${body.take(240)}")
            }

            val root = JSONObject(body)
            val currency = root.optString("currency", "RUB")
            val options = root.optJSONArray("options") ?: return emptyList()
            return (0 until options.length()).mapNotNull { index ->
                val item = options.optJSONObject(index) ?: return@mapNotNull null
                val price = item.optDouble("price", Double.NaN)
                if (!price.isFinite() || price <= 0.0) return@mapNotNull null
                val base = item.optDouble("min_price", Double.NaN)
                    .takeIf { it.isFinite() && it > 0.0 }
                val multiplier = base?.let { (price / it).coerceIn(0.1, 20.0) }
                PointTariffPrice(
                    tariffClass = item.optString("class_name"),
                    title = item.optString("class_text", item.optString("class_name")),
                    price = price,
                    basePrice = base,
                    multiplier = multiplier,
                    currency = currency
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
