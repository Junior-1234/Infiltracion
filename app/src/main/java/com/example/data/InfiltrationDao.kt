package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InfiltrationDao {

    @Transaction
    @Query("SELECT * FROM infiltration_tests ORDER BY updatedAt DESC")
    fun getAllTestsWithRows(): Flow<List<TestWithRows>>

    @Transaction
    @Query("SELECT * FROM infiltration_tests WHERE id = :testId")
    fun getTestWithRows(testId: Long): Flow<TestWithRows?>

    @Query("SELECT COUNT(*) FROM infiltration_tests")
    suspend fun countTestsSync(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTest(test: InfiltrationTestEntity): Long

    @Update
    suspend fun updateTest(test: InfiltrationTestEntity)

    @Query("DELETE FROM infiltration_tests WHERE id = :testId")
    suspend fun deleteTestById(testId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRow(row: InfiltrationRowEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRows(rows: List<InfiltrationRowEntity>)

    @Update
    suspend fun updateRow(row: InfiltrationRowEntity)

    @Update
    suspend fun updateRows(rows: List<InfiltrationRowEntity>)

    @Query("DELETE FROM infiltration_rows WHERE id = :rowId")
    suspend fun deleteRowById(rowId: Long)

    @Query("SELECT * FROM infiltration_rows WHERE testId = :testId ORDER BY orderIndex ASC")
    suspend fun getRowsForTestSync(testId: Long): List<InfiltrationRowEntity>

    @Query("UPDATE infiltration_tests SET updatedAt = :updatedAt WHERE id = :testId")
    suspend fun touchTest(testId: Long, updatedAt: Long = System.currentTimeMillis())
}
