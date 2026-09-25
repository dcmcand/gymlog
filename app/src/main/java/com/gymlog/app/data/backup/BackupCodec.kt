package com.gymlog.app.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.DateTimeException
import java.time.Instant

object BackupCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true // write every field so the file is self-describing
        ignoreUnknownKeys = true // additive fields from a newer app still import
    }

    fun encode(data: BackupData, exportedAt: Instant, appVersion: String): String =
        json.encodeToString(BackupFile.serializer(), data.toFile(exportedAt, appVersion))

    /** Parses and fully validates [text] before anything touches the database. */
    fun decode(text: String): BackupData {
        val root = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            throw BackupException.NotABackup()
        }
        val version = ((root as? JsonObject)?.get("formatVersion") as? JsonPrimitive)?.intOrNull
            ?: throw BackupException.NotABackup()
        if (version < 1) throw BackupException.NotABackup()
        if (version > BACKUP_FORMAT_VERSION) throw BackupException.NewerVersion(version)

        val data = try {
            json.decodeFromJsonElement(BackupFile.serializer(), root).toData()
        } catch (e: SerializationException) {
            throw BackupException.Corrupt(e.message ?: "unreadable")
        } catch (e: IllegalArgumentException) {
            throw BackupException.Corrupt(e.message ?: "unreadable")
        } catch (e: DateTimeException) {
            throw BackupException.Corrupt(e.message ?: "bad date")
        }
        validate(data)
        return data
    }

    private fun validate(d: BackupData) {
        fun unique(table: String, ids: List<Long>) {
            if (ids.size != ids.toSet().size) throw BackupException.Corrupt("duplicate id in $table")
        }
        unique("exercises", d.exercises.map { it.id })
        unique("workouts", d.workouts.map { it.id })
        unique("workoutExercises", d.workoutExercises.map { it.id })
        unique("sessions", d.sessions.map { it.id })
        unique("sets", d.sets.map { it.id })

        val exercises = d.exercises.map { it.id }.toSet()
        val workouts = d.workouts.map { it.id }.toSet()
        val sessions = d.sessions.map { it.id }.toSet()
        fun require(ok: Boolean, what: String) {
            if (!ok) throw BackupException.Corrupt(what)
        }
        d.workoutExercises.forEach {
            require(it.workoutId in workouts, "workout exercise ${it.id} -> missing workout ${it.workoutId}")
            require(it.exerciseId in exercises, "workout exercise ${it.id} -> missing exercise ${it.exerciseId}")
        }
        d.sessions.forEach {
            require(it.workoutId == null || it.workoutId in workouts, "session ${it.id} -> missing workout ${it.workoutId}")
        }
        d.sets.forEach {
            require(it.sessionId in sessions, "set ${it.id} -> missing session ${it.sessionId}")
            require(it.exerciseId in exercises, "set ${it.id} -> missing exercise ${it.exerciseId}")
        }
    }
}
