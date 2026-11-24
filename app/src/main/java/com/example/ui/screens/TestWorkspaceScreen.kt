package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.InfiltrationRowEntity
import com.example.data.InfiltrationTestEntity
import com.example.domain.CalculatedRow
import com.example.domain.InfiltrationAnalysis
import com.example.domain.InfiltrationCalculator
import com.example.domain.NumberFormatters
import com.example.domain.PhysicalStage
import com.example.export.ExportFormat
import com.example.ui.components.CumulativeDepthChartCard
import com.example.ui.components.FieldTimerCard
import com.example.ui.components.Infiltrometer3DView
import com.example.ui.components.InstantRateChartCard
import com.example.ui.components.PartialDepthBarChartCard
import com.example.ui.theme.MonoTextStyles
import com.example.ui.theme.StageCapillaryColor
import com.example.ui.theme.StageDarcyColor
import com.example.ui.theme.StageTransitionColor
import com.example.ui.theme.StageWarningColor
import com.example.ui.viewmodel.TestWorkspaceState
import com.example.ui.viewmodel.WorkspaceUiState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestWorkspaceScreen(
    uiState: WorkspaceUiState,
    uiMessages: SharedFlow<String>,
    onBack: () -> Unit,
    onUpdateParams: (InfiltrationTestEntity, String, Double, Double, Double, Double, String) -> Unit,
    onCaptureGps: (InfiltrationTestEntity) -> Unit,
    onAttachPhoto: (InfiltrationTestEntity, Uri) -> Unit,
    onRemovePhoto: (InfiltrationTestEntity) -> Unit,
    onAddRow: (Long, Double, Double, Double) -> Unit,
    onUpdateRow: (InfiltrationRowEntity, Double, Double, Double) -> Unit,
    onDeleteRow: (InfiltrationRowEntity) -> Unit,
    onExport: (ExportFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showEditParamsDialog by remember { mutableStateOf(false) }
    var prefilledDeltaTForAdd by remember { mutableStateOf<Double?>(null) }
    var showAddRowDialog by remember { mutableStateOf(false) }
    var rowBeingEdited by remember { mutableStateOf<Pair<CalculatedRow, InfiltrationRowEntity>?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        uiMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val readyState = (uiState as? WorkspaceUiState.Ready)?.workspace

    // Photo Picker (Zero storage permissions needed)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && readyState != null) {
            onAttachPhoto(readyState.test, uri)
        }
    }

    // Runtime Location Permission Flow
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && readyState != null) {
            onCaptureGps(readyState.test)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Permiso de ubicación denegado. Puedes continuar usando la app sin GPS.")
            }
        }
    }

    fun requestGpsCapture(test: InfiltrationTestEntity) {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            onCaptureGps(test)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("btn_back_home")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a ensayos"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = readyState?.test?.plotName ?: "Ensayo",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (readyState != null) {
                            Text(
                                text = "D = ${NumberFormatters.formatDecimal(readyState.analysis.diameterM, 2)} m · T = ${NumberFormatters.formatDecimal(readyState.analysis.waterTempC, 1)} °C",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (readyState != null) {
                        IconButton(
                            onClick = { showEditParamsDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_edit_params")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar parámetros del ensayo")
                        }
                        Box {
                            IconButton(
                                onClick = { showExportMenu = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_export_menu")
                            ) {
                                Icon(Icons.Filled.IosShare, contentDescription = "Exportar o compartir informe")
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                ExportFormat.entries.forEach { fmt ->
                                    DropdownMenuItem(
                                        text = { Text(fmt.label) },
                                        onClick = {
                                            showExportMenu = false
                                            onExport(fmt)
                                        },
                                        modifier = Modifier.testTag("export_${fmt.extension}")
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (readyState != null && selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        prefilledDeltaTForAdd = null
                        showAddRowDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_interval")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar intervalo de campo")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Campo", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("tab_field_table")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Modelos", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("tab_models_charts")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Vista 3D", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("tab_3d_view")
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Fórmulas", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("tab_formulas")
                )
            }

            when (uiState) {
                is WorkspaceUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is WorkspaceUiState.NotFound -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No se encontró el ensayo solicitado.")
                    }
                }
                is WorkspaceUiState.Ready -> {
                    when (selectedTab) {
                        0 -> FieldTableTab(
                            workspace = uiState.workspace,
                            onEditParams = { showEditParamsDialog = true },
                            onRequestGps = { requestGpsCapture(uiState.workspace.test) },
                            onPickPhoto = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRemovePhoto = { onRemovePhoto(uiState.workspace.test) },
                            onCaptureWithElapsed = { elapsedMin ->
                                prefilledDeltaTForAdd = elapsedMin
                                showAddRowDialog = true
                            },
                            onAddInterval = {
                                prefilledDeltaTForAdd = null
                                showAddRowDialog = true
                            },
                            onEditRow = { calcRow ->
                                val raw = uiState.workspace.rawRows.firstOrNull { it.id == calcRow.id }
                                if (raw != null) rowBeingEdited = calcRow to raw
                            }
                        )
                        1 -> ModelsAndChartsTab(analysis = uiState.workspace.analysis)
                        2 -> Infiltrometer3DView(
                            plotName = uiState.workspace.test.plotName,
                            analysis = uiState.workspace.analysis
                        )
                        3 -> FormulasScreen(contentPadding = PaddingValues(16.dp))
                    }
                }
            }
        }
    }

    if (showEditParamsDialog && readyState != null) {
        EditParametersDialog(
            test = readyState.test,
            onDismiss = { showEditParamsDialog = false },
            onConfirm = { name, diam, outerDiam, dens, tempC, notes ->
                showEditParamsDialog = false
                onUpdateParams(readyState.test, name, diam, outerDiam, dens, tempC, notes)
            }
        )
    }

    if (showAddRowDialog && readyState != null) {
        val lastRow = readyState.analysis.rows.lastOrNull()
        val defaultDt = prefilledDeltaTForAdd ?: (lastRow?.deltaTMin?.takeIf { it > 0.0 } ?: 5.0)
        IntervalEditorDialog(
            title = "Agregar intervalo #${readyState.analysis.rows.size}",
            isInitialRow = false,
            prevWaterLevelCm = lastRow?.waterLevelCm ?: 15.0,
            initialDeltaT = NumberFormatters.formatDecimal(defaultDt, 2),
            initialRefill = "0",
            initialLevel = NumberFormatters.formatDecimal((lastRow?.waterLevelCm ?: 15.0) - 0.5, 2),
            onDismiss = { showAddRowDialog = false },
            onDelete = null,
            onConfirm = { dt, refill, h ->
                showAddRowDialog = false
                onAddRow(readyState.test.id, dt, refill, h)
            }
        )
    }

    rowBeingEdited?.let { (calcRow, rawRow) ->
        val prevLevel = if (calcRow.orderIndex == 0) {
            rawRow.waterLevelCm
        } else {
            readyState?.analysis?.rows?.getOrNull(calcRow.orderIndex - 1)?.waterLevelCm ?: rawRow.waterLevelCm
        }
        IntervalEditorDialog(
            title = if (calcRow.isInitialRow) "Editar lectura inicial (t = 0)" else "Editar intervalo #${calcRow.orderIndex}",
            isInitialRow = calcRow.isInitialRow,
            prevWaterLevelCm = prevLevel,
            initialDeltaT = NumberFormatters.formatDecimal(rawRow.deltaTMin, 2),
            initialRefill = NumberFormatters.formatDecimal(rawRow.refillCm, 2),
            initialLevel = NumberFormatters.formatDecimal(rawRow.waterLevelCm, 2),
            onDismiss = { rowBeingEdited = null },
            onDelete = if (calcRow.isInitialRow) null else {
                {
                    rowBeingEdited = null
                    onDeleteRow(rawRow)
                }
            },
            onConfirm = { dt, refill, h ->
                rowBeingEdited = null
                onUpdateRow(rawRow, dt, refill, h)
            }
        )
    }
}

@Composable
private fun FieldTableTab(
    workspace: TestWorkspaceState,
    onEditParams: () -> Unit,
    onRequestGps: () -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onCaptureWithElapsed: (Double) -> Unit,
    onAddInterval: () -> Unit,
    onEditRow: (CalculatedRow) -> Unit
) {
    val analysis = workspace.analysis
    val lastLevel = analysis.rows.lastOrNull()?.waterLevelCm

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 980.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Field Timer + Capture Card
            item {
                FieldTimerCard(
                    lastWaterLevelCm = lastLevel,
                    isStabilized = analysis.isStabilized,
                    stabilizationMessage = analysis.stabilizationMessage,
                    onCaptureReading = onCaptureWithElapsed
                )
            }

            // Parameters, GPS & Photo Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Parámetros, GPS y sitio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onEditParams) {
                                Text("Editar")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ParamBox(
                                label = "D int / ext",
                                value = "${NumberFormatters.formatDecimal(analysis.diameterM, 2)} / ${NumberFormatters.formatDecimal(analysis.outerDiameterM, 2)} m",
                                modifier = Modifier.weight(1.1f)
                            )
                            ParamBox(
                                label = "Área A",
                                value = "${NumberFormatters.formatDecimal(analysis.areaM2, 5)} m²",
                                sub = "${NumberFormatters.formatDecimal(analysis.areaCm2, 1)} cm²",
                                modifier = Modifier.weight(1.1f)
                            )
                            ParamBox(
                                label = "ρ | T agua",
                                value = "${NumberFormatters.formatDecimal(analysis.waterDensityKgM3, 0)} kg/m³",
                                sub = "${NumberFormatters.formatDecimal(analysis.waterTempC, 1)} °C",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // GPS & Photo Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onRequestGps,
                                modifier = Modifier.weight(1f).testTag("btn_capture_gps")
                            ) {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (workspace.test.latitude != null && workspace.test.longitude != null) {
                                        "${NumberFormatters.formatDecimal(workspace.test.latitude, 4)}, ${NumberFormatters.formatDecimal(workspace.test.longitude, 4)}"
                                    } else "Capturar GPS",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            OutlinedButton(
                                onClick = onPickPhoto,
                                modifier = Modifier.weight(1f).testTag("btn_pick_photo")
                            ) {
                                Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (workspace.test.photoPath != null) "Cambiar foto" else "Foto del sitio",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        // Attached site photo preview
                        workspace.test.photoPath?.let { path ->
                            val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
                            if (bmp != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Fotografía del sitio del ensayo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .clickable(onClick = onRemovePhoto)
                                    ) {
                                        Text(
                                            text = "Quitar foto",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (workspace.test.notes.isNotBlank()) {
                            Text(
                                text = workspace.test.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "El descenso de h mide la infiltración vertical al suelo (no es fuga). En t = 0 las tasas i, q y Q quedan en '—'. Toca cualquier fila para editarla con recálculo en cascada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (analysis.warnings.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = "Advertencia de validación",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                analysis.warnings.forEach { warn ->
                                    Text(
                                        text = warn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                InfiltrationSpreadsheetTable(
                    analysis = analysis,
                    onRowClick = onEditRow
                )
            }

            item {
                OutlinedButton(
                    onClick = onAddInterval,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_add_interval_inline")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar lectura de campo")
                }
            }
        }
    }
}

@Composable
private fun ParamBox(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MonoTextStyles.tableCellBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Text(text = sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun InfiltrationSpreadsheetTable(
    analysis: InfiltrationAnalysis,
    onRowClick: (CalculatedRow) -> Unit
) {
    val hScroll = rememberScrollState()

    val wIdx = 36.dp
    val wDt = 68.dp
    val wDts = 72.dp
    val wT = 70.dp
    val wRefill = 76.dp
    val wLevel = 72.dp
    val wDi = 74.dp
    val wCumI = 78.dp
    val wRate = 84.dp
    val wFlux = 96.dp
    val wFlowCm = 88.dp
    val wFlowAlt = 110.dp
    val wMass = 78.dp
    val wStage = 148.dp

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("infiltration_data_table")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tabla de registro y cálculos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Desliza →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.horizontalScroll(hScroll)) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderCell("#", wIdx)
                    HeaderCell("Δt\n(min)", wDt)
                    HeaderCell("Δt_s\n(s)", wDts)
                    HeaderCell("t acum\n(min)", wT)
                    HeaderCell("Relleno\nΔhᵣ (cm)", wRefill)
                    HeaderCell("Nivel h\n(cm)", wLevel)
                    HeaderCell("ΔI\n(mm)", wDi)
                    HeaderCell("I(t)\n(mm)", wCumI)
                    HeaderCell("i(t)\n(mm/h)", wRate)
                    HeaderCell("q\n(m/s)", wFlux)
                    HeaderCell("Q\n(cm³/s)", wFlowCm)
                    HeaderCell("Q\n(m³/s | L/s)", wFlowAlt)
                    HeaderCell("Δm\n(kg)", wMass)
                    HeaderCell("Etapa física", wStage, TextAlign.Start)
                }

                analysis.rows.forEachIndexed { idx, row ->
                    val bg = when {
                        row.stage == PhysicalStage.INVALID_INTERVAL -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        idx % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    Row(
                        modifier = Modifier
                            .background(bg)
                            .clickable { onRowClick(row) }
                            .padding(vertical = 9.dp, horizontal = 8.dp)
                            .testTag("table_row_${row.orderIndex}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataCell(row.orderIndex.toString(), wIdx, bold = true)
                        DataCell(NumberFormatters.formatDecimal(row.deltaTMin, 1), wDt)
                        DataCell(NumberFormatters.formatDecimal(row.deltaTSec, 0), wDts)
                        DataCell(NumberFormatters.formatDecimal(row.cumulativeTimeMin, 1), wT, bold = true)
                        DataCell(NumberFormatters.formatDecimal(row.refillCm, 2), wRefill)
                        DataCell(NumberFormatters.formatDecimal(row.waterLevelCm, 2), wLevel)
                        DataCell(NumberFormatters.formatDecimal(row.deltaIMm, 2), wDi)
                        DataCell(NumberFormatters.formatDecimal(row.cumulativeIMm, 2), wCumI, bold = true)
                        DataCell(
                            if (row.isInitialRow) "—" else NumberFormatters.formatDecimal(row.instantRateMmH, 2),
                            wRate,
                            bold = true
                        )
                        DataCell(
                            if (row.isInitialRow) "—" else NumberFormatters.formatScientific(row.fluxDensityMS, 2),
                            wFlux
                        )
                        DataCell(
                            if (row.isInitialRow) "—" else NumberFormatters.formatDecimal(row.flowRateCm3S, 3),
                            wFlowCm
                        )
                        DataCell(
                            if (row.isInitialRow || row.flowRateM3S == null) "—"
                            else "${NumberFormatters.formatScientific(row.flowRateM3S, 1)}\n${NumberFormatters.formatDecimal(row.flowRateLS, 4)} L/s",
                            wFlowAlt
                        )
                        DataCell(NumberFormatters.formatDecimal(row.massKg, 3), wMass)
                        StageBadgeCell(row.stage, wStage)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }

                val tot = analysis.totals
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                        .padding(vertical = 11.dp, horizontal = 8.dp)
                        .testTag("table_totals_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DataCell("Σ/x̄", wIdx, bold = true)
                    DataCell("—", wDt, bold = true)
                    DataCell(NumberFormatters.formatDecimal(tot.totalDeltaTSec, 0), wDts, bold = true)
                    DataCell(NumberFormatters.formatDecimal(tot.totalTimeMin, 1), wT, bold = true)
                    DataCell(NumberFormatters.formatDecimal(tot.totalRefillCm, 2), wRefill, bold = true)
                    DataCell("—", wLevel, bold = true)
                    DataCell(NumberFormatters.formatDecimal(tot.totalDeltaIMm, 2), wDi, bold = true)
                    DataCell(NumberFormatters.formatDecimal(tot.totalCumulativeIMm, 2), wCumI, bold = true)
                    DataCell(NumberFormatters.formatDecimal(tot.meanRateMmH, 2), wRate, bold = true)
                    DataCell(NumberFormatters.formatScientific(tot.meanFluxMS, 2), wFlux, bold = true)
                    DataCell(NumberFormatters.formatDecimal(tot.meanFlowCm3S, 3), wFlowCm, bold = true)
                    DataCell(
                        if (tot.meanFlowM3S == null) "—"
                        else "${NumberFormatters.formatScientific(tot.meanFlowM3S, 1)}\n${NumberFormatters.formatDecimal(tot.meanFlowLS, 4)} L/s",
                        wFlowAlt,
                        bold = true
                    )
                    DataCell(NumberFormatters.formatDecimal(tot.totalMassKg, 3), wMass, bold = true)
                    Box(modifier = Modifier.width(wStage).padding(horizontal = 4.dp)) {
                        Text(
                            text = "TOTAL / MEDIA\n(${tot.activeIntervalCount} act.)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign = TextAlign.End) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = align,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp)
    )
}

@Composable
private fun DataCell(text: String, width: Dp, bold: Boolean = false) {
    Text(
        text = text,
        style = if (bold) MonoTextStyles.tableCellBold else MonoTextStyles.tableCell,
        textAlign = TextAlign.End,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp)
    )
}

@Composable
private fun StageBadgeCell(stage: PhysicalStage, width: Dp) {
    val badgeColor = when (stage) {
        PhysicalStage.INITIAL_BASELINE -> MaterialTheme.colorScheme.outline
        PhysicalStage.CAPILLARY_SUCTION -> StageCapillaryColor
        PhysicalStage.TRANSITION -> StageTransitionColor
        PhysicalStage.DARCY_STEADY -> StageDarcyColor
        PhysicalStage.INVALID_INTERVAL -> StageWarningColor
    }
    Box(modifier = Modifier.width(width).padding(horizontal = 4.dp)) {
        Surface(
            color = badgeColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = stage.label,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ModelsAndChartsTab(analysis: InfiltrationAnalysis) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Darcy & Basic Infiltration + 20°C Temperature Correction
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ModelKpiCard(
                        title = "Infiltración básica i_b",
                        value = "${NumberFormatters.formatDecimal(analysis.basicInfiltrationMmH, 2)} mm/h",
                        subtitle = if (analysis.steadyIntervalCount > 0) "Media últimos ${analysis.steadyIntervalCount} interv." else "Sin datos",
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f).testTag("kpi_basic_infiltration")
                    )
                    ModelKpiCard(
                        title = "K_sat (T = ${NumberFormatters.formatDecimal(analysis.waterTempC, 0)} °C)",
                        value = "${NumberFormatters.formatScientific(analysis.kSatMS, 2)} m/s",
                        subtitle = "20°C: ${NumberFormatters.formatScientific(analysis.kSat20MS, 2)} m/s",
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).testTag("kpi_ksat")
                    )
                }
            }

            // Model R² Comparison Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().testTag("card_model_comparison")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Comparativa de modelos (R²)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            analysis.modelComparison.bestModelName?.let { bestName ->
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                        Text(
                                            text = "Mejor: $bestName",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        analysis.modelComparison.entries.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = entry.name, style = MaterialTheme.typography.labelLarge)
                                    Text(text = entry.equationSummary, style = MonoTextStyles.tableCell, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = if (entry.isValid) "R² = ${NumberFormatters.formatDecimal(entry.rSquared, 4)}" else "—",
                                    style = MonoTextStyles.tableCellBold,
                                    color = if (entry.name == analysis.modelComparison.bestModelName) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Kostiakov Simple Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().testTag("card_kostiakov")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kostiakov simple",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "i(t) = k · tⁿ",
                                    style = MonoTextStyles.tableCellBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (analysis.kostiakov.isValid) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ParamBox("Coef. k", NumberFormatters.formatDecimal(analysis.kostiakov.k, 3), modifier = Modifier.weight(1f))
                                ParamBox("Exp. n", NumberFormatters.formatDecimal(analysis.kostiakov.n, 4), modifier = Modifier.weight(1f))
                                ParamBox("R²", NumberFormatters.formatDecimal(analysis.kostiakov.rSquared, 4), modifier = Modifier.weight(1f))
                                ParamBox("Reducción", "${NumberFormatters.formatDecimal(analysis.reductionPercent, 1)}%", modifier = Modifier.weight(1f))
                            }
                        }
                        Text(text = analysis.kostiakov.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Philip & Modified Kostiakov Cards
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).testTag("card_philip")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Modelo de Philip", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("I(t) = S·t¹/² + A_p·t", style = MonoTextStyles.tableCell, color = MaterialTheme.colorScheme.primary)
                            if (analysis.philip.isValid) {
                                Text("S = ${NumberFormatters.formatDecimal(analysis.philip.sorptivityS, 2)} mm/min¹/²", style = MonoTextStyles.tableCellBold)
                                Text("A_p = ${NumberFormatters.formatDecimal(analysis.philip.transmissivityMmH, 1)} mm/h", style = MonoTextStyles.tableCell)
                                Text("R² = ${NumberFormatters.formatDecimal(analysis.philip.rSquared, 4)}", style = MonoTextStyles.tableCellBold, color = MaterialTheme.colorScheme.tertiary)
                            } else {
                                Text(analysis.philip.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).testTag("card_kostiakov_lewis")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Kostiakov-Lewis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("i(t) = k_m·tⁿᵐ + i_b", style = MonoTextStyles.tableCell, color = MaterialTheme.colorScheme.secondary)
                            if (analysis.modifiedKostiakov.isValid) {
                                Text("k_m = ${NumberFormatters.formatDecimal(analysis.modifiedKostiakov.k, 2)}", style = MonoTextStyles.tableCellBold)
                                Text("n_m = ${NumberFormatters.formatDecimal(analysis.modifiedKostiakov.n, 3)}", style = MonoTextStyles.tableCell)
                                Text("R² = ${NumberFormatters.formatDecimal(analysis.modifiedKostiakov.rSquared, 4)}", style = MonoTextStyles.tableCellBold, color = MaterialTheme.colorScheme.tertiary)
                            } else {
                                Text(analysis.modifiedKostiakov.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Automatic Hydraulic Soil Diagnosis
            analysis.diagnosis?.let { diag ->
                item {
                    val diagBg = when (diag.severityLevel) {
                        0 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        1 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = diagBg),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().testTag("card_soil_diagnosis")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Diagnóstico hidráulico automático",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Permeabilidad: ${diag.permeabilityClass}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = "Textura probable: ${diag.soilTextureHint}", style = MaterialTheme.typography.bodySmall)
                            Text(text = diag.kostiakovInterpretation, style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            Text(text = diag.managementAdvice, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            item {
                InstantRateChartCard(
                    rows = analysis.rows,
                    kostiakov = analysis.kostiakov,
                    basicInfiltrationMmH = analysis.basicInfiltrationMmH
                )
            }
            item { CumulativeDepthChartCard(rows = analysis.rows) }
            item { PartialDepthBarChartCard(rows = analysis.rows) }
        }
    }
}

@Composable
private fun ModelKpiCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MonoTextStyles.metricMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditParametersDialog(
    test: InfiltrationTestEntity,
    onDismiss: () -> Unit,
    onConfirm: (plotName: String, diameterM: Double, outerDiameterM: Double, densityKgM3: Double, waterTempC: Double, notes: String) -> Unit
) {
    var plotName by remember { mutableStateOf(test.plotName) }
    var diameterText by remember { mutableStateOf(NumberFormatters.formatDecimal(test.diameterM, 3)) }
    var outerDiameterText by remember { mutableStateOf(NumberFormatters.formatDecimal(test.outerDiameterM, 3)) }
    var tempText by remember { mutableStateOf(NumberFormatters.formatDecimal(test.waterTempC, 1)) }
    var densityText by remember { mutableStateOf(NumberFormatters.formatDecimal(test.waterDensityKgM3, 2)) }
    var notes by remember { mutableStateOf(test.notes) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedD = NumberFormatters.parseNumberInput(diameterText)
    val previewArea = parsedD?.let { InfiltrationCalculator.calculateAreaM2(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar parámetros del ensayo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = plotName,
                    onValueChange = { plotName = it; errorMsg = null },
                    label = { Text("Parcela / Lote") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = diameterText,
                        onValueChange = { diameterText = it; errorMsg = null },
                        label = { Text("D interno (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
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
                    } else "Ingresa D > 0 m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        modifier = Modifier.weight(1f)
                    )
                }
                TextButton(
                    onClick = {
                        val t = NumberFormatters.parseNumberInput(tempText) ?: 20.0
                        densityText = NumberFormatters.formatDecimal(InfiltrationCalculator.waterDensityAtTemp(t), 2)
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("Calcular densidad física ρ(T)", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                    when {
                        plotName.isBlank() -> errorMsg = "Ingresa el nombre de la parcela."
                        d == null || d <= 0.0 -> errorMsg = "El diámetro D debe ser mayor que 0."
                        rho == null || rho <= 0.0 -> errorMsg = "La densidad ρ debe ser mayor que 0."
                        else -> onConfirm(plotName, d, dExt.coerceAtLeast(d), rho, tempC.coerceIn(0.0, 60.0), notes)
                    }
                },
                modifier = Modifier.testTag("confirm_edit_params")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun IntervalEditorDialog(
    title: String,
    isInitialRow: Boolean,
    prevWaterLevelCm: Double,
    initialDeltaT: String,
    initialRefill: String,
    initialLevel: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onConfirm: (deltaTMin: Double, refillCm: Double, waterLevelCm: Double) -> Unit
) {
    var deltaTText by remember { mutableStateOf(initialDeltaT) }
    var refillText by remember { mutableStateOf(initialRefill) }
    var levelText by remember { mutableStateOf(initialLevel) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedDt = NumberFormatters.parseNumberInput(deltaTText)
    val parsedRefill = NumberFormatters.parseNumberInput(refillText) ?: 0.0
    val parsedLevel = NumberFormatters.parseNumberInput(levelText)

    val liveDeltaI = if (!isInitialRow && parsedLevel != null) {
        ((prevWaterLevelCm + parsedRefill) - parsedLevel) * 10.0
    } else null
    val liveRate = if (liveDeltaI != null && parsedDt != null && parsedDt > 0.0) {
        (liveDeltaI / parsedDt) * 60.0
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isInitialRow) {
                    Text(
                        text = "En t = 0 min, Δt y relleno permanecen en 0 y las tasas quedan en '—' para evitar división entre cero.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Nivel anterior hᵢ₋₁ = ${NumberFormatters.formatDecimal(prevWaterLevelCm, 2)} cm",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = deltaTText,
                        onValueChange = { deltaTText = it; errorMsg = null },
                        label = { Text("Intervalo Δt (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_row_deltat")
                    )
                    OutlinedTextField(
                        value = refillText,
                        onValueChange = { refillText = it; errorMsg = null },
                        label = { Text("Lámina de relleno Δhᵣ (cm)") },
                        supportingText = { Text("0 si no se recargó agua en este intervalo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_row_refill")
                    )
                }

                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it; errorMsg = null },
                    label = { Text("Lectura de nivel h (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_row_level")
                )

                if (!isInitialRow && liveDeltaI != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Vista previa: ΔI = ${NumberFormatters.formatDecimal(liveDeltaI, 2)} mm" +
                                    if (liveRate != null) " | i(t) = ${NumberFormatters.formatDecimal(liveRate, 1)} mm/h" else "",
                                style = MonoTextStyles.tableCellBold
                            )
                        }
                    }
                }

                errorMsg?.let { err ->
                    Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("btn_delete_row")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar fila", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    onClick = {
                        val h = NumberFormatters.parseNumberInput(levelText)
                        if (isInitialRow) {
                            if (h == null || h < 0.0) {
                                errorMsg = "Ingresa una lectura inicial h válida (≥ 0 cm)."
                            } else {
                                onConfirm(0.0, 0.0, h)
                            }
                        } else {
                            val dt = NumberFormatters.parseNumberInput(deltaTText)
                            val rf = NumberFormatters.parseNumberInput(refillText) ?: 0.0
                            when {
                                dt == null || dt <= 0.0 -> errorMsg = "El intervalo Δt debe ser mayor que 0 min."
                                rf < 0.0 -> errorMsg = "La lámina de relleno Δhᵣ no puede ser negativa."
                                h == null || h < 0.0 -> errorMsg = "La lectura h debe ser válida (≥ 0 cm)."
                                else -> onConfirm(dt, rf, h)
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_save_row")
                ) {
                    Text("Guardar")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
