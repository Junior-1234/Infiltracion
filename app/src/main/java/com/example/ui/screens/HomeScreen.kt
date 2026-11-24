package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.InfiltrationCalculator
import com.example.domain.NumberFormatters
import com.example.ui.theme.MonoTextStyles
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.TestSummaryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenTest: (Long) -> Unit,
    onCreateTest: (
        plotName: String,
        diameterM: Double,
        outerDiameterM: Double,
        densityKgM3: Double,
        waterTempC: Double,
        initialLevelCm: Double,
        notes: String
    ) -> Unit,
    onDeleteTest: (Long) -> Unit,
    onLoadSample: () -> Unit,
    bottomPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var testPendingDelete by remember { mutableStateOf<TestSummaryItem?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = bottomPadding.calculateBottomPadding())
                    .testTag("fab_new_test")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo ensayo de infiltración")
            }
        }
    ) { innerPadding ->
        val combinedBottom = innerPadding.calculateBottomPadding() + bottomPadding.calculateBottomPadding() + 80.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            when (uiState) {
                is HomeUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is HomeUiState.Ready -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = combinedBottom
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            HeroBannerCard(onNewTest = { showCreateDialog = true })
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ensayos guardados (${uiState.tests.size})",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = onLoadSample,
                                    modifier = Modifier.testTag("btn_load_sample")
                                ) {
                                    Text("+ Ejemplo")
                                }
                            }
                        }

                        if (uiState.tests.isEmpty()) {
                            item {
                                EmptyTestsCard(
                                    onCreate = { showCreateDialog = true },
                                    onLoadSample = onLoadSample
                                )
                            }
                        } else {
                            items(uiState.tests, key = { it.test.id }) { item ->
                                TestSummaryCard(
                                    item = item,
                                    onClick = { onOpenTest(item.test.id) },
                                    onDelete = { testPendingDelete = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTestDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, diam, outerDiam, dens, tempC, h0, notes ->
                showCreateDialog = false
                onCreateTest(name, diam, outerDiam, dens, tempC, h0, notes)
            }
        )
    }

    testPendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { testPendingDelete = null },
            title = { Text("Eliminar ensayo") },
            text = {
                Text("¿Deseas eliminar \"${pending.test.plotName}\" y todos sus intervalos de campo? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTest(pending.test.id)
                        testPendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_test")
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { testPendingDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun HeroBannerCard(onNewTest: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_soil_hero),
                contentDescription = "Ilustración de infiltrómetro de doble anillo en perfil de suelo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color(0xFF063738).copy(alpha = 0.88f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "HIDROLOGÍA DE SUELOS · OFFLINE + 3D",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "Cilindros Infiltrómetros",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kostiakov, Philip, Darcy, Visor 3D y exportación XLSX/PDF",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onNewTest,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("hero_new_test_button")
                    ) {
                        Text("Nuevo", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun TestSummaryCard(
    item: TestSummaryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es")) }
    val analysis = item.analysis

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("test_card_${item.test.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.WaterDrop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column {
                        Text(
                            text = item.test.plotName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "D = ${NumberFormatters.formatDecimal(item.test.diameterM, 2)} m · ${item.rowCount} lecturas · ${dateFmt.format(Date(item.test.updatedAt))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_test_${item.test.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Eliminar ensayo ${item.test.plotName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniKpiBadge(
                    label = "I total",
                    value = "${NumberFormatters.formatDecimal(analysis.totals.totalCumulativeIMm, 1)} mm",
                    modifier = Modifier.weight(1f)
                )
                MiniKpiBadge(
                    label = "Básica i_b",
                    value = "${NumberFormatters.formatDecimal(analysis.basicInfiltrationMmH, 1)} mm/h",
                    modifier = Modifier.weight(1f)
                )
                MiniKpiBadge(
                    label = "K_sat",
                    value = NumberFormatters.formatScientific(analysis.kSatMS, 1),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = analysis.diagnosis?.permeabilityClass ?: "Pendiente de intervalos activos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Abrir registro",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniKpiBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MonoTextStyles.tableCellBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyTestsCard(onCreate: () -> Unit, onLoadSample: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Science,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "Aún no hay ensayos registrados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Crea un nuevo ensayo de cilindro infiltrómetro o carga el ejemplo de campo para explorar todos los cálculos y gráficos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onLoadSample) {
                    Text("Cargar ejemplo")
                }
                Button(onClick = onCreate) {
                    Text("Nuevo ensayo")
                }
            }
        }
    }
}

@Composable
private fun CreateTestDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        plotName: String,
        diameterM: Double,
        outerDiameterM: Double,
        densityKgM3: Double,
        waterTempC: Double,
        initialLevelCm: Double,
        notes: String
    ) -> Unit
) {
    var plotName by remember { mutableStateOf("") }
    var diameterText by remember { mutableStateOf("0.30") }
    var outerDiameterText by remember { mutableStateOf("0.60") }
    var tempText by remember { mutableStateOf("20.0") }
    var densityText by remember { mutableStateOf("1000") }
    var initialLevelText by remember { mutableStateOf("15.0") }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedDiameter = NumberFormatters.parseNumberInput(diameterText)
    val previewArea = parsedDiameter?.let { InfiltrationCalculator.calculateAreaM2(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo ensayo de infiltración") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = plotName,
                    onValueChange = { plotName = it; errorMsg = null },
                    label = { Text("Nombre de la parcela / lote") },
                    placeholder = { Text("Ej. Lote Experimental 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_plot_name")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = diameterText,
                        onValueChange = { diameterText = it; errorMsg = null },
                        label = { Text("D interno (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_diameter")
                    )
                    OutlinedTextField(
                        value = outerDiameterText,
                        onValueChange = { outerDiameterText = it; errorMsg = null },
                        label = { Text("D externo (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = if (previewArea != null && previewArea > 0.0) {
                        "Área A = ${NumberFormatters.formatDecimal(previewArea, 5)} m²"
                    } else "Ingresa un diámetro interno válido (> 0 m)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = tempText,
                        onValueChange = { tempText = it; errorMsg = null },
                        label = { Text("Temp. T (°C)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = densityText,
                        onValueChange = { densityText = it; errorMsg = null },
                        label = { Text("ρ (kg/m³)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_density")
                    )
                }
                TextButton(
                    onClick = {
                        val t = NumberFormatters.parseNumberInput(tempText) ?: 20.0
                        val rhoT = InfiltrationCalculator.waterDensityAtTemp(t)
                        densityText = NumberFormatters.formatDecimal(rhoT, 2)
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("Calcular ρ(T) física exacta", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedTextField(
                    value = initialLevelText,
                    onValueChange = { initialLevelText = it; errorMsg = null },
                    label = { Text("Lectura inicial h₀ en t = 0 (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_initial_level")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas de campo (opcional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let { err ->
                    Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = NumberFormatters.parseNumberInput(diameterText)
                    val dExt = NumberFormatters.parseNumberInput(outerDiameterText) ?: ((d ?: 0.3) * 2.0)
                    val rho = NumberFormatters.parseNumberInput(densityText)
                    val tempC = NumberFormatters.parseNumberInput(tempText) ?: 20.0
                    val h0 = NumberFormatters.parseNumberInput(initialLevelText)
                    when {
                        d == null || d <= 0.0 -> errorMsg = "El diámetro D debe ser mayor que 0 m."
                        rho == null || rho <= 0.0 -> errorMsg = "La densidad ρ debe ser mayor que 0 kg/m³."
                        h0 == null || h0 < 0.0 -> errorMsg = "La lectura inicial h₀ debe ser válida (≥ 0 cm)."
                        else -> onConfirm(
                            plotName.ifBlank { "Parcela de campo" },
                            d,
                            dExt.coerceAtLeast(d),
                            rho,
                            tempC.coerceIn(0.0, 60.0),
                            h0,
                            notes
                        )
                    }
                },
                modifier = Modifier.testTag("confirm_create_test")
            ) {
                Text("Crear ensayo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
