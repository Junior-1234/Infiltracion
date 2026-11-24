package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [InfiltrationTestEntity::class, InfiltrationRowEntity::class],
    version = 2,
    exportSchema = false
)
abstract class InfiltrationDatabase : RoomDatabase() {
    abstract fun infiltrationDao(): InfiltrationDao

    companion object {
        @Volatile
        private var INSTANCE: InfiltrationDatabase? = null

        fun getInstance(context: Context): InfiltrationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InfiltrationDatabase::class.java,
                    "infiltration_field_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
