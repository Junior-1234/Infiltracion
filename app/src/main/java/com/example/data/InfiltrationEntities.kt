package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "infiltration_tests")
data class InfiltrationTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val plotName: String,
    val diameterM: Double = 0.30,
    val outerDiameterM: Double = 0.60,
    val waterDensityKgM3: Double = 1000.0,
    val waterTempC: Double = 20.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val photoPath: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "infiltration_rows",
    foreignKeys = [
        ForeignKey(
            entity = InfiltrationTestEntity::class,
            parentColumns = ["id"],
            childColumns = ["testId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("testId")]
)
data class InfiltrationRowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val testId: Long,
    val orderIndex: Int,
    val deltaTMin: Double,
    val refillCm: Double,
    val waterLevelCm: Double
)

data class TestWithRows(
    @Embedded val test: InfiltrationTestEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "testId"
    )
    val rows: List<InfiltrationRowEntity>
)
