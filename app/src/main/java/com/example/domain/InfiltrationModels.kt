package com.example.domain

enum class PhysicalStage(
    val label: String,
    val shortLabel: String,
    val description: String
) {
    INITIAL_BASELINE(
        label = "Lectura inicial (t = 0)",
        shortLabel = "Inicio",
        description = "Nivel de referencia inicial antes de iniciar el conteo de infiltración."
    ),
    CAPILLARY_SUCTION(
        label = "Succión capilar inicial",
        shortLabel = "Capilar",
        description = "El gradiente mátrico del suelo seco domina; la velocidad de infiltración es elevada y desciende rápidamente."
    ),
    TRANSITION(
        label = "Transición",
        shortLabel = "Transición",
        description = "Disminuye la succión capilar al humedecerse el perfil y empieza a predominar la fuerza gravitacional."
    ),
    DARCY_STEADY(
        label = "Régimen estable (Darcy)",
        shortLabel = "Darcy",
        description = "La velocidad se estabiliza cerca de la infiltración básica i_b bajo flujo gravitacional saturado (dH/dz ≈ 1)."
    ),
    INVALID_INTERVAL(
        label = "Intervalo pendiente / Δt = 0",
        shortLabel = "Revisar",
        description = "Ingresa un intervalo Δt > 0 min para calcular tasas de flujo sin división entre cero."
    )
}

data class RawIntervalInput(
    val id: Long = 0L,
    val orderIndex: Int,
    val deltaTMin: Double,
    val refillCm: Double,
    val waterLevelCm: Double
)

data class CalculatedRow(
    val id: Long,
    val orderIndex: Int,
    val isInitialRow: Boolean,
    val deltaTMin: Double,
    val refillCm: Double,
    val waterLevelCm: Double,
    val deltaTSec: Double,
    val cumulativeTimeMin: Double,
    val deltaIMm: Double?,
    val cumulativeIMm: Double,
    val instantRateMmH: Double?,
    val fluxDensityMS: Double?,
    val flowRateCm3S: Double?,
    val flowRateM3S: Double?,
    val flowRateLS: Double?,
    val massKg: Double?,
    val stage: PhysicalStage,
    val validationWarning: String? = null
)

data class TableTotals(
    val totalDeltaTSec: Double,
    val totalTimeMin: Double,
    val totalRefillCm: Double,
    val totalDeltaIMm: Double,
    val totalCumulativeIMm: Double,
    val totalMassKg: Double,
    val meanRateMmH: Double?,
    val meanFluxMS: Double?,
    val meanFlowCm3S: Double?,
    val meanFlowM3S: Double?,
    val meanFlowLS: Double?,
    val activeIntervalCount: Int
)

data class KostiakovModel(
    val k: Double?,
    val n: Double?,
    val rSquared: Double?,
    val pointCount: Int,
    val isValid: Boolean,
    val note: String
)

data class ModifiedKostiakovModel(
    val k: Double?,
    val n: Double?,
    val ib: Double?,
    val rSquared: Double?,
    val pointCount: Int,
    val isValid: Boolean,
    val note: String
)

data class PhilipModel(
    val sorptivityS: Double?,
    val transmissivityA: Double?,
    val transmissivityMmH: Double?,
    val rSquared: Double?,
    val pointCount: Int,
    val isValid: Boolean,
    val note: String
)

data class ModelComparisonEntry(
    val name: String,
    val equationSummary: String,
    val rSquared: Double?,
    val isValid: Boolean
)

data class ModelComparison(
    val bestModelName: String?,
    val bestRSquared: Double?,
    val entries: List<ModelComparisonEntry>
)

data class SoilDiagnosis(
    val permeabilityClass: String,
    val soilTextureHint: String,
    val managementAdvice: String,
    val kostiakovInterpretation: String,
    val severityLevel: Int
)

data class InfiltrationAnalysis(
    val diameterM: Double,
    val outerDiameterM: Double,
    val waterDensityKgM3: Double,
    val waterTempC: Double,
    val viscosityCorrection20C: Double,
    val areaM2: Double,
    val areaCm2: Double,
    val rows: List<CalculatedRow>,
    val totals: TableTotals,
    val kostiakov: KostiakovModel,
    val modifiedKostiakov: ModifiedKostiakovModel,
    val philip: PhilipModel,
    val modelComparison: ModelComparison,
    val basicInfiltrationMmH: Double?,
    val steadyIntervalCount: Int,
    val isStabilized: Boolean,
    val stabilizationMessage: String,
    val kSatMS: Double?,
    val kSatCmH: Double?,
    val kSat20MS: Double?,
    val kSat20CmH: Double?,
    val initialRateMmH: Double?,
    val reductionPercent: Double?,
    val diagnosis: SoilDiagnosis?,
    val warnings: List<String>
)
