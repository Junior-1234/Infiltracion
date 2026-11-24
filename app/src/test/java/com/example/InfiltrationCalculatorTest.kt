package com.example

import com.example.domain.InfiltrationCalculator
import com.example.domain.PhysicalStage
import com.example.domain.RawIntervalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class InfiltrationCalculatorTest {

    @Test
    fun `test initial row leaves rates null and avoids division by zero`() {
        val rows = listOf(
            RawIntervalInput(id = 1, orderIndex = 0, deltaTMin = 0.0, refillCm = 0.0, waterLevelCm = 15.0),
            RawIntervalInput(id = 2, orderIndex = 1, deltaTMin = 5.0, refillCm = 2.0, waterLevelCm = 14.5)
        )
        val analysis = InfiltrationCalculator.computeAnalysis(
            diameterM = 0.30,
            waterDensityKgM3 = 1000.0,
            rawRows = rows
        )

        val expectedArea = PI * 0.15 * 0.15
        assertEquals(expectedArea, analysis.areaM2, 1e-8)

        val row0 = analysis.rows[0]
        assertTrue(row0.isInitialRow)
        assertNull(row0.instantRateMmH)
        assertNull(row0.fluxDensityMS)
        assertNull(row0.flowRateCm3S)
        assertEquals(PhysicalStage.INITIAL_BASELINE, row0.stage)

        val row1 = analysis.rows[1]
        assertEquals(25.0, row1.deltaIMm!!, 1e-6)
        assertEquals(25.0, row1.cumulativeIMm, 1e-6)
        assertEquals(300.0, row1.deltaTSec, 1e-6)
        assertEquals(300.0, row1.instantRateMmH!!, 1e-6)
        assertEquals(25.0 * 0.001 / 300.0, row1.fluxDensityMS!!, 1e-9)
        assertEquals(expectedArea * row1.fluxDensityMS * 1_000_000.0, row1.flowRateCm3S!!, 1e-6)
        assertEquals(1000.0 * expectedArea * 0.025, row1.massKg!!, 1e-6)
    }

    @Test
    fun `test intermediate zero deltaT produces no NaN or Infinity`() {
        val rows = listOf(
            RawIntervalInput(id = 1, orderIndex = 0, deltaTMin = 0.0, refillCm = 0.0, waterLevelCm = 15.0),
            RawIntervalInput(id = 2, orderIndex = 1, deltaTMin = 0.0, refillCm = 0.0, waterLevelCm = 14.0),
            RawIntervalInput(id = 3, orderIndex = 2, deltaTMin = 5.0, refillCm = 0.0, waterLevelCm = 13.0)
        )
        val analysis = InfiltrationCalculator.computeAnalysis(0.30, 1000.0, rows)
        val row1 = analysis.rows[1]
        assertNull(row1.instantRateMmH)
        assertNull(row1.fluxDensityMS)
        assertNull(row1.flowRateCm3S)
        assertEquals(PhysicalStage.INVALID_INTERVAL, row1.stage)
        assertTrue(analysis.warnings.isNotEmpty())
    }

    @Test
    fun `test kostiakov philip modified kostiakov and temperature correction`() {
        val rows = listOf(
            RawIntervalInput(1, 0, 0.0, 0.0, 15.0),
            RawIntervalInput(2, 1, 2.0, 0.0, 14.3),
            RawIntervalInput(3, 2, 3.0, 0.0, 13.6),
            RawIntervalInput(4, 3, 5.0, 0.0, 12.8),
            RawIntervalInput(5, 4, 10.0, 3.5, 14.7),
            RawIntervalInput(6, 5, 15.0, 0.0, 13.8),
            RawIntervalInput(7, 6, 15.0, 0.0, 12.9),
            RawIntervalInput(8, 7, 15.0, 0.0, 12.0)
        )
        val analysis = InfiltrationCalculator.computeAnalysis(
            diameterM = 0.30,
            waterDensityKgM3 = 997.0,
            rawRows = rows,
            outerDiameterM = 0.60,
            waterTempC = 25.0
        )
        assertTrue(analysis.kostiakov.isValid)
        assertTrue(analysis.philip.isValid)
        assertTrue(analysis.modifiedKostiakov.isValid)
        assertNotNull(analysis.modelComparison.bestModelName)
        assertTrue(analysis.isStabilized)
        assertNotNull(analysis.kSatMS)
        assertNotNull(analysis.kSat20MS)
        // At 25°C water is less viscous than at 20°C, so K_sat(20°C) < K_sat(25°C)
        assertTrue(analysis.kSat20MS!! < analysis.kSatMS!!)
    }
}
