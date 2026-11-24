package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MonoTextStyles
import com.example.ui.theme.StageCapillaryColor
import com.example.ui.theme.StageDarcyColor
import com.example.ui.theme.StageTransitionColor

private data class FormulaSpec(
    val title: String,
    val equation: String,
    val units: String,
    val explanation: String,
    val category: String
)

private data class SymbolSpec(
    val symbol: String,
    val name: String,
    val units: String,
    val meaning: String
)

@Composable
fun FormulasScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val formulas = listOf(
        FormulaSpec(
            title = "Área transversal del cilindro",
            equation = "A = π · (D / 2)²",
            units = "m²",
            explanation = "Superficie circular de suelo expuesta dentro del anillo infiltrómetro interno de diámetro D.",
            category = "Geometría"
        ),
        FormulaSpec(
            title = "Intervalo en segundos (SI) y tiempo acumulado",
            equation = "Δt_s = Δt · 60     |     t = Σ Δt",
            units = "s  |  min",
            explanation = "Convierte el lapso entre lecturas a segundos para cálculos del Sistema Internacional y suma el tiempo transcurrido desde t = 0.",
            category = "Tiempo"
        ),
        FormulaSpec(
            title = "Lámina parcial infiltrada",
            equation = "ΔI = [(h_{i-1} + Δh_r) - h_i] · 10",
            units = "mm",
            explanation = "El descenso de nivel en el cilindro no es una fuga: representa el agua que infiltró verticalmente hacia el suelo. Si se recargó agua (Δh_r), se suma al nivel anterior antes de restar la nueva lectura h_i. El factor 10 convierte cm a mm.",
            category = "Lámina"
        ),
        FormulaSpec(
            title = "Lámina acumulada",
            equation = "I(t) = Σ ΔI",
            units = "mm",
            explanation = "Altura total de agua infiltrada desde el inicio del ensayo hasta el tiempo t.",
            category = "Lámina"
        ),
        FormulaSpec(
            title = "Velocidad de infiltración instantánea",
            equation = "i(t) = (ΔI / Δt) · 60",
            units = "mm/h",
            explanation = "Rapidez con que entra el agua en el intervalo. En la fila inicial (t = 0, Δt = 0) se deja en '—' para evitar división entre cero.",
            category = "Tasa / Flujo"
        ),
        FormulaSpec(
            title = "Densidad de flujo volumétrico (SI)",
            equation = "q = (ΔI · 0.001) / Δt_s",
            units = "m/s",
            explanation = "Volumen de agua que atraviesa una unidad de área por segundo (m³/(m²·s) = m/s). Equivale a i(t) expresada en metros por segundo.",
            category = "Tasa / Flujo"
        ),
        FormulaSpec(
            title = "Caudal o infiltración volumétrica",
            equation = "Q = A · q   (en m³/s;  ·10⁶ → cm³/s;  ·10³ → L/s)",
            units = "cm³/s, m³/s, L/s",
            explanation = "Caudal volumétrico total que abandona el cilindro hacia el perfil del suelo durante el intervalo.",
            category = "Tasa / Flujo"
        ),
        FormulaSpec(
            title = "Masa de agua infiltrada",
            equation = "Δm = ρ · A · (ΔI · 10⁻³)",
            units = "kg",
            explanation = "Masa de agua absorbida por el suelo en el intervalo usando la densidad ρ (1000 kg/m³ por defecto, o ajustada por temperatura T).",
            category = "Masa"
        ),
        FormulaSpec(
            title = "Modelo potencial de Kostiakov",
            equation = "i(t) = k · tⁿ   ⇒   ln(i) = ln(k) + n · ln(t)",
            units = "mm/h (t en min)",
            explanation = "Regresión lineal en logaritmos naturales sobre los intervalos activos (t > 0, i > 0). El exponente n < 0 refleja la caída de succión capilar; R² mide la calidad del ajuste.",
            category = "Modelos"
        ),
        FormulaSpec(
            title = "Modelo de Kostiakov-Lewis (modificado)",
            equation = "i(t) = k_m · tⁿᵐ + i_b   ⇒   ln(i - i_b) = ln(k_m) + n_m · ln(t)",
            units = "mm/h",
            explanation = "Agrega la asíntota estacionaria i_b al término potencial para representar con mayor precisión ensayos largos en régimen cercano a la saturación.",
            category = "Modelos"
        ),
        FormulaSpec(
            title = "Modelo físico de Philip",
            equation = "I(t) = S · t¹/² + A_p · t   ⇒   I(t)/√t = S + A_p · √t",
            units = "mm",
            explanation = "Separa la infiltración acumulada en sortividad capilar S (dominante al inicio) y transmisividad gravitacional A_p (dominante a tiempos largos).",
            category = "Modelos"
        ),
        FormulaSpec(
            title = "Ley de Darcy y corrección térmica a 20 °C",
            equation = "K_sat(T) = (i_b · 0.001) / 3600   |   K_sat(20°C) = K_sat(T) · [μ(T) / μ(20°C)]",
            units = "m/s",
            explanation = "Con gradiente gravitacional unitario (|dH/dz| ≈ 1), K_sat iguala al flujo estable q_b. La corrección por viscosidad dinámica μ(T) permite comparar ensayos a distintas temperaturas.",
            category = "Modelos"
        )
    )

    val symbols = listOf(
        SymbolSpec("D / D_ext", "Diámetros del infiltrómetro", "m", "Diámetro interno de medición D y anillo de guarda externo."),
        SymbolSpec("A", "Área transversal", "m²", "Área circular de infiltración: π·(D/2)²."),
        SymbolSpec("ρ / T", "Densidad y Temp. del agua", "kg/m³ / °C", "Densidad por defecto 1000 kg/m³ o calculada según T."),
        SymbolSpec("Δt / Δt_s", "Intervalo de tiempo", "min / s", "Duración transcurrida entre dos lecturas consecutivas."),
        SymbolSpec("t", "Tiempo acumulado", "min", "Suma progresiva de todos los Δt desde t = 0."),
        SymbolSpec("h_{i-1}, h_i", "Lecturas del nivel de agua", "cm", "Altura de lámina medida con regla dentro del anillo."),
        SymbolSpec("Δh_r", "Lámina de relleno", "cm", "Agua añadida para recargar el cilindro antes de la lectura h_i."),
        SymbolSpec("ΔI", "Lámina parcial infiltrada", "mm", "Agua que infiltró verticalmente durante el intervalo."),
        SymbolSpec("I(t)", "Lámina acumulada", "mm", "Suma total de ΔI hasta el instante t."),
        SymbolSpec("i(t)", "Velocidad instantánea", "mm/h", "Tasa de entrada de agua al suelo en el intervalo."),
        SymbolSpec("q", "Densidad de flujo volumétrico", "m/s", "Tasa de flujo en unidades del Sistema Internacional."),
        SymbolSpec("Q", "Caudal de infiltración", "cm³/s, m³/s", "Volumen de agua infiltrado por unidad de tiempo (A·q)."),
        SymbolSpec("Δm", "Masa de agua infiltrada", "kg", "Masa equivalente de la lámina ΔI en el área A."),
        SymbolSpec("k, n, R²", "Parámetros de Kostiakov", "adim / coef.", "Coeficiente inicial k, exponente n y bondad de ajuste R²."),
        SymbolSpec("S, A_p", "Parámetros de Philip", "mm/min¹/², mm/min", "Sortividad capilar S y transmisividad gravitacional A_p."),
        SymbolSpec("i_b", "Infiltración básica", "mm/h", "Tasa constante alcanzada en el régimen estable final."),
        SymbolSpec("K_sat", "Conductividad hidráulica saturada", "m/s", "Permeabilidad saturada por Darcy y normalizada a 20 °C.")
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Nota física de campo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Interpretación física correcta en campo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "El descenso del nivel de agua dentro del anillo NO es una fuga del equipo: representa el agua que infiltra verticalmente hacia el perfil del suelo. Además, en la fila inicial (t = 0) las tasas i, q y Q quedan en '—' porque Δt = 0 (evita divisiones entre cero).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            item { DataConnectionDiagramCard() }

            item {
                Text(
                    text = "Ecuaciones y física del proceso",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(formulas) { item -> FormulaDetailCard(item) }

            item {
                Text(
                    text = "Etapas físicas del ensayo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item { PhysicalStagesLegendCard() }

            item {
                Text(
                    text = "Glosario completo de símbolos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        symbols.forEachIndexed { idx, sym ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = sym.symbol,
                                        style = MonoTextStyles.tableCellBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = sym.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = sym.units,
                                            style = MonoTextStyles.tableCell,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = sym.meaning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (idx < symbols.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataConnectionDiagramCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Conexión entre mediciones y modelos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            FlowChainStep(
                icon = Icons.Filled.Layers,
                accent = MaterialTheme.colorScheme.primary,
                stepTitle = "1. Geometría y Lecturas de Campo",
                chain = listOf("D → A = π(D/2)²", "(hᵢ₋₁ + Δhᵣ - hᵢ)·10 → ΔI", "Σ ΔI → I(t)")
            )
            FlowChainStep(
                icon = Icons.Filled.WaterDrop,
                accent = MaterialTheme.colorScheme.secondary,
                stepTitle = "2. Tasas Instantáneas, Caudal y Masa",
                chain = listOf("ΔI / Δt → i(t) [mm/h]", "ΔI·10⁻³ / Δt_s → q [m/s]", "A·q → Q  |  ρ·A·ΔI·10⁻³ → Δm")
            )
            FlowChainStep(
                icon = Icons.Filled.Functions,
                accent = MaterialTheme.colorScheme.tertiary,
                stepTitle = "3. Regresión, Philip, Kostiakov-Lewis y Darcy",
                chain = listOf("ln(i) vs ln(t) → Kostiakov (k, n, R²)", "I(t)/√t vs √t → Philip (S, A_p, R²)", "i_b → K_sat(T) → K_sat(20°C)")
            )
        }
    }
}

@Composable
private fun FlowChainStep(
    icon: ImageVector,
    accent: Color,
    stepTitle: String,
    chain: List<String>
) {
    Surface(
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(text = stepTitle, style = MaterialTheme.typography.labelLarge, color = accent)
            }
            chain.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = accent.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(text = line, style = MonoTextStyles.tableCell)
                }
            }
        }
    }
}

@Composable
private fun FormulaDetailCard(spec: FormulaSpec) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = spec.units,
                        style = MonoTextStyles.tableCellBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = spec.equation,
                    style = MonoTextStyles.formula,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = spec.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhysicalStagesLegendCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StageExplainRow(
                color = StageCapillaryColor,
                title = "1. Succión capilar inicial",
                desc = "Los poros secos generan un gradiente mátrico intenso; i(t) es alta y decrece rápido."
            )
            StageExplainRow(
                color = StageTransitionColor,
                title = "2. Transición",
                desc = "El frente húmedo avanza, disminuye el gradiente capilar y la tasa i(t) se aproxima asintóticamente a i_b."
            )
            StageExplainRow(
                color = StageDarcyColor,
                title = "3. Régimen estable de Darcy",
                desc = "La zona superior queda saturada y domina el gradiente gravitacional unitario (dH/dz ≈ 1), permitiendo calcular K_sat."
            )
        }
    }
}

@Composable
private fun StageExplainRow(color: Color, title: String, desc: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
