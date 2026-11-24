package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.InfiltrationDatabase
import com.example.data.InfiltrationRepository
import com.example.data.InfiltrationRowEntity
import com.example.data.InfiltrationTestEntity
import com.example.data.TestWithRows
import com.example.domain.InfiltrationAnalysis
import com.example.domain.InfiltrationCalculator
import com.example.domain.RawIntervalInput
import com.example.export.ExportFormat
import com.example.export.ReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class TestSummaryItem(
    val test: InfiltrationTestEntity,
    val rowCount: Int,
    val analysis: InfiltrationAnalysis
)

data class TestWorkspaceState(
    val test: InfiltrationTestEntity,
    val rawRows: List<InfiltrationRowEntity>,
    val analysis: InfiltrationAnalysis
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val tests: List<TestSummaryItem>) : HomeUiState
}

sealed interface WorkspaceUiState {
    data object Loading : WorkspaceUiState
    data object NotFound : WorkspaceUiState
    data class Ready(val workspace: TestWorkspaceState) : WorkspaceUiState
}

class InfiltrationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: InfiltrationRepository by lazy {
        val dao = InfiltrationDatabase.getInstance(application).infiltrationDao()
        InfiltrationRepository(dao)
    }

    private val selectedTestId = MutableStateFlow<Long?>(null)

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiMessages = _uiMessages.asSharedFlow()

    val homeUiState: StateFlow<HomeUiState> = repository.allTestsWithRows
        .map { list ->
            val items = list.map { twr ->
                val analysis = computeForTest(twr)
                TestSummaryItem(
                    test = twr.test,
                    rowCount = twr.rows.size,
                    analysis = analysis
                )
            }
            HomeUiState.Ready(items)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceUiState: StateFlow<WorkspaceUiState> = selectedTestId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(WorkspaceUiState.Loading)
            } else {
                repository.observeTestWithRows(id).map { twr ->
                    if (twr == null) {
                        WorkspaceUiState.NotFound
                    } else {
                        val sorted = twr.rows.sortedBy { it.orderIndex }
                        val analysis = computeForTest(twr)
                        WorkspaceUiState.Ready(
                            TestWorkspaceState(
                                test = twr.test,
                                rawRows = sorted,
                                analysis = analysis
                            )
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WorkspaceUiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.seedInitialSampleIfEmpty()
        }
    }

    fun selectTest(testId: Long) {
        selectedTestId.value = testId
    }

    fun createNewTest(
        plotName: String,
        diameterM: Double,
        outerDiameterM: Double,
        waterDensityKgM3: Double,
        waterTempC: Double,
        initialWaterLevelCm: Double,
        notes: String,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.createNewTest(
                plotName = plotName,
                diameterM = diameterM,
                outerDiameterM = outerDiameterM,
                waterDensityKgM3 = waterDensityKgM3,
                waterTempC = waterTempC,
                initialWaterLevelCm = initialWaterLevelCm,
                notes = notes
            )
            selectedTestId.value = id
            onCreated(id)
        }
    }

    fun loadSampleTest(onCreated: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = repository.createSampleTest()
            _uiMessages.tryEmit("Ensayo de ejemplo cargado.")
            onCreated?.invoke(id)
        }
    }

    fun updateTestParameters(
        test: InfiltrationTestEntity,
        plotName: String,
        diameterM: Double,
        outerDiameterM: Double,
        waterDensityKgM3: Double,
        waterTempC: Double,
        notes: String
    ) {
        viewModelScope.launch {
            repository.updateTestParameters(
                test = test,
                plotName = plotName,
                diameterM = diameterM,
                outerDiameterM = outerDiameterM,
                waterDensityKgM3 = waterDensityKgM3,
                waterTempC = waterTempC,
                notes = notes
            )
            _uiMessages.tryEmit("Parámetros actualizados y tabla recalculada.")
        }
    }

    @SuppressLint("MissingPermission")
    fun captureGpsLocation(test: InfiltrationTestEntity) {
        val ctx = getApplication<Application>()
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            _uiMessages.tryEmit("Servicio de ubicación no disponible en este dispositivo.")
            return
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val lastKnown = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        if (lastKnown != null) {
            viewModelScope.launch {
                repository.updateTestLocation(test, lastKnown.latitude, lastKnown.longitude)
                _uiMessages.tryEmit("Ubicación GPS guardada.")
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && providers.isNotEmpty()) {
            val provider = providers.first()
            runCatching {
                lm.getCurrentLocation(provider, CancellationSignal(), ctx.mainExecutor) { loc: Location? ->
                    if (loc != null) {
                        viewModelScope.launch {
                            repository.updateTestLocation(test, loc.latitude, loc.longitude)
                            _uiMessages.tryEmit("Ubicación GPS capturada.")
                        }
                    } else {
                        _uiMessages.tryEmit("No se obtuvo señal GPS. Activa la ubicación del dispositivo.")
                    }
                }
            }.onFailure {
                _uiMessages.tryEmit("No fue posible consultar el GPS.")
            }
        } else {
            _uiMessages.tryEmit("Sin lectura GPS reciente. Activa el GPS a cielo abierto e intenta de nuevo.")
        }
    }

    fun attachSitePhoto(test: InfiltrationTestEntity, uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<Application>()
                    val dir = File(ctx.filesDir, "site_photos").apply { mkdirs() }
                    val dest = File(dir, "site_${test.id}_${System.currentTimeMillis()}.jpg")
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@runCatching null
                    dest.absolutePath
                }.getOrNull()
            }
            if (savedPath != null) {
                repository.updateTestPhoto(test, savedPath)
                _uiMessages.tryEmit("Foto del sitio guardada sin conexión.")
            } else {
                _uiMessages.tryEmit("No se pudo copiar la fotografía seleccionada.")
            }
        }
    }

    fun removeSitePhoto(test: InfiltrationTestEntity) {
        viewModelScope.launch {
            test.photoPath?.let { path ->
                withContext(Dispatchers.IO) { runCatching { File(path).delete() } }
            }
            repository.updateTestPhoto(test, null)
            _uiMessages.tryEmit("Fotografía eliminada del ensayo.")
        }
    }

    fun deleteTest(testId: Long) {
        viewModelScope.launch {
            repository.deleteTest(testId)
            if (selectedTestId.value == testId) {
                selectedTestId.value = null
            }
            _uiMessages.tryEmit("Ensayo eliminado.")
        }
    }

    fun addIntervalRow(
        testId: Long,
        deltaTMin: Double,
        refillCm: Double,
        waterLevelCm: Double
    ) {
        viewModelScope.launch {
            repository.addIntervalRow(testId, deltaTMin, refillCm, waterLevelCm)
        }
    }

    fun updateIntervalRow(
        row: InfiltrationRowEntity,
        deltaTMin: Double,
        refillCm: Double,
        waterLevelCm: Double
    ) {
        viewModelScope.launch {
            repository.updateIntervalRow(row, deltaTMin, refillCm, waterLevelCm)
        }
    }

    fun deleteIntervalRow(row: InfiltrationRowEntity) {
        viewModelScope.launch {
            repository.deleteIntervalRow(row)
        }
    }

    fun exportTest(format: ExportFormat) {
        val current = workspaceUiState.value
        if (current !is WorkspaceUiState.Ready) return
        val result = ReportExporter.exportAndShare(
            context = getApplication(),
            test = current.workspace.test,
            analysis = current.workspace.analysis,
            format = format
        )
        result.onFailure { err ->
            _uiMessages.tryEmit("Error al exportar ${format.extension}: ${err.message ?: "desconocido"}")
        }
    }

    private fun computeForTest(twr: TestWithRows): InfiltrationAnalysis {
        val rawInputs = twr.rows
            .sortedBy { it.orderIndex }
            .map {
                RawIntervalInput(
                    id = it.id,
                    orderIndex = it.orderIndex,
                    deltaTMin = it.deltaTMin,
                    refillCm = it.refillCm,
                    waterLevelCm = it.waterLevelCm
                )
            }
        return InfiltrationCalculator.computeAnalysis(
            diameterM = twr.test.diameterM,
            waterDensityKgM3 = twr.test.waterDensityKgM3,
            rawRows = rawInputs,
            outerDiameterM = twr.test.outerDiameterM,
            waterTempC = twr.test.waterTempC
        )
    }
}
