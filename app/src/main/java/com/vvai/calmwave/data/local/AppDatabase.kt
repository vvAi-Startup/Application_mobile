package com.vvai.calmwave.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.vvai.calmwave.data.model.AnalyticsEvent

/**
 * Banco de dados local do aplicativo usando Room
 */
@Database(
    entities = [AnalyticsEvent::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun analyticsEventDao(): AnalyticsEventDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calmwave_database"
                )
                    .fallbackToDestructiveMigration() // Em produção, use migrations adequadas
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
