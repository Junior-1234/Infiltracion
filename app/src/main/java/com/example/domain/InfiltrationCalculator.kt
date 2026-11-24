package com.example.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object InfiltrationCalculator {

    fun calculateAreaM2(diameterM: Double): Double {
        if (!diameterM.isFinite() || diameterM <= 0.0) return 0.0
        val radius = diameterM / 2.0
        return PI * radius * radius
    }

    /**
     * Standard Kell / Tanaka water density ρ(T) in kg/m³ for T in [0, 50] °C.
     */
    fun waterDensityAtTemp(tempC: Double): Double {
        val t = tempC.coerceIn(0.0, 50.0)
        val a1 = -3.983035
        val a2 = 301.797
        val a3 = 522528.9
        val a4 = 69.34881
        val a5 = 999.974950
        val num = (t + a1) * (t + a1) * (t + a2)
        val den = a3 * (t + a4)
        return a5 * (1.0 - num / den)
    }

    /**
     * Dynamic viscosity μ(T) in Pa·s (Vogel-type standard equation).
     */
    fun dynamicViscosityPaS(tempC: Double): Double {
        val t = tempC.coerceIn(0.0, 60.0)
        return 2.414e-5 * 10.0.pow(247.8 / (t + 133.15))
    }

    /**
     * Ratio μ(T) / μ(20 °C) to normalize K_sat(T) to K_sat(20 °C).
     */
    fun viscosityCorrectionTo20C(tempC: Double): Double {
        val muT = dynamicViscosityPaS(tempC)
        val mu20 = dynamicViscosityPaS(20.0)
        return if (mu20 > 0.0 && muT.isFinite()) muT / mu20 else 1.0
    }

    fun computeAnalysis(
        diameterM: Double,
        waterDensityKgM3: Double,
        rawRows: List<RawIntervalInput>,
        outerDiameterM: Double = 0.60,
        waterTempC: Double = 20.0
    ): InfiltrationAnalysis {
        val safeDiameter = if (diameterM.isFinite() && diameterM > 0.0) diameterM else 0.0
        val safeOuter = if (outerDiameterM.isFinite() && outerDiameterM > safeDiameter) outerDiameterM else safeDiameter * 2.0
        val safeDensity = if (waterDensityKgM3.isFinite() && waterDensityKgM3 > 0.0) waterDensityKgM3 else 1000.0
        val safeTemp = if (waterTempC.isFinite()) waterTempC.coerceIn(0.0, 60.0) else 20.0
        val viscFactor20 = viscosityCorrectionTo20C(safeTemp)

        val areaM2 = calculateAreaM2(safeDiameter)
        val areaCm2 = areaM2 * 10_000.0

        val sortedRows = rawRows.sortedBy { it.orderIndex }
        val warnings = mutableListOf<String>()

        if (safeDiameter <= 0.0) {
            warnings.add("El diámetro del cilindro D debe ser mayor a 0 m para calcular área A, caudal Q y masa Δm.")
        }
        if (waterDensityKgM3 <= 0.0) {
            warnings.add("La densidad del agua ρ debe ser mayor a 0 kg/m³ (se usa 1000 kg/m³ por defecto).")
        }

        if (sortedRows.isEmpty()) {
            return InfiltrationAnalysis(
                diameterM = safeDiameter,
                outerDiameterM = safeOuter,
                waterDensityKgM3 = safeDensity,
                waterTempC = safeTemp,
                viscosityCorrection20C = viscFactor20,
                areaM2 = areaM2,
                areaCm2 = areaCm2,
                rows = emptyList(),
                totals = TableTotals(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, null, null, null, 0),
                kostiakov = KostiakovModel(null, null, null, 0, false, "Agrega al menos 2 intervalos activos (Δt > 0) para ajustar Kostiakov."),
                modifiedKostiakov = ModifiedKostiakovModel(null, null, null, null, 0, false, "Requiere i_b y ≥ 2 intervalos con i(t) > i_b."),
                philip = PhilipModel(null, null, null, null, 0, false, "Requiere ≥ 2 intervalos activos para ajustar el modelo de Philip."),
                modelComparison = ModelComparison(null, null, emptyList()),
                basicInfiltrationMmH = null,
                steadyIntervalCount = 0,
                isStabilized = false,
                stabilizationMessage = "Sin intervalos activos para evaluar estabilización.",
                kSatMS = null,
                kSatCmH = null,
                kSat20MS = null,
                kSat20CmH = null,
                initialRateMmH = null,
                reductionPercent = null,
                diagnosis = null,
                warnings = warnings
            )
        }

        val provisionalRows = mutableListOf<CalculatedRow>()
        var cumulativeTime = 0.0
        var cumulativeI = 0.0
        var prevWaterLevel = sortedRows.first().waterLevelCm

        sortedRows.forEachIndexed { index, input ->
            if (index == 0) {
                prevWaterLevel = input.waterLevelCm
                provisionalRows.add(
                    CalculatedRow(
                        id = input.id,
                        orderIndex = index,
                        isInitialRow = true,
                        deltaTMin = 0.0,
                        refillCm = 0.0,
                        waterLevelCm = input.waterLevelCm,
                        deltaTSec = 0.0,
                        cumulativeTimeMin = 0.0,
                        deltaIMm = null,
                        cumulativeIMm = 0.0,
                        instantRateMmH = null,
                        fluxDensityMS = null,
                        flowRateCm3S = null,
                        flowRateM3S = null,
                        flowRateLS = null,
                        massKg = null,
                        stage = PhysicalStage.INITIAL_BASELINE,
                        validationWarning = null
                    )
                )
            } else {
                val safeDeltaT = if (input.deltaTMin.isFinite() && input.deltaTMin > 0.0) input.deltaTMin else 0.0
                val safeRefill = if (input.refillCm.isFinite() && input.refillCm >= 0.0) input.refillCm else 0.0
                val safeLevel = if (input.waterLevelCm.isFinite()) input.waterLevelCm else prevWaterLevel

                val deltaTSec = safeDeltaT * 60.0
                cumulativeTime += safeDeltaT

                val deltaI = ((prevWaterLevel + safeRefill) - safeLevel) * 10.0
                cumulativeI += deltaI

                val massKg = if (areaM2 > 0.0) safeDensity * areaM2 * (deltaI * 1e-3) else null

                var rowWarning: String? = null
                val instantRate: Double?
                val fluxDensity: Double?
                val flowM3S: Double?
                val flowCm3S: Double?
                val flowLS: Double?

                if (safeDeltaT <= 0.0) {
                    instantRate = null
                    fluxDensity = null
                    flowM3S = null
                    flowCm3S = null
                    flowLS = null
                    rowWarning = "Fila #$index: Δt = 0 min. Ingresa un intervalo mayor a 0 para evitar división entre cero."
                } else {
                    val rate = (deltaI / safeDeltaT) * 60.0
                    val q = (deltaI * 0.001) / deltaTSec
                    val bigQ = if (areaM2 > 0.0) areaM2 * q else null

                    instantRate = if (rate.isFinite()) rate else null
                    fluxDensity = if (q.isFinite()) q else null
                    flowM3S = bigQ?.takeIf { it.isFinite() }
                    flowCm3S = flowM3S?.let { it * 1_000_000.0 }
                    flowLS = flowM3S?.let { it * 1_000.0 }

                    if (deltaI < 0.0) {
                        rowWarning = "Fila #$index: ΔI negativo (${NumberFormatters.formatDecimal(deltaI)} mm). Verifica si faltó registrar lámina de relleno Δhᵣ."
                    }
                }

                if (rowWarning != null) warnings.add(rowWarning)
                prevWaterLevel = safeLevel

                provisionalRows.add(
                    CalculatedRow(
                        id = input.id,
                        orderIndex = index,
                        isInitialRow = false,
                        deltaTMin = safeDeltaT,
                        refillCm = safeRefill,
                        waterLevelCm = safeLevel,
                        deltaTSec = deltaTSec,
                        cumulativeTimeMin = cumulativeTime,
                        deltaIMm = deltaI,
                        cumulativeIMm = cumulativeI,
                        instantRateMmH = instantRate,
                        fluxDensityMS = fluxDensity,
                        flowRateCm3S = flowCm3S,
                        flowRateM3S = flowM3S,
                        flowRateLS = flowLS,
                        massKg = massKg,
                        stage = if (instantRate == null) PhysicalStage.INVALID_INTERVAL else PhysicalStage.TRANSITION,
                        validationWarning = rowWarning
                    )
                )
            }
        }

        val activeRows = provisionalRows.filter { !it.isInitialRow && it.instantRateMmH != null }
        val (basicInfiltration, steadyIndices, isStabilized) = computeBasicInfiltration(activeRows)

        val stabilizationMessage = when {
            activeRows.size < 3 -> "Registra al menos 3 intervalos activos para verificar estabilización."
            isStabilized -> "Estabilización alcanzada: los últimos ${steadyIndices.size} intervalos varían ≤ 10% alrededor de i_b."
            else -> "Aún en descenso/transición: continúa midiendo hasta que 3 intervalos seguidos varíen ≤ 10%."
        }

        val finalRows = provisionalRows.map { row ->
            if (row.isInitialRow) {
                row
            } else if (row.instantRateMmH == null) {
                row.copy(stage = PhysicalStage.INVALID_INTERVAL)
            } else {
                row.copy(stage = classifyPhysicalStage(row, basicInfiltration, steadyIndices))
            }
        }

        val totalDeltaTSec = finalRows.filter { !it.isInitialRow }.sumOf { it.deltaTSec }
        val totalTimeMin = finalRows.lastOrNull()?.cumulativeTimeMin ?: 0.0
        val totalRefillCm = finalRows.filter { !it.isInitialRow }.sumOf { it.refillCm }
        val totalDeltaIMm = finalRows.mapNotNull { it.deltaIMm }.sum()
        val totalCumulativeIMm = finalRows.lastOrNull()?.cumulativeIMm ?: 0.0
        val totalMassKg = finalRows.mapNotNull { it.massKg }.sum()

        val activeRates = activeRows.mapNotNull { it.instantRateMmH }
        val activeFluxes = activeRows.mapNotNull { it.fluxDensityMS }
        val activeFlowsCm3 = activeRows.mapNotNull { it.flowRateCm3S }
        val activeFlowsM3 = activeRows.mapNotNull { it.flowRateM3S }
        val activeFlowsL = activeRows.mapNotNull { it.flowRateLS }

        val totals = TableTotals(
            totalDeltaTSec = totalDeltaTSec,
            totalTimeMin = totalTimeMin,
            totalRefillCm = totalRefillCm,
            totalDeltaIMm = totalDeltaIMm,
            totalCumulativeIMm = totalCumulativeIMm,
            totalMassKg = totalMassKg,
            meanRateMmH = activeRates.takeIf { it.isNotEmpty() }?.average(),
            meanFluxMS = activeFluxes.takeIf { it.isNotEmpty() }?.average(),
            meanFlowCm3S = activeFlowsCm3.takeIf { it.isNotEmpty() }?.average(),
            meanFlowM3S = activeFlowsM3.takeIf { it.isNotEmpty() }?.average(),
            meanFlowLS = activeFlowsL.takeIf { it.isNotEmpty() }?.average(),
            activeIntervalCount = activeRows.size
        )

        val kostiakov = fitKostiakov(activeRows)
        val modifiedKostiakov = fitModifiedKostiakov(activeRows, basicInfiltration)
        val philip = fitPhilip(activeRows)
        val comparison = compareModels(kostiakov, modifiedKostiakov, philip)

        val kSatMS = basicInfiltration?.takeIf { it >= 0.0 }?.let { (it * 0.001) / 3600.0 }
        val kSatCmH = basicInfiltration?.takeIf { it >= 0.0 }?.let { it / 10.0 }
        val kSat20MS = kSatMS?.let { it * viscFactor20 }
        val kSat20CmH = kSatCmH?.let { it * viscFactor20 }

        val initialRate = activeRows.firstOrNull()?.instantRateMmH
        val reductionPct = if (initialRate != null && initialRate > 0.0 && basicInfiltration != null) {
            ((initialRate - basicInfiltration) / initialRate) * 100.0
        } else null

        val diagnosis = if (basicInfiltration != null && basicInfiltration >= 0.0) {
            buildSoilDiagnosis(basicInfiltration, kostiakov, reductionPct)
        } else null

        return InfiltrationAnalysis(
            diameterM = safeDiameter,
            outerDiameterM = safeOuter,
            waterDensityKgM3 = safeDensity,
            waterTempC = safeTemp,
            viscosityCorrection20C = viscFactor20,
            areaM2 = areaM2,
            areaCm2 = areaCm2,
            rows = finalRows,
            totals = totals,
            kostiakov = kostiakov,
            modifiedKostiakov = modifiedKostiakov,
            philip = philip,
            modelComparison = comparison,
            basicInfiltrationMmH = basicInfiltration,
            steadyIntervalCount = steadyIndices.size,
            isStabilized = isStabilized,
            stabilizationMessage = stabilizationMessage,
            kSatMS = kSatMS,
            kSatCmH = kSatCmH,
            kSat20MS = kSat20MS,
            kSat20CmH = kSat20CmH,
            initialRateMmH = initialRate,
            reductionPercent = reductionPct,
            diagnosis = diagnosis,
            warnings = warnings.distinct()
        )
    }

    private data class SteadyResult(val ib: Double?, val indices: Set<Int>, val stabilized: Boolean)

    private fun computeBasicInfiltration(activeRows: List<CalculatedRow>): SteadyResult {
        val positiveRows = activeRows.filter { (it.instantRateMmH ?: -1.0) >= 0.0 }
        if (positiveRows.isEmpty()) return SteadyResult(null, emptySet(), false)
        if (positiveRows.size == 1) {
            val only = positiveRows.first()
            return SteadyResult(only.instantRateMmH, setOf(only.orderIndex), false)
        }

        val lastRate = positiveRows.last().instantRateMmH ?: 0.0
        val steadyTail = mutableListOf<CalculatedRow>()
        for (row in positiveRows.asReversed()) {
            val rate = row.instantRateMmH ?: break
            val diffRatio = if (max(lastRate, rate) > 0.0) {
                abs(rate - lastRate) / max(lastRate, rate)
            } else 0.0
            if (diffRatio <= 0.10 && steadyTail.size < 4) {
                steadyTail.add(row)
            } else {
                break
            }
        }

        val isStabilized = steadyTail.size >= 3
        val chosenTail = if (steadyTail.size >= 2) {
            steadyTail
        } else {
            positiveRows.takeLast(minOf(2, positiveRows.size))
        }

        val ib = chosenTail.mapNotNull { it.instantRateMmH }.average()
        return SteadyResult(ib, chosenTail.map { it.orderIndex }.toSet(), isStabilized)
    }

    private fun classifyPhysicalStage(
        row: CalculatedRow,
        basicInfiltration: Double?,
        steadyIndices: Set<Int>
    ): PhysicalStage {
        val rate = row.instantRateMmH ?: return PhysicalStage.INVALID_INTERVAL
        if (row.orderIndex in steadyIndices) return PhysicalStage.DARCY_STEADY
        if (basicInfiltration == null || basicInfiltration <= 0.0) {
            return if (row.orderIndex == 1) PhysicalStage.CAPILLARY_SUCTION else PhysicalStage.TRANSITION
        }
        return when {
            rate > basicInfiltration * 1.35 -> PhysicalStage.CAPILLARY_SUCTION
            rate > basicInfiltration * 1.10 -> PhysicalStage.TRANSITION
            else -> PhysicalStage.DARCY_STEADY
        }
    }

    private fun fitKostiakov(activeRows: List<CalculatedRow>): KostiakovModel {
        val validPoints = activeRows.mapNotNull { row ->
            val t = row.cumulativeTimeMin
            val i = row.instantRateMmH
            if (t > 0.0 && i != null && i > 0.0) {
                Triple(t, i, ln(t) to ln(i))
            } else null
        }

        if (validPoints.size < 2) {
            return KostiakovModel(
                k = null, n = null, rSquared = null, pointCount = validPoints.size, isValid = false,
                note = "Se requieren al menos 2 intervalos con t > 0 e i(t) > 0 para la regresión potencial."
            )
        }

        val count = validPoints.size.toDouble()
        val sumX = validPoints.sumOf { it.third.first }
        val sumY = validPoints.sumOf { it.third.second }
        val sumXX = validPoints.sumOf { it.third.first * it.third.first }
        val sumYY = validPoints.sumOf { it.third.second * it.third.second }
        val sumXY = validPoints.sumOf { it.third.first * it.third.second }

        val denomX = count * sumXX - sumX * sumX
        if (abs(denomX) < 1e-12) {
            return KostiakovModel(
                k = null, n = null, rSquared = null, pointCount = validPoints.size, isValid = false,
                note = "Los tiempos acumulados deben variar entre intervalos para ajustar Kostiakov."
            )
        }

        val n = (count * sumXY - sumX * sumY) / denomX
        val lnK = (sumY / count) - n * (sumX / count)
        val k = exp(lnK)

        val denomY = count * sumYY - sumY * sumY
        val rSquared = if (abs(denomY) < 1e-12) 1.0 else {
            val num = count * sumXY - sumX * sumY
            ((num * num) / (denomX * denomY)).coerceIn(0.0, 1.0)
        }

        if (!k.isFinite() || !n.isFinite() || !rSquared.isFinite()) {
            return KostiakovModel(null, null, null, validPoints.size, false, "No fue posible ajustar Kostiakov.")
        }

        val fitNote = when {
            rSquared >= 0.90 -> "Ajuste potencial excelente (R² ≥ 0.90): el suelo sigue fielmente la ley de Kostiakov."
            rSquared >= 0.70 -> "Ajuste potencial aceptable: refleja la caída gradual de succión capilar."
            else -> "Ajuste moderado/bajo: puede haber heterogeneidad en el suelo o grietas."
        }

        return KostiakovModel(k, n, rSquared, validPoints.size, true, fitNote)
    }

    /**
     * Kostiakov-Lewis (modified): i(t) = k_m * t^n_m + i_b
     * Linearized as ln(i(t) - i_b) = ln(k_m) + n_m * ln(t) for points with i(t) > i_b.
     */
    private fun fitModifiedKostiakov(
        activeRows: List<CalculatedRow>,
        basicInfiltration: Double?
    ): ModifiedKostiakovModel {
        val ib = basicInfiltration ?: return ModifiedKostiakovModel(
            null, null, null, null, 0, false,
            "Se requiere infiltración básica i_b válida para ajustar Kostiakov-Lewis."
        )
        val pts = activeRows.mapNotNull { row ->
            val t = row.cumulativeTimeMin
            val i = row.instantRateMmH
            if (t > 0.0 && i != null && (i - ib) > 1e-4) {
                Triple(t, i, ln(t) to ln(i - ib))
            } else null
        }

        if (pts.size < 2) {
            return ModifiedKostiakovModel(
                null, null, ib, null, pts.size, false,
                "Se requieren al menos 2 intervalos transitorios con i(t) > i_b."
            )
        }

        val count = pts.size.toDouble()
        val sumX = pts.sumOf { it.third.first }
        val sumY = pts.sumOf { it.third.second }
        val sumXX = pts.sumOf { it.third.first * it.third.first }
        val sumXY = pts.sumOf { it.third.first * it.third.second }
        val denomX = count * sumXX - sumX * sumX
        if (abs(denomX) < 1e-12) {
            return ModifiedKostiakovModel(null, null, ib, null, pts.size, false, "Tiempos insuficientes para ajuste.")
        }

        val nm = (count * sumXY - sumX * sumY) / denomX
        val km = exp((sumY / count) - nm * (sumX / count))
        if (!km.isFinite() || !nm.isFinite()) {
            return ModifiedKostiakovModel(null, null, ib, null, pts.size, false, "No convergió Kostiakov-Lewis.")
        }

        // Compute R² on full observed rates (i_obs vs k_m * t^n_m + i_b)
        val allObs = activeRows.mapNotNull { r ->
            val rate = r.instantRateMmH
            if (r.cumulativeTimeMin > 0.0 && rate != null) r.cumulativeTimeMin to rate else null
        }
        val meanObs = allObs.map { it.second }.average()
        val ssTot = allObs.sumOf { (it.second - meanObs) * (it.second - meanObs) }
        val ssRes = allObs.sumOf { (t, obs) ->
            val pred = km * t.pow(nm) + ib
            (obs - pred) * (obs - pred)
        }
        val r2 = if (ssTot > 1e-12) (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0) else 1.0

        return ModifiedKostiakovModel(
            k = km,
            n = nm,
            ib = ib,
            rSquared = r2,
            pointCount = allObs.size,
            isValid = true,
            note = "Incluye la asíntota estacionaria i_b = ${NumberFormatters.formatDecimal(ib, 2)} mm/h."
        )
    }

    /**
     * Philip's infiltration equation: I(t) = S * t^(1/2) + A_p * t
     * Linearized via I(t) / sqrt(t) = S + A_p * sqrt(t) for t > 0, I > 0.
     */
    private fun fitPhilip(activeRows: List<CalculatedRow>): PhilipModel {
        val pts = activeRows.mapNotNull { row ->
            val t = row.cumulativeTimeMin
            val cumI = row.cumulativeIMm
            if (t > 0.0 && cumI > 0.0) {
                val sqT = sqrt(t)
                Triple(t, cumI, sqT to (cumI / sqT))
            } else null
        }

        if (pts.size < 2) {
            return PhilipModel(
                null, null, null, null, pts.size, false,
                "Se requieren al menos 2 intervalos con t > 0 e I(t) > 0 para ajustar Philip."
            )
        }

        val count = pts.size.toDouble()
        val sumX = pts.sumOf { it.third.first }
        val sumY = pts.sumOf { it.third.second }
        val sumXX = pts.sumOf { it.third.first * it.third.first }
        val sumXY = pts.sumOf { it.third.first * it.third.second }
        val denomX = count * sumXX - sumX * sumX
        if (abs(denomX) < 1e-12) {
            return PhilipModel(null, null, null, null, pts.size, false, "Datos insuficientes para ajustar Philip.")
        }

        val ap = (count * sumXY - sumX * sumY) / denomX
        val sorptivityS = (sumY / count) - ap * (sumX / count)
        if (!ap.isFinite() || !sorptivityS.isFinite()) {
            return PhilipModel(null, null, null, null, pts.size, false, "Ajuste de Philip no convergente.")
        }

        val meanI = pts.map { it.second }.average()
        val ssTot = pts.sumOf { (it.second - meanI) * (it.second - meanI) }
        val ssRes = pts.sumOf { (t, cumI, _) ->
            val pred = sorptivityS * sqrt(t) + ap * t
            (cumI - pred) * (cumI - pred)
        }
        val r2 = if (ssTot > 1e-12) (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0) else 1.0

        return PhilipModel(
            sorptivityS = sorptivityS,
            transmissivityA = ap,
            transmissivityMmH = ap * 60.0,
            rSquared = r2,
            pointCount = pts.size,
            isValid = true,
            note = "Sortividad capilar S = ${NumberFormatters.formatDecimal(sorptivityS, 2)} mm/min¹/²; componente gravitacional A = ${NumberFormatters.formatDecimal(ap * 60.0, 2)} mm/h."
        )
    }

    private fun compareModels(
        kostiakov: KostiakovModel,
        modified: ModifiedKostiakovModel,
        philip: PhilipModel
    ): ModelComparison {
        val entries = listOf(
            ModelComparisonEntry(
                name = "Kostiakov simple",
                equationSummary = if (kostiakov.isValid) {
                    "i(t) = ${NumberFormatters.formatDecimal(kostiakov.k, 2)}·t^(${NumberFormatters.formatDecimal(kostiakov.n, 3)})"
                } else "No disponible",
                rSquared = kostiakov.rSquared,
                isValid = kostiakov.isValid
            ),
            ModelComparisonEntry(
                name = "Kostiakov-Lewis (mod.)",
                equationSummary = if (modified.isValid) {
                    "i(t) = ${NumberFormatters.formatDecimal(modified.k, 2)}·t^(${NumberFormatters.formatDecimal(modified.n, 3)}) + ${NumberFormatters.formatDecimal(modified.ib, 1)}"
                } else "No disponible",
                rSquared = modified.rSquared,
                isValid = modified.isValid
            ),
            ModelComparisonEntry(
                name = "Philip (lámina acum.)",
                equationSummary = if (philip.isValid) {
                    "I(t) = ${NumberFormatters.formatDecimal(philip.sorptivityS, 2)}·t¹/² + ${NumberFormatters.formatDecimal(philip.transmissivityA, 3)}·t"
                } else "No disponible",
                rSquared = philip.rSquared,
                isValid = philip.isValid
            )
        )
        val best = entries.filter { it.isValid && it.rSquared != null }.maxByOrNull { it.rSquared!! }
        return ModelComparison(
            bestModelName = best?.name,
            bestRSquared = best?.rSquared,
            entries = entries
        )
    }

    fun evaluateKostiakov(k: Double, n: Double, tMin: Double): Double? {
        if (!k.isFinite() || !n.isFinite() || tMin <= 0.0) return null
        val v = k * tMin.pow(n)
        return v.takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun buildSoilDiagnosis(
        ib: Double,
        kostiakov: KostiakovModel,
        reductionPct: Double?
    ): SoilDiagnosis {
        val (permClass, texture, advice, severity) = when {
            ib < 1.0 -> Quadruple(
                "Muy lenta (< 1 mm/h)",
                "Arcilloso pesado o suelo fuertemente compactado",
                "Alto riesgo de encharcamiento y escorrentía superficial. Se recomiendan labores de subsolado, incorporación de materia orgánica y riegos de muy baja lámina.",
                2
            )
            ib < 5.0 -> Quadruple(
                "Lenta (1 – 5 mm/h)",
                "Arcilloso o franco-arcilloso",
                "Drenaje interno restringido. Aplicar riegos fraccionados con emisores de bajo caudal (goteo) para evitar escurrimiento.",
                1
            )
            ib < 20.0 -> Quadruple(
                "Moderadamente lenta (5 – 20 mm/h)",
                "Franco-arcillo-limoso o franco-arcilloso estructurado",
                "Buena retención de humedad con infiltración controlada. Adecuado para aspersión de baja intensidad o riego por goteo.",
                0
            )
            ib < 60.0 -> Quadruple(
                "Moderada (20 – 60 mm/h)",
                "Franco equilibrado (condición agrícola óptima)",
                "Balance ideal entre aireación, retención de agua y velocidad de entrada. Apto para la mayoría de cultivos y métodos de riego.",
                0
            )
            ib < 125.0 -> Quadruple(
                "Moderadamente rápida (60 – 125 mm/h)",
                "Franco-arenoso",
                "Alta capacidad de drenaje y rápida entrada de agua. Vigilar la frecuencia de riego para evitar estrés hídrico entre aplicaciones.",
                0
            )
            ib < 250.0 -> Quadruple(
                "Rápida (125 – 250 mm/h)",
                "Arenoso-franco",
                "Baja retención hídrica y riesgo de percolación profunda de nutrientes. Priorizar riego localizado de alta frecuencia.",
                1
            )
            else -> Quadruple(
                "Muy rápida (> 250 mm/h)",
                "Arenoso grueso o suelo muy macroporoso / agrietado",
                "Percolación excesiva y baja eficiencia en riego por gravedad. Evitar inundación/surcos largos; usar goteo y enmiendas orgánicas.",
                2
            )
        }

        val kostiakovText = buildString {
            if (kostiakov.isValid && kostiakov.n != null) {
                val nVal = kostiakov.n
                when {
                    nVal < -0.55 -> append("Exponente n = ${NumberFormatters.formatDecimal(nVal, 3)} indica fuerte descenso inicial por succión capilar en suelo inicialmente seco.")
                    nVal < -0.20 -> append("Exponente n = ${NumberFormatters.formatDecimal(nVal, 3)} refleja una transición gradual típica hacia el flujo gravitacional.")
                    nVal <= 0.0 -> append("Exponente n = ${NumberFormatters.formatDecimal(nVal, 3)} cercano a cero sugiere suelo previamente húmedo o de textura gruesa.")
                    else -> append("Exponente n positivo (${NumberFormatters.formatDecimal(nVal, 3)}): revisar presencia de flujo preferencial o apertura de poros durante el ensayo.")
                }
            } else {
                append("Completa más intervalos activos para interpretar la pendiente n de Kostiakov.")
            }
            if (reductionPct != null && reductionPct.isFinite()) {
                append(" La tasa se redujo en ${NumberFormatters.formatDecimal(reductionPct, 1)}% desde la lectura inicial hasta i_b.")
            }
        }

        return SoilDiagnosis(
            permeabilityClass = permClass,
            soilTextureHint = texture,
            managementAdvice = advice,
            kostiakovInterpretation = kostiakovText,
            severityLevel = severity
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
