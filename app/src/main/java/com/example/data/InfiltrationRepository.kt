package com.example.data

import kotlinx.coroutines.flow.Flow

class InfiltrationRepository(private val dao: InfiltrationDao) {

    val allTestsWithRows: Flow<List<TestWithRows>> = dao.getAllTestsWithRows()

    fun observeTestWithRows(testId: Long): Flow<TestWithRows?> = dao.getTestWithRows(testId)

    suspend fun seedInitialSampleIfEmpty() {
        if (dao.countTestsSync() == 0) {
            createSampleTest()
        }
    }

    suspend fun createSampleTest(): Long {
        val now = System.currentTimeMillis()
        val testId = dao.insertTest(
            InfiltrationTestEntity(
                plotName = "Lote Experimental 1",
                diameterM = 0.30,
                outerDiameterM = 0.60,
                waterDensityKgM3 = 1000.0,
                waterTempC = 20.0,
                notes = "Ensayo demostrativo de doble anillo en suelo franco. Incluye recarga de lámina en campo.",
                createdAt = now,
                updatedAt = now
            )
        )
        val sampleRows = listOf(
            InfiltrationRowEntity(testId = testId, orderIndex = 0, deltaTMin = 0.0, refillCm = 0.0, waterLevelCm = 15.0),
            InfiltrationRowEntity(testId = testId, orderIndex = 1, deltaTMin = 2.0, refillCm = 0.0, waterLevelCm = 14.3),
            InfiltrationRowEntity(testId = testId, orderIndex = 2, deltaTMin = 3.0, refillCm = 0.0, waterLevelCm = 13.6),
            InfiltrationRowEntity(testId = testId, orderIndex = 3, deltaTMin = 5.0, refillCm = 0.0, waterLevelCm = 12.8),
            InfiltrationRowEntity(testId = testId, orderIndex = 4, deltaTMin = 5.0, refillCm = 0.0, waterLevelCm = 12.2),
            InfiltrationRowEntity(testId = testId, orderIndex = 5, deltaTMin = 10.0, refillCm = 3.5, waterLevelCm = 14.7),
            InfiltrationRowEntity(testId = testId, orderIndex = 6, deltaTMin = 10.0, refillCm = 0.0, waterLevelCm = 13.9),
            InfiltrationRowEntity(testId = testId, orderIndex = 7, deltaTMin = 15.0, refillCm = 0.0, waterLevelCm = 12.9),
            InfiltrationRowEntity(testId = testId, orderIndex = 8, deltaTMin = 15.0, refillCm = 0.0, waterLevelCm = 12.0),
            InfiltrationRowEntity(testId = testId, orderIndex = 9, deltaTMin = 15.0, refillCm = 0.0, waterLevelCm = 11.1)
        )
        dao.insertRows(sampleRows)
        return testId
    }

    suspend fun createNewTest(
        plotName: String,
        diameterM: Double,
        outerDiameterM: Double,
        waterDensityKgM3: Double,
        waterTempC: Double,
        initialWaterLevelCm: Double,
        notes: String = ""
    ): Long {
        val now = System.currentTimeMillis()
        val testId = dao.insertTest(
            InfiltrationTestEntity(
                plotName = plotName.trim().ifEmpty { "Parcela sin nombre" },
                diameterM = diameterM,
                outerDiameterM = outerDiameterM,
                waterDensityKgM3 = waterDensityKgM3,
                waterTempC = waterTempC,
                notes = notes.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
        dao.insertRow(
            InfiltrationRowEntity(
                testId = testId,
                orderIndex = 0,
                deltaTMin = 0.0,
                refillCm = 0.0,
                waterLevelCm = initialWaterLevelCm
            )
        )
        return testId
    }

    suspend fun updateTestParameters(
        test: InfiltrationTestEntity,
        plotName: String,
        diameterM: Double,
        outerDiameterM: Double,
        waterDensityKgM3: Double,
        waterTempC: Double,
        notes: String
    ) {
        dao.updateTest(
            test.copy(
                plotName = plotName.trim().ifEmpty { test.plotName },
                diameterM = diameterM,
                outerDiameterM = outerDiameterM,
                waterDensityKgM3 = waterDensityKgM3,
                waterTempC = waterTempC,
                notes = notes.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateTestLocation(test: InfiltrationTestEntity, latitude: Double, longitude: Double) {
        dao.updateTest(
            test.copy(
                latitude = latitude,
                longitude = longitude,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateTestPhoto(test: InfiltrationTestEntity, photoPath: String?) {
        dao.updateTest(
            test.copy(
                photoPath = photoPath,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteTest(testId: Long) {
        dao.deleteTestById(testId)
    }

    suspend fun addIntervalRow(
        testId: Long,
        deltaTMin: Double,
        refillCm: Double,
        waterLevelCm: Double
    ) {
        val existing = dao.getRowsForTestSync(testId)
        val nextIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        dao.insertRow(
            InfiltrationRowEntity(
                testId = testId,
                orderIndex = nextIndex,
                deltaTMin = if (nextIndex == 0) 0.0 else deltaTMin,
                refillCm = if (nextIndex == 0) 0.0 else refillCm,
                waterLevelCm = waterLevelCm
            )
        )
        dao.touchTest(testId)
    }

    suspend fun updateIntervalRow(
        row: InfiltrationRowEntity,
        deltaTMin: Double,
        refillCm: Double,
        waterLevelCm: Double
    ) {
        val isBaseline = row.orderIndex == 0
        dao.updateRow(
            row.copy(
                deltaTMin = if (isBaseline) 0.0 else deltaTMin,
                refillCm = if (isBaseline) 0.0 else refillCm,
                waterLevelCm = waterLevelCm
            )
        )
        dao.touchTest(row.testId)
    }

    suspend fun deleteIntervalRow(row: InfiltrationRowEntity) {
        if (row.orderIndex == 0) return
        dao.deleteRowById(row.id)
        val remaining = dao.getRowsForTestSync(row.testId).sortedBy { it.orderIndex }
        val reindexed = remaining.mapIndexed { idx, item -> item.copy(orderIndex = idx) }
        if (reindexed.isNotEmpty()) {
            dao.updateRows(reindexed)
        }
        dao.touchTest(row.testId)
    }
}
