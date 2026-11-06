package ru.tambovkaef.app

import kotlin.math.exp
import kotlin.math.floor

/**
 * Local demand-grid model. Values are observations read from the driver's
 * own screen; this does not invent or claim live Yandex data.
 */
data class RadarObservation(
    val latitude: Double,
    val longitude: Double,
    val tariff: Tariff,
    val multiplier: Double,
    val observedAtMillis: Long
)

enum class Tariff { ECONOMY, COMFORT }

data class RadarCell(
    val latitude: Double,
    val longitude: Double,
    val tariff: Tariff,
    val multiplier: Double,
    val confidence: Double,
    val samples: Int,
    val updatedAtMillis: Long
)

object RadarGrid {
    private const val CELL_SIZE_DEGREES = 0.005
    private const val HALF_LIFE_MILLIS = 5 * 60 * 1000.0

    /**
     * Aggregates recent observations into ~500 m cells. Newer observations
     * carry more weight; old observations decay instead of looking current.
     */
    fun aggregate(
        observations: List<RadarObservation>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<RadarCell> {
        return observations
            .asSequence()
            .filter {
                it.latitude in -90.0..90.0 &&
                    it.longitude in -180.0..180.0 &&
                    it.multiplier.isFinite() &&
                    it.multiplier in 1.0..20.0 &&
                    it.observedAtMillis <= nowMillis &&
                    nowMillis - it.observedAtMillis <= 30 * 60 * 1000L
            }
            .groupBy {
                Triple(
                    floor(it.latitude / CELL_SIZE_DEGREES).toInt(),
                    floor(it.longitude / CELL_SIZE_DEGREES).toInt(),
                    it.tariff
                )
            }
            .map { (key, samples) ->
                var weightedSum = 0.0
                var totalWeight = 0.0
                var newest = 0L
                samples.forEach { sample ->
                    val age = (nowMillis - sample.observedAtMillis).coerceAtLeast(0L)
                    val weight = exp(-ln2() * age / HALF_LIFE_MILLIS)
                    weightedSum += sample.multiplier * weight
                    totalWeight += weight
                    newest = maxOf(newest, sample.observedAtMillis)
                }
                val freshness = if (totalWeight == 0.0) 0.0 else
                    exp(-ln2() * (nowMillis - newest) / HALF_LIFE_MILLIS)
                RadarCell(
                    latitude = (key.first + 0.5) * CELL_SIZE_DEGREES,
                    longitude = (key.second + 0.5) * CELL_SIZE_DEGREES,
                    tariff = key.third,
                    multiplier = if (totalWeight == 0.0) 0.0 else weightedSum / totalWeight,
                    confidence = (freshness * (1.0 - exp(-samples.size / 3.0))).coerceIn(0.0, 1.0),
                    samples = samples.size,
                    updatedAtMillis = newest
                )
            }
            .sortedByDescending { it.multiplier * it.confidence }
    }

    private fun ln2() = 0.6931471805599453
}
