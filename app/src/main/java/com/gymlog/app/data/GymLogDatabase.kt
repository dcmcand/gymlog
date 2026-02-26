package com.gymlog.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        Workout::class,
        WorkoutExercise::class,
        WorkoutSession::class,
        ExerciseSet::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GymLogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        @Volatile
        private var INSTANCE: GymLogDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE exercise_sets SET status = 'EASY' WHERE status = 'COMPLETED'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN cardioFixedDimension TEXT")
                db.execSQL("ALTER TABLE exercises ADD COLUMN fixedValue INTEGER")
                db.execSQL("ALTER TABLE exercises ADD COLUMN level INTEGER")
                db.execSQL("ALTER TABLE exercises ADD COLUMN distanceDisplayKm INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename tables
                db.execSQL("ALTER TABLE workout_templates RENAME TO workouts")
                db.execSQL("ALTER TABLE workout_template_exercises RENAME TO workout_exercises")

                // Rename columns
                db.execSQL("ALTER TABLE workout_exercises RENAME COLUMN templateId TO workoutId")
                db.execSQL("ALTER TABLE workout_sessions RENAME COLUMN templateId TO workoutId")

                // Recreate indices with new names (SQLite doesn't support ALTER INDEX)
                db.execSQL("DROP INDEX IF EXISTS index_workout_template_exercises_templateId")
                db.execSQL("DROP INDEX IF EXISTS index_workout_template_exercises_exerciseId")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercises_workoutId ON workout_exercises(workoutId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercises_exerciseId ON workout_exercises(exerciseId)")
                db.execSQL("DROP INDEX IF EXISTS index_workout_sessions_templateId")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_workoutId ON workout_sessions(workoutId)")
            }
        }

        fun getDatabase(context: Context): GymLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GymLogDatabase::class.java,
                    "gymlog_database"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
