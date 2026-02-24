# GymLog Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a local-only Android gym workout tracker with exercise library, workout templates, session logging with weight increments, calendar view, and progress charts.

**Architecture:** Room database with typed entities and DAOs for persistence. Jetpack Compose with Material 3 for UI. Compose Navigation with bottom nav bar (Calendar, Templates, Exercises). Vico for line charts.

**Tech Stack:** Kotlin 2.2.10, AGP 9.0.1, Gradle 9.1.0, JDK 21, Room 2.8.4, Compose BOM 2026.01.01, Navigation Compose 2.9.7, Vico 3.0.0, Min SDK 31, Target SDK 36

**Build command:** `ANDROID_HOME=$HOME/Android JAVA_HOME=/home/chuck/.local/jdk/jdk-21.0.10+7 PATH=/home/chuck/.local/jdk/jdk-21.0.10+7/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$HOME/Android/cmdline-tools/latest/bin:$HOME/Android/platform-tools ./gradlew assembleDebug test`

**Working directory:** `/home/chuck/devel/android/gymlog/`

---

### Task 0: Scaffold Gradle Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/gymlog/app/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`

**Step 1: Create gradle wrapper**

The wrapper already exists at the system level. Copy from vibro:

```bash
cp -r /home/chuck/devel/android/vibro/gradle/wrapper /home/chuck/devel/android/gymlog/gradle/wrapper
cp /home/chuck/devel/android/vibro/gradlew /home/chuck/devel/android/gymlog/gradlew
cp /home/chuck/devel/android/vibro/gradlew.bat /home/chuck/devel/android/gymlog/gradlew.bat
chmod +x /home/chuck/devel/android/gymlog/gradlew
```

**Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.0.1"
kotlin = "2.2.10"
ksp = "2.2.10-2.0.2"
composeBom = "2026.01.01"
activityCompose = "1.10.1"
coreKtx = "1.16.0"
lifecycleRuntimeKtx = "2.9.0"
room = "2.8.4"
navigationCompose = "2.9.7"
vico = "3.0.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GymLog"
include(":app")
```

**Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

**Step 5: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

**Step 6: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gymlog.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gymlog.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.vico.compose.m3)
    testImplementation(libs.junit)
}
```

**Step 7: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="false"
        android:label="GymLog"
        android:supportsRtl="true"
        android:theme="@style/Theme.GymLog">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.GymLog">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Step 8: Create `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">GymLog</string>
</resources>
```

**Step 9: Create `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.GymLog" parent="android:Theme.Material.NoActionBar" />
</resources>
```

**Step 10: Create minimal `MainActivity.kt`**

```kotlin
package com.gymlog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("GymLog")
        }
    }
}
```

**Step 11: Build to verify scaffold**

Run: build command from header
Expected: BUILD SUCCESSFUL

**Step 12: Commit**

```bash
git add -A
git commit -m "feat: scaffold GymLog project with Room, Navigation, Vico dependencies"
```

---

### Task 1: Exercise Entity and DAO

**Files:**
- Create: `app/src/main/java/com/gymlog/app/data/ExerciseType.kt`
- Create: `app/src/main/java/com/gymlog/app/data/Exercise.kt`
- Create: `app/src/main/java/com/gymlog/app/data/ExerciseDao.kt`
- Create: `app/src/main/java/com/gymlog/app/data/GymLogDatabase.kt`
- Create: `app/src/main/java/com/gymlog/app/data/Converters.kt`
- Test: `app/src/test/java/com/gymlog/app/data/ExerciseTest.kt`

**Step 1: Write tests for Exercise entity**

```kotlin
package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseTest {

    @Test
    fun `exercise has name and type`() {
        val exercise = Exercise(name = "Squat", type = ExerciseType.WEIGHT)
        assertEquals("Squat", exercise.name)
        assertEquals(ExerciseType.WEIGHT, exercise.type)
    }

    @Test
    fun `exercise id defaults to 0`() {
        val exercise = Exercise(name = "Deadlift", type = ExerciseType.WEIGHT)
        assertEquals(0L, exercise.id)
    }

    @Test
    fun `exercise type has WEIGHT and CARDIO`() {
        assertEquals(2, ExerciseType.entries.size)
        assertEquals("WEIGHT", ExerciseType.WEIGHT.name)
        assertEquals("CARDIO", ExerciseType.CARDIO.name)
    }
}
```

**Step 2: Run tests - expect FAIL**

**Step 3: Implement ExerciseType enum**

```kotlin
package com.gymlog.app.data

enum class ExerciseType {
    WEIGHT,
    CARDIO
}
```

**Step 4: Implement Exercise entity**

```kotlin
package com.gymlog.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ExerciseType
)
```

**Step 5: Implement Converters**

```kotlin
package com.gymlog.app.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromExerciseType(value: ExerciseType): String = value.name

    @TypeConverter
    fun toExerciseType(value: String): ExerciseType = ExerciseType.valueOf(value)

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromSetStatus(value: SetStatus): String = value.name

    @TypeConverter
    fun toSetStatus(value: String): SetStatus = SetStatus.valueOf(value)

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)
}
```

Note: SetStatus and SessionStatus will be created in Task 3. For now, create placeholder enums so converters compile, OR add those converters later. Simplest approach: only add the converters needed now (ExerciseType, LocalDate, Instant) and add the rest in Task 3.

**Step 6: Implement ExerciseDao**

```kotlin
package com.gymlog.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE type = :type ORDER BY name ASC")
    fun getByType(type: ExerciseType): Flow<List<Exercise>>

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)
}
```

**Step 7: Create initial GymLogDatabase (just Exercise table for now)**

```kotlin
package com.gymlog.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Exercise::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GymLogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        @Volatile
        private var INSTANCE: GymLogDatabase? = null

        fun getDatabase(context: Context): GymLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GymLogDatabase::class.java,
                    "gymlog_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

**Step 8: Run tests - expect PASS**

**Step 9: Build to verify compilation**

**Step 10: Commit**

```bash
git commit -m "feat: add Exercise entity, DAO, and database"
```

---

### Task 2: Workout Template Entities and DAOs

**Files:**
- Create: `app/src/main/java/com/gymlog/app/data/WorkoutTemplate.kt`
- Create: `app/src/main/java/com/gymlog/app/data/WorkoutTemplateExercise.kt`
- Create: `app/src/main/java/com/gymlog/app/data/WorkoutTemplateDao.kt`
- Modify: `app/src/main/java/com/gymlog/app/data/GymLogDatabase.kt` (add entities + DAO)
- Test: `app/src/test/java/com/gymlog/app/data/WorkoutTemplateTest.kt`

**Step 1: Write tests**

```kotlin
package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutTemplateTest {

    @Test
    fun `template has name and auto-generated id`() {
        val template = WorkoutTemplate(name = "Push Day")
        assertEquals("Push Day", template.name)
        assertEquals(0L, template.id)
    }

    @Test
    fun `template exercise links template to exercise with targets`() {
        val te = WorkoutTemplateExercise(
            templateId = 1L,
            exerciseId = 2L,
            targetSets = 5,
            targetReps = 8,
            targetWeightKg = 100.0,
            targetDistanceM = null,
            targetDurationSec = null,
            sortOrder = 0
        )
        assertEquals(1L, te.templateId)
        assertEquals(2L, te.exerciseId)
        assertEquals(5, te.targetSets)
        assertEquals(8, te.targetReps)
        assertEquals(100.0, te.targetWeightKg!!, 0.01)
        assertNull(te.targetDistanceM)
        assertNull(te.targetDurationSec)
        assertEquals(0, te.sortOrder)
    }

    @Test
    fun `cardio template exercise has distance and duration`() {
        val te = WorkoutTemplateExercise(
            templateId = 1L,
            exerciseId = 3L,
            targetSets = 1,
            targetReps = null,
            targetWeightKg = null,
            targetDistanceM = 2000,
            targetDurationSec = 480,
            sortOrder = 2
        )
        assertEquals(2000, te.targetDistanceM)
        assertEquals(480, te.targetDurationSec)
        assertNull(te.targetReps)
        assertNull(te.targetWeightKg)
    }
}
```

**Step 2: Run tests - expect FAIL**

**Step 3: Implement entities**

`WorkoutTemplate.kt`:
```kotlin
package com.gymlog.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_templates")
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
```

`WorkoutTemplateExercise.kt`:
```kotlin
package com.gymlog.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId"), Index("exerciseId")]
)
data class WorkoutTemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val targetSets: Int,
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
    val targetDistanceM: Int? = null,
    val targetDurationSec: Int? = null,
    val sortOrder: Int
)
```

**Step 4: Implement WorkoutTemplateDao**

```kotlin
package com.gymlog.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateDao {
    @Query("SELECT * FROM workout_templates ORDER BY name ASC")
    fun getAll(): Flow<List<WorkoutTemplate>>

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getById(id: Long): WorkoutTemplate?

    @Insert
    suspend fun insert(template: WorkoutTemplate): Long

    @Update
    suspend fun update(template: WorkoutTemplate)

    @Delete
    suspend fun delete(template: WorkoutTemplate)

    @Query("SELECT * FROM workout_template_exercises WHERE templateId = :templateId ORDER BY sortOrder ASC")
    suspend fun getExercisesForTemplate(templateId: Long): List<WorkoutTemplateExercise>

    @Insert
    suspend fun insertTemplateExercise(templateExercise: WorkoutTemplateExercise): Long

    @Query("DELETE FROM workout_template_exercises WHERE templateId = :templateId")
    suspend fun deleteAllExercisesForTemplate(templateId: Long)
}
```

**Step 5: Update GymLogDatabase - add WorkoutTemplate and WorkoutTemplateExercise entities, add workoutTemplateDao()**

**Step 6: Run tests - expect PASS, then build**

**Step 7: Commit**

```bash
git commit -m "feat: add WorkoutTemplate entities and DAO"
```

---

### Task 3: Workout Session and ExerciseSet Entities and DAOs

**Files:**
- Create: `app/src/main/java/com/gymlog/app/data/SessionStatus.kt`
- Create: `app/src/main/java/com/gymlog/app/data/SetStatus.kt`
- Create: `app/src/main/java/com/gymlog/app/data/WorkoutSession.kt`
- Create: `app/src/main/java/com/gymlog/app/data/ExerciseSet.kt`
- Create: `app/src/main/java/com/gymlog/app/data/WorkoutSessionDao.kt`
- Modify: `app/src/main/java/com/gymlog/app/data/GymLogDatabase.kt`
- Modify: `app/src/main/java/com/gymlog/app/data/Converters.kt` (add SetStatus, SessionStatus converters)
- Test: `app/src/test/java/com/gymlog/app/data/WorkoutSessionTest.kt`

**Step 1: Write tests**

```kotlin
package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WorkoutSessionTest {

    @Test
    fun `session status has IN_PROGRESS and COMPLETED`() {
        assertEquals(2, SessionStatus.entries.size)
    }

    @Test
    fun `set status has PENDING, COMPLETED, PARTIAL, FAILED`() {
        assertEquals(4, SetStatus.entries.size)
    }

    @Test
    fun `session links to template with date and status`() {
        val now = Instant.now()
        val session = WorkoutSession(
            templateId = 1L,
            date = LocalDate.of(2026, 2, 24),
            status = SessionStatus.IN_PROGRESS,
            startedAt = now
        )
        assertEquals(1L, session.templateId)
        assertEquals(LocalDate.of(2026, 2, 24), session.date)
        assertEquals(SessionStatus.IN_PROGRESS, session.status)
        assertEquals(now, session.startedAt)
        assertNull(session.completedAt)
    }

    @Test
    fun `weight exercise set has weight and reps`() {
        val set = ExerciseSet(
            sessionId = 1L,
            exerciseId = 2L,
            setNumber = 1,
            weightKg = 100.0,
            repsCompleted = 8,
            status = SetStatus.COMPLETED
        )
        assertEquals(100.0, set.weightKg!!, 0.01)
        assertEquals(8, set.repsCompleted)
        assertEquals(SetStatus.COMPLETED, set.status)
        assertNull(set.distanceM)
        assertNull(set.durationSec)
    }

    @Test
    fun `cardio exercise set has distance and duration`() {
        val set = ExerciseSet(
            sessionId = 1L,
            exerciseId = 3L,
            setNumber = 1,
            distanceM = 2000,
            durationSec = 480,
            status = SetStatus.COMPLETED
        )
        assertEquals(2000, set.distanceM)
        assertEquals(480, set.durationSec)
        assertNull(set.weightKg)
        assertNull(set.repsCompleted)
    }
}
```

**Step 2: Run tests - expect FAIL**

**Step 3: Implement enums**

`SessionStatus.kt`:
```kotlin
package com.gymlog.app.data

enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED
}
```

`SetStatus.kt`:
```kotlin
package com.gymlog.app.data

enum class SetStatus {
    PENDING,
    COMPLETED,
    PARTIAL,
    FAILED
}
```

**Step 4: Implement WorkoutSession entity**

```kotlin
package com.gymlog.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("templateId"), Index("date")]
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    val date: LocalDate,
    val status: SessionStatus,
    val startedAt: Instant,
    val completedAt: Instant? = null
)
```

**Step 5: Implement ExerciseSet entity**

```kotlin
package com.gymlog.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class ExerciseSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double? = null,
    val repsCompleted: Int? = null,
    val distanceM: Int? = null,
    val durationSec: Int? = null,
    val status: SetStatus = SetStatus.PENDING
)
```

**Step 6: Implement WorkoutSessionDao**

```kotlin
package com.gymlog.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions WHERE date = :date")
    suspend fun getSessionsForDate(date: LocalDate): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' LIMIT 1")
    suspend fun getInProgressSession(): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSession?

    @Query("SELECT DISTINCT date FROM workout_sessions WHERE date BETWEEN :start AND :end AND status = 'COMPLETED'")
    fun getWorkoutDatesInRange(start: LocalDate, end: LocalDate): Flow<List<LocalDate>>

    @Insert
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM exercise_sets WHERE sessionId = :sessionId ORDER BY exerciseId, setNumber")
    suspend fun getSetsForSession(sessionId: Long): List<ExerciseSet>

    @Query("SELECT * FROM exercise_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId ORDER BY setNumber")
    suspend fun getSetsForExercise(sessionId: Long, exerciseId: Long): List<ExerciseSet>

    @Insert
    suspend fun insertSet(set: ExerciseSet): Long

    @Insert
    suspend fun insertSets(sets: List<ExerciseSet>)

    @Update
    suspend fun updateSet(set: ExerciseSet)

    @Query("DELETE FROM exercise_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("""
        SELECT es.* FROM exercise_sets es
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id
        WHERE es.exerciseId = :exerciseId AND es.status != 'PENDING'
        ORDER BY ws.date DESC, es.setNumber ASC
        LIMIT 1
    """)
    suspend fun getLastCompletedSet(exerciseId: Long): ExerciseSet?

    @Query("""
        SELECT MAX(es.weightKg) as maxWeight, ws.date
        FROM exercise_sets es
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id
        WHERE es.exerciseId = :exerciseId AND es.status = 'COMPLETED' AND es.weightKg IS NOT NULL
        GROUP BY ws.date
        ORDER BY ws.date ASC
    """)
    suspend fun getWeightProgressForExercise(exerciseId: Long): List<ProgressEntry>

    @Query("""
        SELECT MAX(es.distanceM) as maxDistance, es.durationSec as minDuration, ws.date
        FROM exercise_sets es
        INNER JOIN workout_sessions ws ON es.sessionId = ws.id
        WHERE es.exerciseId = :exerciseId AND es.status = 'COMPLETED' AND es.distanceM IS NOT NULL
        GROUP BY ws.date
        ORDER BY ws.date ASC
    """)
    suspend fun getCardioProgressForExercise(exerciseId: Long): List<CardioProgressEntry>
}
```

**Step 7: Create progress data classes**

These are NOT Room entities, just data holders for query results:

```kotlin
// Add to a new file: app/src/main/java/com/gymlog/app/data/ProgressEntry.kt

package com.gymlog.app.data

import java.time.LocalDate

data class ProgressEntry(
    val maxWeight: Double,
    val date: LocalDate
)

data class CardioProgressEntry(
    val maxDistance: Int,
    val minDuration: Int?,
    val date: LocalDate
)
```

**Step 8: Update Converters.kt with SetStatus and SessionStatus converters**

**Step 9: Update GymLogDatabase - add all entities, bump version, add all DAOs**

Final database should have entities: Exercise, WorkoutTemplate, WorkoutTemplateExercise, WorkoutSession, ExerciseSet and DAOs for all three.

**Step 10: Run tests + build**

**Step 11: Commit**

```bash
git commit -m "feat: add WorkoutSession, ExerciseSet entities and DAOs with progress queries"
```

---

### Task 4: Navigation Shell with Bottom Nav

**Files:**
- Create: `app/src/main/java/com/gymlog/app/ui/navigation/GymLogNavigation.kt`
- Create: `app/src/main/java/com/gymlog/app/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/gymlog/app/MainActivity.kt`

**Step 1: Create Screen sealed class for navigation destinations**

```kotlin
package com.gymlog.app.ui.navigation

sealed class Screen(val route: String) {
    data object Calendar : Screen("calendar")
    data object Templates : Screen("templates")
    data object Exercises : Screen("exercises")
    data object NewWorkout : Screen("new_workout/{templateId}") {
        fun createRoute(templateId: Long) = "new_workout/$templateId"
    }
    data object ExerciseProgress : Screen("exercise_progress/{exerciseId}") {
        fun createRoute(exerciseId: Long) = "exercise_progress/$exerciseId"
    }
    data object EditTemplate : Screen("edit_template/{templateId}") {
        fun createRoute(templateId: Long) = "edit_template/$templateId"
    }
    data object CreateTemplate : Screen("create_template")
}
```

**Step 2: Create GymLogNavigation composable with NavHost and bottom nav**

```kotlin
package com.gymlog.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
fun GymLogNavigation() {
    val navController = rememberNavController()

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Calendar, "Calendar", Icons.Default.DateRange),
        BottomNavItem(Screen.Templates, "Templates", Icons.Default.List),
        BottomNavItem(Screen.Exercises, "Exercises", Icons.Default.FitnessCenter)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Calendar.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Calendar.route) {
                Text("Calendar - Coming Soon")
            }
            composable(Screen.Templates.route) {
                Text("Templates - Coming Soon")
            }
            composable(Screen.Exercises.route) {
                Text("Exercises - Coming Soon")
            }
        }
    }
}
```

**Step 3: Update MainActivity to use GymLogNavigation**

```kotlin
package com.gymlog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.gymlog.app.ui.navigation.GymLogNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GymLogNavigation()
            }
        }
    }
}
```

**Step 4: Build**

**Step 5: Commit**

```bash
git commit -m "feat: add navigation shell with bottom nav bar"
```

---

### Task 5: Exercise Library Screen

**Files:**
- Create: `app/src/main/java/com/gymlog/app/ui/exercises/ExerciseListScreen.kt`
- Modify: `app/src/main/java/com/gymlog/app/ui/navigation/GymLogNavigation.kt`

**Implementation details:**
- List all exercises grouped by type (WEIGHT section, CARDIO section)
- FAB to add new exercise (shows dialog with name field + type toggle)
- Tap exercise to navigate to ExerciseProgress screen
- Long-press or swipe to delete
- Use the database singleton from GymLogDatabase.getDatabase(context)
- Collect `exerciseDao.getAll()` as state with `collectAsState()`

**Key composables:**
- `ExerciseListScreen(onExerciseClick: (Long) -> Unit)` - main screen
- `AddExerciseDialog(onDismiss, onConfirm: (name, type) -> Unit)` - add dialog

Wire into NavHost replacing the placeholder.

**Step: Build and commit**

```bash
git commit -m "feat: add exercise library screen with add/delete"
```

---

### Task 6: Workout Templates Screen

**Files:**
- Create: `app/src/main/java/com/gymlog/app/ui/templates/TemplateListScreen.kt`
- Create: `app/src/main/java/com/gymlog/app/ui/templates/EditTemplateScreen.kt`
- Modify: `app/src/main/java/com/gymlog/app/ui/navigation/GymLogNavigation.kt`

**Implementation details:**

`TemplateListScreen`:
- List all templates
- FAB to create new template (navigates to CreateTemplate screen)
- Tap to edit, swipe to delete

`EditTemplateScreen` (used for both create and edit):
- Name text field at top
- List of exercises currently in template (reorderable, with target sets/reps/weight fields)
- "Add Exercise" button opens a picker dialog showing all exercises from library
- For weight exercises: target sets, reps, weight fields
- For cardio exercises: target distance (m) and duration (mm:ss) fields
- Save button persists to database

Wire CreateTemplate and EditTemplate routes into NavHost.

**Step: Build and commit**

```bash
git commit -m "feat: add workout templates screen with create/edit"
```

---

### Task 7: Calendar / Home Screen

**Files:**
- Create: `app/src/main/java/com/gymlog/app/ui/calendar/CalendarScreen.kt`
- Modify: `app/src/main/java/com/gymlog/app/ui/navigation/GymLogNavigation.kt`

**Implementation details:**

- Monthly calendar grid (custom Compose, no external library needed)
- Header with month/year and left/right arrows to navigate months
- 7-column grid (Mon-Sun), days of the current month
- Days with completed workouts get a colored dot indicator
- Tap a day to show that day's workout summary in a bottom sheet or expandable card
- Workout summary: template name, list of exercises with sets completed
- FAB "New Workout" button - navigates to template picker, then to NewWorkout screen
- Query `workoutSessionDao.getWorkoutDatesInRange(firstDayOfMonth, lastDayOfMonth)` for dots
- If there's an IN_PROGRESS session, show a "Resume Workout" banner at top

**Step: Build and commit**

```bash
git commit -m "feat: add calendar home screen with workout indicators"
```

---

### Task 8: New Workout / Active Workout Screen

This is the most complex screen. It should be implemented carefully.

**Files:**
- Create: `app/src/main/java/com/gymlog/app/ui/workout/ActiveWorkoutScreen.kt`
- Create: `app/src/main/java/com/gymlog/app/ui/workout/WeightIncrementChips.kt`
- Modify: `app/src/main/java/com/gymlog/app/ui/navigation/GymLogNavigation.kt`

**Implementation details:**

`ActiveWorkoutScreen(templateId: Long)`:
1. On launch: create a new WorkoutSession (IN_PROGRESS), populate ExerciseSets from template defaults
2. Display list of exercises from the template
3. Each exercise is an expandable card showing its sets

**For each WEIGHT exercise set row:**
- Weight display (e.g., "100.0 kg") with current value
- Row of increment chips: 1.25, 2.5, 5, 10, 20 - tap to add, long-press to subtract
- Reps display with +/- buttons
- Three status buttons: checkmark (COMPLETED), half-circle (PARTIAL), X (FAILED)
- Weight defaults to last session's weight via `getLastCompletedSet(exerciseId)`

**For each CARDIO exercise:**
- Distance field (meters) - numeric input
- Duration field (mm:ss) - numeric input
- Status button (COMPLETED)

**WeightIncrementChips composable:**
```
[1.25] [2.5] [5] [10] [20]
```
- Tap: add increment to current weight
- Long-press: subtract increment from current weight (min 0)

**Bottom bar:**
- "Add Set" button per exercise
- "Finish Workout" button - marks session COMPLETED, sets completedAt, navigates back

**Mid-workout persistence:**
- All set updates are saved to DB immediately (Room insert/update on each status change)
- If app is killed and reopened, CalendarScreen detects IN_PROGRESS session and offers resume

Wire NewWorkout route into NavHost with templateId argument.

**Step: Build and commit**

```bash
git commit -m "feat: add active workout screen with weight increments and set tracking"
```

---

### Task 9: Exercise Progress Chart Screen

**Files:**
- Create: `app/src/main/java/com/gymlog/app/ui/progress/ExerciseProgressScreen.kt`
- Modify: `app/src/main/java/com/gymlog/app/ui/navigation/GymLogNavigation.kt`

**Implementation details:**

`ExerciseProgressScreen(exerciseId: Long)`:
- Top bar with exercise name and back button
- For WEIGHT exercises: line chart with X = date, Y = max weight (kg)
- For CARDIO exercises: line chart with X = date, Y = distance (m)
- Use Vico library: `CartesianChartHost` with `rememberCartesianChart`
- Query data via `getWeightProgressForExercise()` or `getCardioProgressForExercise()`
- Below chart: list of recent sessions showing individual set details

**Vico chart setup (weight example):**
```kotlin
val modelProducer = remember { CartesianChartModelProducer() }
// Feed data points from ProgressEntry list
LaunchedEffect(progressData) {
    modelProducer.runTransaction {
        lineSeries { series(progressData.map { it.maxWeight }) }
    }
}
CartesianChartHost(
    chart = rememberCartesianChart(
        rememberLineCartesianLayer()
    ),
    modelProducer = modelProducer
)
```

Wire ExerciseProgress route into NavHost with exerciseId argument.

**Step: Build and commit**

```bash
git commit -m "feat: add exercise progress chart screen with Vico"
```

---

### Task 10: Template Picker for New Workout

**Files:**
- Create: `app/src/main/java/com/gymlog/app/ui/workout/TemplatePickerScreen.kt`
- Modify: `app/src/main/java/com/gymlog/app/ui/navigation/GymLogNavigation.kt`
- Modify: `app/src/main/java/com/gymlog/app/ui/calendar/CalendarScreen.kt`

**Implementation details:**
- Simple list screen showing all workout templates
- Tap a template to navigate to `ActiveWorkoutScreen(templateId)`
- This is the screen the "New Workout" FAB navigates to
- Add a `TemplatePicker` route to Screen sealed class
- Wire it between Calendar's FAB and ActiveWorkout

**Step: Build and commit**

```bash
git commit -m "feat: add template picker for starting new workouts"
```

---

### Task 11: Polish and Final Build

**Files:**
- Various UI files for visual polish
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/values/ic_launcher_background.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

**Implementation details:**
- Create a simple dumbbell icon for the app launcher
- Verify all navigation flows work
- Verify build succeeds: `./gradlew assembleDebug test`
- APK location: `app/build/outputs/apk/debug/app-debug.apk`

**Step: Build and commit**

```bash
git commit -m "feat: add app icon and final polish"
```
