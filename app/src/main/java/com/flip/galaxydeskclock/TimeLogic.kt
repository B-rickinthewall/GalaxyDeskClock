package com.flip.galaxydeskclock

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.floor


enum class DisplayPhase {
    NORMAL,
    REST,
    FADE_IN
}

data class DisplayMoment(
    val phase: DisplayPhase,
    val activeHourSeed: Long,
    val nextHourSeed: Long,
    val nextLayoutAlpha: Float,
    val secondsIntoHour: Int
)

object TimeLogic {
    fun resolve(
        epochMillis: Long,
        zoneId: ZoneId,
        restEnabled: Boolean,
        restStartMinute: Int,
        fadeSeconds: Int
    ): DisplayMoment {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId)
        val startOfHour = now.withMinute(0).withSecond(0).withNano(0)
        val nextHour = startOfHour.plusHours(1)
        val secondsIntoHour = now.minute * 60 + now.second
        val fadeStart = 3600 - fadeSeconds.coerceIn(1, 20)
        val restStart = restStartMinute.coerceIn(45, 59) * 60

        val phase = when {
            !restEnabled -> DisplayPhase.NORMAL
            secondsIntoHour >= fadeStart -> DisplayPhase.FADE_IN
            secondsIntoHour >= restStart -> DisplayPhase.REST
            else -> DisplayPhase.NORMAL
        }

        val alpha = when (phase) {
            DisplayPhase.FADE_IN -> {
                val elapsed = secondsIntoHour - fadeStart + now.nano / 1_000_000_000f
                (elapsed / (3600 - fadeStart).toFloat()).coerceIn(0f, 1f)
            }
            DisplayPhase.NORMAL -> 1f
            DisplayPhase.REST -> 0f
        }

        return DisplayMoment(
            phase = phase,
            activeHourSeed = startOfHour.toEpochSecond(),
            nextHourSeed = nextHour.toEpochSecond(),
            nextLayoutAlpha = alpha,
            secondsIntoHour = secondsIntoHour
        )
    }

    fun enabledLayoutIndices(mask: Int, count: Int): List<Int> {
        val enabled = (0 until count).filter { (mask and (1 shl it)) != 0 }
        return if (enabled.isEmpty()) (0 until count).toList() else enabled
    }

    fun layoutIndex(hourSeed: Long, enabledIndices: List<Int>): Int {
        if (enabledIndices.isEmpty()) return 0
        val localIndex = rotatingIndex(
            seed = hourSeed,
            count = enabledIndices.size,
            salt = 0x517CC1B727220A95L
        )
        return enabledIndices[localIndex]
    }

    fun backgroundIndex(hourSeed: Long, count: Int): Int {
        if (count <= 0) return -1
        return rotatingIndex(
            seed = hourSeed,
            count = count,
            salt = -0x61C8864680B583EBL
        )
    }

    fun microShiftIndex(epochMillis: Long, intervalMinutes: Int, count: Int): Int {
        if (count <= 0) return 0
        val bucketMillis = intervalMinutes.coerceAtLeast(1) * 60_000L
        return floorMod(mix64(floor(epochMillis / bucketMillis.toDouble()).toLong()), count)
    }

    fun restPositionIndex(epochMillis: Long, intervalSeconds: Int, count: Int): Int {
        if (count <= 0) return 0
        val bucketMillis = intervalSeconds.coerceAtLeast(1) * 1_000L
        return floorMod(mix64(floor(epochMillis / bucketMillis.toDouble()).toLong()), count)
    }

    private fun rotatingIndex(seed: Long, count: Int, salt: Long): Int {
        if (count <= 1) return 0
        val hourOrdinal = Math.floorDiv(seed, 3600L)
        val offset = floorMod(mix64(salt), count)
        val validSteps = (1 until count).filter { greatestCommonDivisor(it, count) == 1 }
        val step = validSteps[floorMod(mix64(salt xor 0x2D358DCCAA6C78A5L), validSteps.size)]
        return Math.floorMod(offset.toLong() + hourOrdinal * step.toLong(), count.toLong()).toInt()
    }

    private fun greatestCommonDivisor(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val remainder = x % y
            x = y
            y = remainder
        }
        return kotlin.math.abs(x)
    }

    private fun floorMod(value: Long, divisor: Int): Int {
        val mod = value % divisor
        return if (mod < 0) (mod + divisor).toInt() else mod.toInt()
    }

    private fun mix64(input: Long): Long {
        var z = input
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }
}
