package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LocalMovieEntity::class, DubSegmentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DuboraDatabase : RoomDatabase() {
    abstract fun duboraDao(): DuboraDao

    companion object {
        @Volatile
        private var INSTANCE: DuboraDatabase? = null

        fun getInstance(context: Context): DuboraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DuboraDatabase::class.java,
                    "dubora_ai_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
