package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.CalculatedRow
import com.example.domain.InfiltrationCalculator
import com.example.domain.KostiakovModel
import com.example.domain.NumberFormatters
import com.example.domain.PhysicalStage
import com.example.ui.theme.MonoTextStyles
import com.example.ui.theme.StageCapillaryColor
import com.example.ui.theme.StageDarcyColor
import com.example.ui.theme.StageTransitionColor
import kotlin.math.abs
import kotlin.math.max

@Composable
fun InstantRateChartCard(
    rows: List<CalculatedRow>,
    kostiakov: KostiakovModel,
    basicInfiltrationMmH: Double?,
    modifier: Modifier = Modifier
) {
    val activePoints = remember(rows) {
        rows.filter { !it.isInitialRow && it.instantRateMmH != null && it.cumulativeTimeMin > 0.0 }
    }
    var selectedIdx by remember(activePoints) { mutableStateOf<Int?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val kostiakovColor = MaterialTheme.colorScheme.secondary
    val steadyColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    ChartContainerCard(
        title = "Velocidad de infiltración instantánea i(t) vs. t",
        subtitle = "Curva observada (mm/h), ajuste de Kostiakov y régimen estable i_b",
        testTag = "chart_instant_rate",
        modifier = modifier
    ) {
        if (activePoints.isEmpty()) {
            EmptyChartNotice("Agrega al menos un intervalo activo (Δt > 0) para graficar i(t).")
        } else {
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendChip(color = primaryColor, label = "Observada i(t)")
                if (kostiakov.isValid) {
                    LegendChip(color = kostiakovColor, label = "Kostiakov k·tⁿ")
                }
                if (basicInfiltrationMmH != null) {
                    LegendChip(color = steadyColor, label = "Básica i_b")
                }
            }
            Spacer(Modifier.height(8.dp))

            selectedIdx?.let { idx ->
                activePoints.getOrNull(idx)?.let { pt ->
                    val pred = if (kostiakov.isValid && kostiakov.k != null && kostiakov.n != null) {
                        InfiltrationCalculator.evaluateKostiakov(kostiakov.k, kostiakov.n, pt.cumulativeTimeMin)
                    } else null
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Intervalo #${pt.orderIndex} · t = ${NumberFormatters.formatDecimal(pt.cumulativeTimeMin, 1)} min",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = buildString {
                                    append("i = ${NumberFormatters.formatDecimal(pt.instantRateMmH, 1)} mm/h")
                                    if (pred != null) append(" | Mod: ${NumberFormatters.formatDecimal(pred, 1)}")
                                },
                                style = MonoTextStyles.tableCellBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            val density = LocalDensity.current
            val textSizePx = with(density) { 10.sp.toPx() }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .pointerInput(activePoints) {
                        detectTapGestures { tap ->
                            val padL = 52f
                            val padR = 18f
                            val maxX = max(activePoints.maxOf { it.cumulativeTimeMin }, 1.0)
                            val plotW = (size.width - padL - padR).coerceAtLeast(1f)
                            var bestIdx = 0
                            var bestDist = Float.MAX_VALUE
                            activePoints.forEachIndexed { idx, r ->
                                val px = padL + (r.cumulativeTimeMin / maxX).toFloat() * plotW
                                val dist = abs(tap.x - px)
                                if (dist < bestDist) {
                                    bestDist = dist
                                    bestIdx = idx
                                }
                            }
                            selectedIdx = if (selectedIdx == bestIdx) null else bestIdx
                        }
                    }
            ) {
                val padL = 52.dp.toPx()
                val padR = 16.dp.toPx()
                val padT = 14.dp.toPx()
                val padB = 28.dp.toPx()
                val plotW = (size.width - padL - padR).coerceAtLeast(1f)
                val plotH = (size.height - padT - padB).coerceAtLeast(1f)

                val maxX = max(activePoints.maxOf { it.cumulativeTimeMin }, 1.0)
                val maxObservedY = activePoints.maxOf { it.instantRateMmH ?: 0.0 }
                val maxY = max(max(maxObservedY, basicInfiltrationMmH ?: 0.0) * 1.18, 1.0)

                fun mapX(t: Double) = padL + (t / maxX).toFloat() * plotW
                fun mapY(v: Double) = padT + plotH - ((v / maxY).toFloat().coerceIn(0f, 1.25f) * plotH)

                val textPaint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = textSizePx
                    isAntiAlias = true
                }

                // Horizontal gridlines & Y labels
                for (s in 0..4) {
                    val frac = s / 4f
                    val yPos = padT + plotH * (1f - frac)
                    drawLine(gridColor, Offset(padL, yPos), Offset(padL + plotW, yPos), strokeWidth = 1.dp.toPx())
                    val label = NumberFormatters.formatDecimal(maxY * frac, if (maxY >= 100) 0 else 1)
                    drawContext.canvas.nativeCanvas.drawText(label, 4.dp.toPx(), yPos + textSizePx / 3f, textPaint)
                }

                // X axis labels
                drawContext.canvas.nativeCanvas.drawText("0", padL - 4f, padT + plotH + 18.dp.toPx(), textPaint)
                val midT = NumberFormatters.formatDecimal(maxX / 2.0, 0)
                drawContext.canvas.nativeCanvas.drawText("$midT min", padL + plotW / 2f - 18.dp.toPx(), padT + plotH + 18.dp.toPx(), textPaint)
                val endT = NumberFormatters.formatDecimal(maxX, 0)
                drawContext.canvas.nativeCanvas.drawText("$endT min", padL + plotW - 32.dp.toPx(), padT + plotH + 18.dp.toPx(), textPaint)

                // Basic infiltration dashed line
                if (basicInfiltrationMmH != null && basicInfiltrationMmH.isFinite()) {
                    val sy = mapY(basicInfiltrationMmH)
                    drawLine(
                        color = steadyColor,
                        start = Offset(padL, sy),
                        end = Offset(padL + plotW, sy),
                        strokeWidth = 1.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                }

                // Kostiakov fitted curve
                if (kostiakov.isValid && kostiakov.k != null && kostiakov.n != null) {
                    val fitPath = Path()
                    val minT = activePoints.minOf { it.cumulativeTimeMin }.coerceAtLeast(0.25)
                    var started = false
                    for (step in 0..48) {
                        val t = minT + (maxX - minT) * (step / 48.0)
                        val pred = InfiltrationCalculator.evaluateKostiakov(kostiakov.k, kostiakov.n, t)
                        if (pred != null && pred <= maxY * 1.15) {
                            val px = mapX(t)
                            val py = mapY(pred)
                            if (!started) {
                                fitPath.moveTo(px, py)
                                started = true
                            } else {
                                fitPath.lineTo(px, py)
                            }
                        }
                    }
                    if (started) {
                        drawPath(
                            path = fitPath,
                            color = kostiakovColor,
                            style = Stroke(
                                width = 2.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                // Observed polyline + gradient fill
                val linePath = Path()
                val fillPath = Path()
                activePoints.forEachIndexed { idx, row ->
                    val px = mapX(row.cumulativeTimeMin)
                    val py = mapY(row.instantRateMmH ?: 0.0)
                    if (idx == 0) {
                        linePath.moveTo(px, py)
                        fillPath.moveTo(px, padT + plotH)
                        fillPath.lineTo(px, py)
                    } else {
                        linePath.lineTo(px, py)
                        fillPath.lineTo(px, py)
                    }
                    if (idx == activePoints.lastIndex) {
                        fillPath.lineTo(px, padT + plotH)
                        fillPath.close()
                    }
                }

                if (activePoints.size >= 2) {
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.22f), Color.Transparent),
                            startY = padT,
                            endY = padT + plotH
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = primaryColor,
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // Observed data points
                activePoints.forEachIndexed { idx, row ->
                    val px = mapX(row.cumulativeTimeMin)
                    val py = mapY(row.instantRateMmH ?: 0.0)
                    val isSel = selectedIdx == idx
                    drawCircle(
                        color = if (isSel) kostiakovColor else Color.White,
                        radius = if (isSel) 6.5.dp.toPx() else 4.5.dp.toPx(),
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = if (isSel) kostiakovColor else primaryColor,
                        radius = if (isSel) 6.5.dp.toPx() else 4.5.dp.toPx(),
                        center = Offset(px, py),
                        style = Stroke(width = 2.2.dp.toPx())
                    )
                }
            }
            Text(
                text = "Toca un punto del gráfico para inspeccionar su valor exacto.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CumulativeDepthChartCard(
    rows: List<CalculatedRow>,
    modifier: Modifier = Modifier
) {
    var selectedIdx by remember(rows) { mutableStateOf<Int?>(null) }
    val lineColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    ChartContainerCard(
        title = "Lámina acumulada I(t) vs. tiempo t",
        subtitle = "Volumen total infiltrado expresado en milímetros de altura (mm)",
        testTag = "chart_cumulative_depth",
        modifier = modifier
    ) {
        if (rows.size < 2) {
            EmptyChartNotice("Se requieren lecturas activas después de t = 0 para graficar I(t).")
        } else {
            selectedIdx?.let { idx ->
                rows.getOrNull(idx)?.let { pt ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Lectura #${pt.orderIndex} · t = ${NumberFormatters.formatDecimal(pt.cumulativeTimeMin, 1)} min",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "I(t) = ${NumberFormatters.formatDecimal(pt.cumulativeIMm, 2)} mm",
                                style = MonoTextStyles.tableCellBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            val density = LocalDensity.current
            val textSizePx = with(density) { 10.sp.toPx() }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .pointerInput(rows) {
                        detectTapGestures { tap ->
                            val padL = 52f
                            val padR = 18f
                            val maxX = max(rows.maxOf { it.cumulativeTimeMin }, 1.0)
                            val plotW = (size.width - padL - padR).coerceAtLeast(1f)
                            var bestIdx = 0
                            var bestDist = Float.MAX_VALUE
                            rows.forEachIndexed { idx, r ->
                                val px = padL + (r.cumulativeTimeMin / maxX).toFloat() * plotW
                                val d = abs(tap.x - px)
                                if (d < bestDist) {
                                    bestDist = d
                                    bestIdx = idx
                                }
                            }
                            selectedIdx = if (selectedIdx == bestIdx) null else bestIdx
                        }
                    }
            ) {
                val padL = 52.dp.toPx()
                val padR = 16.dp.toPx()
                val padT = 14.dp.toPx()
                val padB = 28.dp.toPx()
                val plotW = (size.width - padL - padR).coerceAtLeast(1f)
                val plotH = (size.height - padT - padB).coerceAtLeast(1f)

                val maxX = max(rows.maxOf { it.cumulativeTimeMin }, 1.0)
                val maxY = max((rows.maxOf { it.cumulativeIMm }) * 1.15, 1.0)

                fun mapX(t: Double) = padL + (t / maxX).toFloat() * plotW
                fun mapY(v: Double) = padT + plotH - ((v / maxY).toFloat().coerceIn(0f, 1.15f) * plotH)

                val textPaint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = textSizePx
                    isAntiAlias = true
                }

                for (s in 0..4) {
                    val frac = s / 4f
                    val yPos = padT + plotH * (1f - frac)
                    drawLine(gridColor, Offset(padL, yPos), Offset(padL + plotW, yPos), strokeWidth = 1.dp.toPx())
                    val label = NumberFormatters.formatDecimal(maxY * frac, 1)
                    drawContext.canvas.nativeCanvas.drawText(label, 4.dp.toPx(), yPos + textSizePx / 3f, textPaint)
                }

                drawContext.canvas.nativeCanvas.drawText("0", padL - 4f, padT + plotH + 18.dp.toPx(), textPaint)
                drawContext.canvas.nativeCanvas.drawText(
                    "${NumberFormatters.formatDecimal(maxX, 0)} min",
                    padL + plotW - 32.dp.toPx(),
                    padT + plotH + 18.dp.toPx(),
                    textPaint
                )

                val linePath = Path()
                val areaPath = Path()
                rows.forEachIndexed { idx, row ->
                    val px = mapX(row.cumulativeTimeMin)
                    val py = mapY(row.cumulativeIMm)
                    if (idx == 0) {
                        linePath.moveTo(px, py)
                        areaPath.moveTo(px, padT + plotH)
                        areaPath.lineTo(px, py)
                    } else {
                        linePath.lineTo(px, py)
                        areaPath.lineTo(px, py)
                    }
                    if (idx == rows.lastIndex) {
                        areaPath.lineTo(px, padT + plotH)
                        areaPath.close()
                    }
                }

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                        startY = padT,
                        endY = padT + plotH
                    )
                )
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                rows.forEachIndexed { idx, row ->
                    val px = mapX(row.cumulativeTimeMin)
                    val py = mapY(row.cumulativeIMm)
                    val isSel = selectedIdx == idx
                    drawCircle(color = Color.White, radius = if (isSel) 6.dp.toPx() else 4.dp.toPx(), center = Offset(px, py))
                    drawCircle(
                        color = lineColor,
                        radius = if (isSel) 6.dp.toPx() else 4.dp.toPx(),
                        center = Offset(px, py),
                        style = Stroke(width = 2.2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun PartialDepthBarChartCard(
    rows: List<CalculatedRow>,
    modifier: Modifier = Modifier
) {
    val activeRows = remember(rows) { rows.filter { !it.isInitialRow && it.deltaIMm != null } }
    var selectedIdx by remember(activeRows) { mutableStateOf<Int?>(null) }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    ChartContainerCard(
        title = "Lámina parcial infiltrada ΔI por intervalo",
        subtitle = "Coloreada según etapa física: succión capilar, transición o régimen de Darcy",
        testTag = "chart_partial_bars",
        modifier = modifier
    ) {
        if (activeRows.isEmpty()) {
            EmptyChartNotice("Agrega intervalos para visualizar las barras de ΔI (mm).")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendChip(color = StageCapillaryColor, label = "Capilar")
                LegendChip(color = StageTransitionColor, label = "Transición")
                LegendChip(color = StageDarcyColor, label = "Darcy")
            }
            Spacer(Modifier.height(8.dp))

            selectedIdx?.let { idx ->
                activeRows.getOrNull(idx)?.let { r ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Intervalo #${r.orderIndex} · ${r.stage.shortLabel}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "ΔI = ${NumberFormatters.formatDecimal(r.deltaIMm, 2)} mm (${NumberFormatters.formatDecimal(r.massKg, 3)} kg)",
                                style = MonoTextStyles.tableCellBold
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            val density = LocalDensity.current
            val textSizePx = with(density) { 10.sp.toPx() }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .pointerInput(activeRows) {
                        detectTapGestures { tap ->
                            val padL = 48f
                            val padR = 16f
                            val plotW = (size.width - padL - padR).coerceAtLeast(1f)
                            val slotW = plotW / activeRows.size
                            val idx = ((tap.x - padL) / slotW).toInt().coerceIn(0, activeRows.lastIndex)
                            selectedIdx = if (selectedIdx == idx) null else idx
                        }
                    }
            ) {
                val padL = 48.dp.toPx()
                val padR = 14.dp.toPx()
                val padT = 14.dp.toPx()
                val padB = 28.dp.toPx()
                val plotW = (size.width - padL - padR).coerceAtLeast(1f)
                val plotH = (size.height - padT - padB).coerceAtLeast(1f)

                val maxVal = max((activeRows.maxOfOrNull { it.deltaIMm ?: 0.0 } ?: 1.0) * 1.18, 1.0)
                val textPaint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = textSizePx
                    isAntiAlias = true
                }

                for (s in 0..4) {
                    val frac = s / 4f
                    val yPos = padT + plotH * (1f - frac)
                    drawLine(gridColor, Offset(padL, yPos), Offset(padL + plotW, yPos), strokeWidth = 1.dp.toPx())
                    val label = NumberFormatters.formatDecimal(maxVal * frac, 1)
                    drawContext.canvas.nativeCanvas.drawText(label, 4.dp.toPx(), yPos + textSizePx / 3f, textPaint)
                }

                val slotW = plotW / activeRows.size
                val barW = (slotW * 0.62f).coerceIn(8.dp.toPx(), 40.dp.toPx())

                activeRows.forEachIndexed { idx, row ->
                    val valMm = (row.deltaIMm ?: 0.0).coerceAtLeast(0.0)
                    val barH = ((valMm / maxVal).toFloat() * plotH).coerceAtLeast(2.dp.toPx())
                    val bx = padL + idx * slotW + (slotW - barW) / 2f
                    val by = padT + plotH - barH

                    val barColor = when (row.stage) {
                        PhysicalStage.CAPILLARY_SUCTION -> StageCapillaryColor
                        PhysicalStage.TRANSITION -> StageTransitionColor
                        PhysicalStage.DARCY_STEADY -> StageDarcyColor
                        else -> Color.Gray
                    }
                    val isSel = selectedIdx == idx
                    drawRoundRect(
                        color = if (isSel) barColor else barColor.copy(alpha = 0.88f),
                        topLeft = Offset(bx, by),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    if (isSel) {
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.35f),
                            topLeft = Offset(bx, by),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    if (activeRows.size <= 14 || idx % 2 == 0) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "#${row.orderIndex}",
                            bx + barW / 2f - 6.dp.toPx(),
                            padT + plotH + 17.dp.toPx(),
                            textPaint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartContainerCard(
    title: String,
    subtitle: String,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyChartNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
