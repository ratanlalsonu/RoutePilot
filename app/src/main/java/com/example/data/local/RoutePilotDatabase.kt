package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DestinationEntity::class,
        JourneyEntity::class,
        HazardCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RoutePilotDatabase : RoomDatabase() {
    abstract fun routePilotDao(): RoutePilotDao

    companion object {
        @Volatile
        private var INSTANCE: RoutePilotDatabase? = null

        fun getInstance(context: Context): RoutePilotDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoutePilotDatabase::class.java,
                    "routepilot_driver.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
