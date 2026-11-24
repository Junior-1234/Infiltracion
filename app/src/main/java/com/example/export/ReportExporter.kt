package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.InfiltrationTestEntity
import com.example.domain.InfiltrationAnalysis
import com.example.domain.InfiltrationCalculator
import com.example.domain.NumberFormatters
import com.example.domain.PhysicalStage
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel (.xlsx)"),
    PDF("pdf", "application/pdf", "Informe PDF (.pdf)"),
    CSV("csv", "text/csv", "Tabla CSV (.csv)")
}

object ReportExporter {

    fun exportAndShare(
        context: Context,
        test: InfiltrationTestEntity,
        analysis: InfiltrationAnalysis,
        format: ExportFormat
    ): Result<File> {
        return runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = test.plotName
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .trim('_')
                .ifEmpty { "ensayo_infiltracion" }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val outFile = File(exportDir, "${safeName}_${timestamp}.${format.extension}")

            when (format) {
                ExportFormat.XLSX -> writeXlsx(outFile, test, analysis)
                ExportFormat.PDF -> writePdf(outFile, test, analysis)
                ExportFormat.CSV -> writeCsv(outFile, test, analysis)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ensayo de Infiltración: ${test.plotName}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Reporte de infiltración en suelos (${test.plotName}) generado sin conexión."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Compartir ${format.label}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            outFile
        }
    }

    private fun writeCsv(file: File, test: InfiltrationTestEntity, analysis: InfiltrationAnalysis) {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine("ENSAYO DE INFILTRACIÓN EN SUELOS (CILINDRO INFILTRÓMETRO)")
        sb.appendLine("Parcela / Lote,${csvEscape(test.plotName)}")
        sb.appendLine("Diámetro interno D (m),${NumberFormatters.formatDecimal(analysis.diameterM, 4)}")
        sb.appendLine("Diámetro externo D_ext (m),${NumberFormatters.formatDecimal(analysis.outerDiameterM, 4)}")
        sb.appendLine("Área transversal A (m²),${NumberFormatters.formatDecimal(analysis.areaM2, 6)}")
        sb.appendLine("Densidad del agua ρ (kg/m³),${NumberFormatters.formatDecimal(analysis.waterDensityKgM3, 2)}")
        sb.appendLine("Temperatura del agua T (°C),${NumberFormatters.formatDecimal(analysis.waterTempC, 1)}")
        if (test.latitude != null && test.longitude != null) {
            sb.appendLine("Coordenadas GPS,${NumberFormatters.formatDecimal(test.latitude, 6)}; ${NumberFormatters.formatDecimal(test.longitude, 6)}")
        }
        sb.appendLine()
        sb.appendLine("#,Δt (min),Δt_s (s),t acum (min),Relleno Δh_r (cm),Lectura h (cm),ΔI (mm),I acum (mm),i(t) (mm/h),q (m/s),Q (cm³/s),Q (m³/s),Δm (kg),Etapa física")

        analysis.rows.forEach { r ->
            sb.appendLine(
                listOf(
                    r.orderIndex.toString(),
                    NumberFormatters.formatDecimal(r.deltaTMin, 2),
                    NumberFormatters.formatDecimal(r.deltaTSec, 1),
                    NumberFormatters.formatDecimal(r.cumulativeTimeMin, 2),
                    NumberFormatters.formatDecimal(r.refillCm, 2),
                    NumberFormatters.formatDecimal(r.waterLevelCm, 2),
                    NumberFormatters.formatDecimal(r.deltaIMm, 2),
                    NumberFormatters.formatDecimal(r.cumulativeIMm, 2),
                    NumberFormatters.formatDecimal(r.instantRateMmH, 2),
                    NumberFormatters.formatScientificPlain(r.fluxDensityMS, 3),
                    NumberFormatters.formatDecimal(r.flowRateCm3S, 4),
                    NumberFormatters.formatScientificPlain(r.flowRateM3S, 3),
                    NumberFormatters.formatDecimal(r.massKg, 4),
                    csvEscape(r.stage.label)
                ).joinToString(",")
            )
        }

        val t = analysis.totals
        sb.appendLine(
            listOf(
                "TOTAL / MEDIA",
                "—",
                NumberFormatters.formatDecimal(t.totalDeltaTSec, 1),
                NumberFormatters.formatDecimal(t.totalTimeMin, 2),
                NumberFormatters.formatDecimal(t.totalRefillCm, 2),
                "—",
                NumberFormatters.formatDecimal(t.totalDeltaIMm, 2),
                NumberFormatters.formatDecimal(t.totalCumulativeIMm, 2),
                NumberFormatters.formatDecimal(t.meanRateMmH, 2),
                NumberFormatters.formatScientificPlain(t.meanFluxMS, 3),
                NumberFormatters.formatDecimal(t.meanFlowCm3S, 4),
                NumberFormatters.formatScientificPlain(t.meanFlowM3S, 3),
                NumberFormatters.formatDecimal(t.totalMassKg, 4),
                "Promedios de ${t.activeIntervalCount} intervalos activos"
            ).joinToString(",")
        )

        sb.appendLine()
        sb.appendLine("MODELOS FÍSICOS Y DIAGNÓSTICO")
        sb.appendLine("Infiltración básica i_b (mm/h),${NumberFormatters.formatDecimal(analysis.basicInfiltrationMmH, 2)}")
        sb.appendLine("K_sat a T campo (m/s),${NumberFormatters.formatScientificPlain(analysis.kSatMS, 3)}")
        sb.appendLine("K_sat normalizada 20°C (m/s),${NumberFormatters.formatScientificPlain(analysis.kSat20MS, 3)}")
        sb.appendLine("Kostiakov k,${NumberFormatters.formatDecimal(analysis.kostiakov.k, 4)}")
        sb.appendLine("Kostiakov n,${NumberFormatters.formatDecimal(analysis.kostiakov.n, 4)}")
        sb.appendLine("Kostiakov R²,${NumberFormatters.formatDecimal(analysis.kostiakov.rSquared, 4)}")
        sb.appendLine("Kostiakov-Lewis k_m,${NumberFormatters.formatDecimal(analysis.modifiedKostiakov.k, 4)}")
        sb.appendLine("Kostiakov-Lewis n_m,${NumberFormatters.formatDecimal(analysis.modifiedKostiakov.n, 4)}")
        sb.appendLine("Kostiakov-Lewis R²,${NumberFormatters.formatDecimal(analysis.modifiedKostiakov.rSquared, 4)}")
        sb.appendLine("Philip Sortividad S (mm/min^0.5),${NumberFormatters.formatDecimal(analysis.philip.sorptivityS, 4)}")
        sb.appendLine("Philip Transmisividad A (mm/min),${NumberFormatters.formatDecimal(analysis.philip.transmissivityA, 4)}")
        sb.appendLine("Philip R²,${NumberFormatters.formatDecimal(analysis.philip.rSquared, 4)}")
        sb.appendLine("Reducción inicial-estable (%),${NumberFormatters.formatDecimal(analysis.reductionPercent, 1)}")
        analysis.diagnosis?.let { d ->
            sb.appendLine("Clase de permeabilidad,${csvEscape(d.permeabilityClass)}")
            sb.appendLine("Textura probable,${csvEscape(d.soilTextureHint)}")
            sb.appendLine("Diagnóstico,${csvEscape(d.managementAdvice)}")
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun writeXlsx(file: File, test: InfiltrationTestEntity, analysis: InfiltrationAnalysis) {
        val sheet1Xml = buildSheet1Xml(test, analysis)
        val sheet2Xml = buildSheet2Xml(test, analysis)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zos ->
            fun putXml(path: String, xml: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(xml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            putXml(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
            )

            putXml(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            putXml(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            )

            putXml(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Registro de Campo" sheetId="1" r:id="rId1"/>
    <sheet name="Modelos y Diagnostico" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""
            )

            putXml(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF0B6E6E"/><name val="Calibri"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0B6E6E"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE4F2F1"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFCCCCCC"/></left>
      <right style="thin"><color rgb="FFCCCCCC"/></right>
      <top style="thin"><color rgb="FFCCCCCC"/></top>
      <bottom style="thin"><color rgb="FFCCCCCC"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
  </cellXfs>
</styleSheet>"""
            )

            putXml("xl/worksheets/sheet1.xml", sheet1Xml)
            putXml("xl/worksheets/sheet2.xml", sheet2Xml)
        }
    }

    private fun buildSheet1Xml(test: InfiltrationTestEntity, analysis: InfiltrationAnalysis): String {
        val rowsXml = StringBuilder()
        var rowNum = 1

        fun addRow(cells: List<Pair<Any?, Int>>) {
            rowsXml.append("<row r=\"$rowNum\">")
            cells.forEachIndexed { colIdx, (value, style) ->
                val ref = "${colLetter(colIdx)}$rowNum"
                when (value) {
                    null -> rowsXml.append("<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t>—</t></is></c>")
                    is Number -> {
                        val d = value.toDouble()
                        if (d.isFinite()) {
                            rowsXml.append("<c r=\"$ref\" s=\"$style\"><v>$d</v></c>")
                        } else {
                            rowsXml.append("<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t>—</t></is></c>")
                        }
                    }
                    else -> rowsXml.append("<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t>${xmlEscape(value.toString())}</t></is></c>")
                }
            }
            rowsXml.append("</row>\n")
            rowNum++
        }

        addRow(listOf("ENSAYO DE INFILTRACIÓN EN SUELOS (CILINDRO INFILTRÓMETRO)" to 2))
        addRow(listOf("Parcela / Lote" to 2, test.plotName to 3, "Temp. agua (°C)" to 2, analysis.waterTempC to 3))
        addRow(listOf("Diámetro interno D (m)" to 2, analysis.diameterM to 3, "Diámetro externo (m)" to 2, analysis.outerDiameterM to 3, "Área A (m²)" to 2, analysis.areaM2 to 3))
        addRow(listOf("Densidad ρ (kg/m³)" to 2, analysis.waterDensityKgM3 to 3, "Latitud GPS" to 2, (test.latitude ?: "—") to 3, "Longitud GPS" to 2, (test.longitude ?: "—") to 3))
        rowNum++

        val headers = listOf(
            "#", "Δt (min)", "Δt_s (s)", "t acum (min)", "Relleno Δh_r (cm)",
            "Lectura h (cm)", "ΔI (mm)", "I(t) (mm)", "i(t) (mm/h)",
            "q (m/s)", "Q (cm³/s)", "Q (m³/s)", "Δm (kg)", "Etapa física"
        )
        addRow(headers.map { it to 1 })

        analysis.rows.forEach { r ->
            addRow(
                listOf(
                    r.orderIndex to 3,
                    r.deltaTMin to 3,
                    r.deltaTSec to 3,
                    r.cumulativeTimeMin to 3,
                    r.refillCm to 3,
                    r.waterLevelCm to 3,
                    r.deltaIMm to 3,
                    r.cumulativeIMm to 3,
                    r.instantRateMmH to 3,
                    r.fluxDensityMS to 3,
                    r.flowRateCm3S to 3,
                    r.flowRateM3S to 3,
                    r.massKg to 3,
                    r.stage.label to 3
                )
            )
        }

        val t = analysis.totals
        addRow(
            listOf(
                "TOTAL / MEDIA" to 2,
                "—" to 2,
                t.totalDeltaTSec to 2,
                t.totalTimeMin to 2,
                t.totalRefillCm to 2,
                "—" to 2,
                t.totalDeltaIMm to 2,
                t.totalCumulativeIMm to 2,
                t.meanRateMmH to 2,
                t.meanFluxMS to 2,
                t.meanFlowCm3S to 2,
                t.meanFlowM3S to 2,
                t.totalMassKg to 2,
                "Media de ${t.activeIntervalCount} intervalos activos" to 2
            )
        )

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    $rowsXml
  </sheetData>
</worksheet>"""
    }

    private fun buildSheet2Xml(test: InfiltrationTestEntity, analysis: InfiltrationAnalysis): String {
        val rowsXml = StringBuilder()
        var rowNum = 1

        fun addPair(label: String, value: Any?, style: Int = 3) {
            rowsXml.append("<row r=\"$rowNum\">")
            rowsXml.append("<c r=\"A$rowNum\" s=\"2\" t=\"inlineStr\"><is><t>${xmlEscape(label)}</t></is></c>")
            when (value) {
                null -> rowsXml.append("<c r=\"B$rowNum\" s=\"$style\" t=\"inlineStr\"><is><t>—</t></is></c>")
                is Number -> {
                    val d = value.toDouble()
                    if (d.isFinite()) {
                        rowsXml.append("<c r=\"B$rowNum\" s=\"$style\"><v>$d</v></c>")
                    } else {
                        rowsXml.append("<c r=\"B$rowNum\" s=\"$style\" t=\"inlineStr\"><is><t>—</t></is></c>")
                    }
                }
                else -> rowsXml.append("<c r=\"B$rowNum\" s=\"$style\" t=\"inlineStr\"><is><t>${xmlEscape(value.toString())}</t></is></c>")
            }
            rowsXml.append("</row>\n")
            rowNum++
        }

        addPair("Parcela / Lote", test.plotName, 2)
        addPair("Infiltración básica i_b (mm/h)", analysis.basicInfiltrationMmH)
        addPair("K_sat a T campo (m/s)", analysis.kSatMS)
        addPair("K_sat normalizada a 20 °C (m/s)", analysis.kSat20MS)
        addPair("Factor corrección viscosidad μ(T)/μ(20°C)", analysis.viscosityCorrection20C)
        addPair("Kostiakov simple: k", analysis.kostiakov.k)
        addPair("Kostiakov simple: n", analysis.kostiakov.n)
        addPair("Kostiakov simple: R²", analysis.kostiakov.rSquared)
        addPair("Kostiakov-Lewis: k_m", analysis.modifiedKostiakov.k)
        addPair("Kostiakov-Lewis: n_m", analysis.modifiedKostiakov.n)
        addPair("Kostiakov-Lewis: R²", analysis.modifiedKostiakov.rSquared)
        addPair("Philip: Sortividad S (mm/min^0.5)", analysis.philip.sorptivityS)
        addPair("Philip: Transmisividad A (mm/min)", analysis.philip.transmissivityA)
        addPair("Philip: R²", analysis.philip.rSquared)
        addPair("Mejor modelo (por R²)", analysis.modelComparison.bestModelName)
        addPair("Reducción inicial-estable (%)", analysis.reductionPercent)
        addPair("Estado de estabilización", analysis.stabilizationMessage)
        analysis.diagnosis?.let { d ->
            addPair("Clase de permeabilidad", d.permeabilityClass)
            addPair("Textura probable del suelo", d.soilTextureHint)
            addPair("Interpretación Kostiakov", d.kostiakovInterpretation)
            addPair("Recomendación de manejo", d.managementAdvice)
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    $rowsXml
  </sheetData>
</worksheet>"""
    }

    private fun colLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + (i % 26)))
            i = (i / 26) - 1
        }
        return sb.toString()
    }

    private fun xmlEscape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun writePdf(file: File, test: InfiltrationTestEntity, analysis: InfiltrationAnalysis) {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val titlePaint = Paint().apply {
            color = Color.parseColor("#0B6E6E")
            textSize = 15.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = 9.5f
            isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = Color.parseColor("#0B6E6E")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val cellPaint = Paint().apply {
            color = Color.parseColor("#191C1C")
            textSize = 7.6f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headerBgPaint = Paint().apply { color = Color.parseColor("#0B6E6E") }
        val altRowPaint = Paint().apply { color = Color.parseColor("#F2F6F6") }
        val totalBgPaint = Paint().apply { color = Color.parseColor("#DDF0EE") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
        }

        // Page 1
        val page1 = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val c1 = page1.canvas
        var y = 36f
        val left = 28f
        val right = pageWidth - 28f

        // Optional site photo thumbnail at top right
        var textMaxRight = right
        test.photoPath?.let { path ->
            val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            if (bmp != null) {
                val photoW = 84f
                val photoH = 62f
                val dst = Rect((right - photoW).toInt(), 24, right.toInt(), (24 + photoH).toInt())
                c1.drawBitmap(bmp, null, dst, null)
                c1.drawRect(dst, borderPaint)
                bmp.recycle()
                textMaxRight = right - photoW - 10f
            }
        }

        c1.drawText("Informe de Infiltración — Cilindro Infiltrómetro", left, y, titlePaint)
        y += 15f
        c1.drawText(
            "Parcela: ${test.plotName}  |  D = ${NumberFormatters.formatDecimal(analysis.diameterM, 3)} m  |  A = ${NumberFormatters.formatDecimal(analysis.areaM2, 5)} m²",
            left, y, subtitlePaint
        )
        y += 13f
        val gpsText = if (test.latitude != null && test.longitude != null) {
            "  |  GPS: ${NumberFormatters.formatDecimal(test.latitude, 5)}, ${NumberFormatters.formatDecimal(test.longitude, 5)}"
        } else ""
        c1.drawText(
            "ρ = ${NumberFormatters.formatDecimal(analysis.waterDensityKgM3, 1)} kg/m³  |  T = ${NumberFormatters.formatDecimal(analysis.waterTempC, 1)} °C$gpsText",
            left, y, subtitlePaint
        )
        y = maxOf(y + 18f, 98f)

        c1.drawText("1. Registro de Campo y Variables Calculadas", left, y, sectionPaint)
        y += 10f

        val colWidths = floatArrayOf(20f, 34f, 34f, 38f, 36f, 36f, 40f, 42f, 46f, 52f, 46f, 42f, 73f)
        val colTitles = arrayOf("#", "Δt", "Δts", "t", "Δhr", "h", "ΔI", "I(t)", "i(t)", "q (m/s)", "Q(cm³/s)", "Δm(kg)", "Etapa")
        val rowHeight = 14.5f

        c1.drawRect(left, y, right, y + rowHeight, headerBgPaint)
        var x = left + 3f
        colTitles.forEachIndexed { idx, title ->
            c1.drawText(title, x, y + 10f, headerTextPaint)
            x += colWidths[idx]
        }
        y += rowHeight

        val maxRowsPage1 = minOf(analysis.rows.size, 22)
        for (idx in 0 until maxRowsPage1) {
            val r = analysis.rows[idx]
            if (idx % 2 == 1) c1.drawRect(left, y, right, y + rowHeight, altRowPaint)
            c1.drawRect(left, y, right, y + rowHeight, borderPaint)
            val vals = arrayOf(
                r.orderIndex.toString(),
                NumberFormatters.formatDecimal(r.deltaTMin, 1),
                NumberFormatters.formatDecimal(r.deltaTSec, 0),
                NumberFormatters.formatDecimal(r.cumulativeTimeMin, 1),
                NumberFormatters.formatDecimal(r.refillCm, 1),
                NumberFormatters.formatDecimal(r.waterLevelCm, 2),
                NumberFormatters.formatDecimal(r.deltaIMm, 1),
                NumberFormatters.formatDecimal(r.cumulativeIMm, 1),
                NumberFormatters.formatDecimal(r.instantRateMmH, 1),
                NumberFormatters.formatScientificPlain(r.fluxDensityMS, 1),
                NumberFormatters.formatDecimal(r.flowRateCm3S, 2),
                NumberFormatters.formatDecimal(r.massKg, 2),
                r.stage.shortLabel
            )
            x = left + 3f
            vals.forEachIndexed { cIdx, v ->
                c1.drawText(v, x, y + 10f, cellPaint)
                x += colWidths[cIdx]
            }
            y += rowHeight
        }

        val tot = analysis.totals
        c1.drawRect(left, y, right, y + rowHeight, totalBgPaint)
        c1.drawRect(left, y, right, y + rowHeight, borderPaint)
        val totVals = arrayOf(
            "Σ/x̄", "—",
            NumberFormatters.formatDecimal(tot.totalDeltaTSec, 0),
            NumberFormatters.formatDecimal(tot.totalTimeMin, 1),
            NumberFormatters.formatDecimal(tot.totalRefillCm, 1),
            "—",
            NumberFormatters.formatDecimal(tot.totalDeltaIMm, 1),
            NumberFormatters.formatDecimal(tot.totalCumulativeIMm, 1),
            NumberFormatters.formatDecimal(tot.meanRateMmH, 1),
            NumberFormatters.formatScientificPlain(tot.meanFluxMS, 1),
            NumberFormatters.formatDecimal(tot.meanFlowCm3S, 2),
            NumberFormatters.formatDecimal(tot.totalMassKg, 2),
            "Media/Total"
        )
        x = left + 3f
        totVals.forEachIndexed { cIdx, v ->
            c1.drawText(v, x, y + 10f, cellPaint)
            x += colWidths[cIdx]
        }
        y += 22f

        c1.drawText("2. Modelos Físicos (Kostiakov, Kostiakov-Lewis, Philip y Darcy)", left, y, sectionPaint)
        y += 13f
        if (analysis.kostiakov.isValid) {
            c1.drawText(
                "• Kostiakov: i(t) = ${NumberFormatters.formatDecimal(analysis.kostiakov.k, 3)}·t^(${NumberFormatters.formatDecimal(analysis.kostiakov.n, 3)})  |  R² = ${NumberFormatters.formatDecimal(analysis.kostiakov.rSquared, 4)}",
                left, y, subtitlePaint
            )
            y += 12f
        }
        if (analysis.modifiedKostiakov.isValid) {
            c1.drawText(
                "• Kostiakov-Lewis: i(t) = ${NumberFormatters.formatDecimal(analysis.modifiedKostiakov.k, 3)}·t^(${NumberFormatters.formatDecimal(analysis.modifiedKostiakov.n, 3)}) + ${NumberFormatters.formatDecimal(analysis.modifiedKostiakov.ib, 2)}  |  R² = ${NumberFormatters.formatDecimal(analysis.modifiedKostiakov.rSquared, 4)}",
                left, y, subtitlePaint
            )
            y += 12f
        }
        if (analysis.philip.isValid) {
            c1.drawText(
                "• Philip: I(t) = ${NumberFormatters.formatDecimal(analysis.philip.sorptivityS, 3)}·t^0.5 + ${NumberFormatters.formatDecimal(analysis.philip.transmissivityA, 3)}·t  |  R² = ${NumberFormatters.formatDecimal(analysis.philip.rSquared, 4)}",
                left, y, subtitlePaint
            )
            y += 12f
        }
        c1.drawText(
            "• i_b = ${NumberFormatters.formatDecimal(analysis.basicInfiltrationMmH, 2)} mm/h  |  K_sat(T) = ${NumberFormatters.formatScientific(analysis.kSatMS, 2)} m/s  |  K_sat(20°C) = ${NumberFormatters.formatScientific(analysis.kSat20MS, 2)} m/s",
            left, y, subtitlePaint
        )
        y += 16f

        analysis.diagnosis?.let { d ->
            c1.drawText("3. Diagnóstico Hidráulico Automático", left, y, sectionPaint)
            y += 13f
            c1.drawText("Permeabilidad: ${d.permeabilityClass} — Textura probable: ${d.soilTextureHint}", left, y, subtitlePaint)
            y += 12f
            drawWrappedText(c1, d.managementAdvice, left, y, textMaxRight - left + (right - textMaxRight), subtitlePaint)
        }

        pdf.finishPage(page1)

        // Page 2: Vector Charts
        val page2 = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create())
        val c2 = page2.canvas
        c2.drawText("Gráficos del Movimiento del Agua — ${test.plotName}", left, 38f, titlePaint)

        drawPdfLineChart(
            canvas = c2,
            title = "Velocidad de infiltración instantánea i(t) vs. t (con ajuste Kostiakov)",
            xVals = analysis.rows.filter { !it.isInitialRow && it.instantRateMmH != null }.map { it.cumulativeTimeMin },
            yVals = analysis.rows.filter { !it.isInitialRow && it.instantRateMmH != null }.map { it.instantRateMmH!! },
            kostiakov = analysis.kostiakov,
            steadyValue = analysis.basicInfiltrationMmH,
            lineColor = Color.parseColor("#0B6E6E"),
            left = left, top = 62f, width = right - left, height = 210f
        )

        drawPdfLineChart(
            canvas = c2,
            title = "Lámina acumulada I(t) vs. tiempo acumulado t (mm)",
            xVals = analysis.rows.map { it.cumulativeTimeMin },
            yVals = analysis.rows.map { it.cumulativeIMm },
            kostiakov = null,
            steadyValue = null,
            lineColor = Color.parseColor("#A8552E"),
            left = left, top = 305f, width = right - left, height = 210f
        )

        drawPdfBarChart(
            canvas = c2,
            title = "Lámina parcial infiltrada ΔI por intervalo (mm)",
            rows = analysis.rows.filter { !it.isInitialRow && it.deltaIMm != null },
            left = left, top = 548f, width = right - left, height = 210f
        )

        pdf.finishPage(page2)

        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint) {
        val words = text.split(" ")
        var line = ""
        var y = startY
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(testLine) > maxWidth) {
                canvas.drawText(line, x, y, paint)
                line = word
                y += 12f
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line, x, y, paint)
    }

    private fun drawPdfLineChart(
        canvas: Canvas,
        title: String,
        xVals: List<Double>,
        yVals: List<Double>,
        kostiakov: com.example.domain.KostiakovModel?,
        steadyValue: Double?,
        lineColor: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val titleP = Paint().apply { color = Color.BLACK; textSize = 10.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val axisP = Paint().apply { color = Color.DKGRAY; strokeWidth = 1f; style = Paint.Style.STROKE }
        val gridP = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f; style = Paint.Style.STROKE }
        val labelP = Paint().apply { color = Color.DKGRAY; textSize = 8f; isAntiAlias = true }

        canvas.drawText(title, left, top + 10f, titleP)
        val plotLeft = left + 40f
        val plotTop = top + 24f
        val plotRight = left + width - 14f
        val plotBottom = top + height - 24f

        canvas.drawRect(plotLeft, plotTop, plotRight, plotBottom, axisP)

        if (xVals.isEmpty() || yVals.isEmpty()) {
            canvas.drawText("Sin suficientes datos para graficar", plotLeft + 20f, (plotTop + plotBottom) / 2f, labelP)
            return
        }

        val maxX = max(xVals.maxOrNull() ?: 1.0, 1.0)
        val maxY = max((yVals.maxOrNull() ?: 1.0) * 1.15, 1.0)

        fun mapX(v: Double) = plotLeft + ((v / maxX).toFloat() * (plotRight - plotLeft))
        fun mapY(v: Double) = plotBottom - ((v / maxY).toFloat().coerceIn(0f, 1.2f) * (plotBottom - plotTop))

        for (step in 0..4) {
            val frac = step / 4f
            val gy = plotBottom - frac * (plotBottom - plotTop)
            canvas.drawLine(plotLeft, gy, plotRight, gy, gridP)
            canvas.drawText(NumberFormatters.formatDecimal(maxY * frac, 1), left + 2f, gy + 3f, labelP)
        }

        if (steadyValue != null && steadyValue.isFinite()) {
            val sy = mapY(steadyValue)
            val dashP = Paint().apply {
                color = Color.parseColor("#2E6F40")
                strokeWidth = 1.4f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
            }
            canvas.drawLine(plotLeft, sy, plotRight, sy, dashP)
        }

        if (kostiakov != null && kostiakov.isValid && kostiakov.k != null && kostiakov.n != null) {
            val fitPath = Path()
            val minT = xVals.filter { it > 0.0 }.minOrNull() ?: 0.5
            var started = false
            for (s in 0..40) {
                val t = minT + (maxX - minT) * (s / 40.0)
                val pred = InfiltrationCalculator.evaluateKostiakov(kostiakov.k, kostiakov.n, t)
                if (pred != null && pred <= maxY * 1.3) {
                    val px = mapX(t)
                    val py = mapY(pred)
                    if (!started) { fitPath.moveTo(px, py); started = true } else fitPath.lineTo(px, py)
                }
            }
            val fitP = Paint().apply {
                color = Color.parseColor("#A8552E")
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
                isAntiAlias = true
            }
            canvas.drawPath(fitPath, fitP)
        }

        val lineP = Paint().apply { color = lineColor; strokeWidth = 2.2f; style = Paint.Style.STROKE; isAntiAlias = true }
        val dotP = Paint().apply { color = lineColor; style = Paint.Style.FILL; isAntiAlias = true }
        val path = Path()
        xVals.indices.forEach { idx ->
            val px = mapX(xVals[idx])
            val py = mapY(yVals[idx])
            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
            canvas.drawCircle(px, py, 3f, dotP)
        }
        canvas.drawPath(path, lineP)
        canvas.drawText("0 min", plotLeft, plotBottom + 12f, labelP)
        canvas.drawText("${NumberFormatters.formatDecimal(maxX, 0)} min", plotRight - 36f, plotBottom + 12f, labelP)
    }

    private fun drawPdfBarChart(
        canvas: Canvas,
        title: String,
        rows: List<com.example.domain.CalculatedRow>,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val titleP = Paint().apply { color = Color.BLACK; textSize = 10.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val axisP = Paint().apply { color = Color.DKGRAY; strokeWidth = 1f; style = Paint.Style.STROKE }
        val labelP = Paint().apply { color = Color.DKGRAY; textSize = 8f; isAntiAlias = true }

        canvas.drawText(title, left, top + 10f, titleP)
        val plotLeft = left + 40f
        val plotTop = top + 24f
        val plotRight = left + width - 14f
        val plotBottom = top + height - 24f
        canvas.drawRect(plotLeft, plotTop, plotRight, plotBottom, axisP)

        if (rows.isEmpty()) {
            canvas.drawText("Sin intervalos activos", plotLeft + 20f, (plotTop + plotBottom) / 2f, labelP)
            return
        }

        val maxVal = max(rows.maxOfOrNull { it.deltaIMm ?: 0.0 } ?: 1.0, 1.0) * 1.15
        val slotW = (plotRight - plotLeft) / rows.size
        val barW = (slotW * 0.65f).coerceAtLeast(6f)

        rows.forEachIndexed { idx, row ->
            val v = (row.deltaIMm ?: 0.0).coerceAtLeast(0.0)
            val barH = ((v / maxVal).toFloat() * (plotBottom - plotTop))
            val bx = plotLeft + idx * slotW + (slotW - barW) / 2f
            val barColor = when (row.stage) {
                PhysicalStage.CAPILLARY_SUCTION -> Color.parseColor("#D0602B")
                PhysicalStage.TRANSITION -> Color.parseColor("#1C7FA8")
                PhysicalStage.DARCY_STEADY -> Color.parseColor("#2E7D45")
                else -> Color.GRAY
            }
            val barP = Paint().apply { color = barColor; style = Paint.Style.FILL }
            canvas.drawRect(bx, plotBottom - barH, bx + barW, plotBottom, barP)
            canvas.drawText("#${row.orderIndex}", bx, plotBottom + 11f, labelP)
        }
        canvas.drawText(NumberFormatters.formatDecimal(maxVal, 1), left + 2f, plotTop + 8f, labelP)
    }
}
