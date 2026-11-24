package com.example.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

object NumberFormatters {

    private val locale = Locale.US

    fun formatDecimal(value: Double?, decimals: Int = 2, fallback: String = "—"): String {
        if (value == null || !value.isFinite()) return fallback
        val clean = if (abs(value) < 1e-12) 0.0 else value
        return String.format(locale, "%.${decimals}f", clean)
    }

    fun formatScientific(value: Double?, decimals: Int = 2, fallback: String = "—"): String {
        if (value == null || !value.isFinite()) return fallback
        if (abs(value) < 1e-18) return String.format(locale, "%.${decimals}f", 0.0)
        val exp = floor(log10(abs(value))).toInt()
        val mantissa = value / 10.0.pow(exp)
        return String.format(locale, "%.${decimals}f×10%s", mantissa, toSuperscript(exp))
    }

    fun formatScientificPlain(value: Double?, decimals: Int = 2, fallback: String = "—"): String {
        if (value == null || !value.isFinite()) return fallback
        if (abs(value) < 1e-18) return "0.00E+00"
        return String.format(locale, "%.${decimals}E", value)
    }

    fun formatSmartFlux(value: Double?, fallback: String = "—"): String {
        if (value == null || !value.isFinite()) return fallback
        if (abs(value) < 1e-18) return "0.00"
        return if (abs(value) < 0.01 || abs(value) >= 10000.0) {
            formatScientific(value, 2, fallback)
        } else {
            formatDecimal(value, 4, fallback)
        }
    }

    fun parseNumberInput(text: String): Double? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val parsed = normalized.toDoubleOrNull() ?: return null
        return if (parsed.isFinite()) parsed else null
    }

    private fun toSuperscript(number: Int): String {
        val map = mapOf(
            '-' to '⁻',
            '0' to '⁰',
            '1' to '¹',
            '2' to '²',
            '3' to '³',
            '4' to '⁴',
            '5' to '⁵',
            '6' to '⁶',
            '7' to '⁷',
            '8' to '⁸',
            '9' to '⁹'
        )
        return number.toString().map { map[it] ?: it }.joinToString("")
    }
}
