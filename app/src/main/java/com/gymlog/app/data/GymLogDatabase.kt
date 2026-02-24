package com.gymlog.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Exercise::class,
        WorkoutTemplate::class,
        WorkoutTemplateExercise::class,
        WorkoutSession::class,
        ExerciseSet::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GymLogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        @Volatile
        private var INSTANCE: GymLogDatabase? = null

        fun getDatabase(context: Context): GymLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GymLogDatabase::class.java,
                    "gymlog_database"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
