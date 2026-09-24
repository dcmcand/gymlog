package com.gymlog.app.ui.workout

import com.gymlog.app.data.ExerciseDao
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.SetStatus
import com.gymlog.app.data.WorkoutSession
import com.gymlog.app.data.WorkoutSessionDao
import com.gymlog.app.data.displayName
import com.gymlog.app.watch.WatchContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// --- Pure, testable helpers ---

// `sets` is expected in the DAO's getSetsForSession order (workout order, then setNumber).
fun firstPendingIndex(sets: List<ExerciseSet>): Int =
    sets.indexOfFirst { it.status == SetStatus.PENDING }

/**
 * The exercise after [currentExerciseId] (in `sets` order, wrapping) that still has a PENDING
 * set, or null if nothing is pending. With no/unknown current exercise, the first unfinished one.
 */
fun nextPendingExerciseId(sets: List<ExerciseSet>, currentExerciseId: Long?): Long? {
    val order = sets.map { it.exerciseId }.distinct()
    val pending = sets.filter { it.status == SetStatus.PENDING }.map { it.exerciseId }.toSet()
    val start = order.indexOf(currentExerciseId)
    return (1..order.size)
        .map { order[(start + it).mod(order.size)] }
        .firstOrNull { it in pending }
}

// Completing keeps the set's target reps (pre-filled on creation) and just sets the status.
fun completeSet(set: ExerciseSet, status: SetStatus): ExerciseSet =
    set.copy(status = status)

private fun formatWeight(kg: Double?): String {
    if (kg == null) return ""
    return if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString()
}

fun watchContextFor(
    exerciseName: String,
    set: ExerciseSet,
    positionInExercise: Int,
    countInExercise: Int,
): WatchContext {
    val w = formatWeight(set.weightKg)
    val target = if (w.isEmpty()) "${set.repsCompleted ?: 0} reps" else "${w}kg x ${set.repsCompleted ?: 0}"
    return WatchContext(
        exerciseName = exerciseName,
        targetText = target,
        setLabel = "Set $positionInExercise of $countInExercise",
    )
}

data class ActiveWorkoutSnapshot(
    val session: WorkoutSession,
    val orderedSets: List<ExerciseSet>,
    val exerciseNames: Map<Long, String>,
    // Exercise the watch is working on (cycled with the watch's center button). The current
    // set is its first pending set, falling back to the first pending set overall once it's done.
    val selectedExerciseId: Long? = null,
) {
    val currentIndex: Int
        get() = orderedSets.indexOfFirst { it.exerciseId == selectedExerciseId && it.status == SetStatus.PENDING }
            .takeIf { it >= 0 } ?: firstPendingIndex(orderedSets)
    val currentSet: ExerciseSet? get() = orderedSets.getOrNull(currentIndex)

    fun watchContext(): WatchContext? {
        val idx = currentIndex
        if (idx < 0) return null
        val set = orderedSets[idx]
        val forExercise = orderedSets.filter { it.exerciseId == set.exerciseId }
        val position = forExercise.indexOfFirst { it.id == set.id } + 1
        return watchContextFor(exerciseNames[set.exerciseId] ?: "", set, position, forExercise.size)
    }
}

/**
 * Single source of truth for the in-progress workout, shared by [ActiveWorkoutScreen] (UI)
 * and the Pebble path (bridge + listener service) so a set can be completed from the watch
 * while the Activity is backgrounded. Side effects that need a Context (starting the rest
 * timer) are left to callers; this stays Android-free and unit-testable.
 */
object ActiveWorkoutStore {
    private val _state = MutableStateFlow<ActiveWorkoutSnapshot?>(null)
    val state: StateFlow<ActiveWorkoutSnapshot?> = _state.asStateFlow()

    suspend fun load(dao: WorkoutSessionDao, exerciseDao: ExerciseDao, sessionId: Long) {
        val session = dao.getById(sessionId) ?: return
        val sets = dao.getSetsForSession(sessionId)
        val names = sets.map { it.exerciseId }.distinct()
            .mapNotNull { id -> exerciseDao.getById(id)?.let { id to it.displayName() } }
            .toMap()
        val selected = _state.value?.takeIf { it.session.id == sessionId }?.selectedExerciseId
        _state.value = ActiveWorkoutSnapshot(session, sets, names, selected)
    }

    /** Moves the watch to the next unfinished exercise (e.g. the machine is taken). */
    fun selectNextExercise() {
        val snap = _state.value ?: return
        val next = nextPendingExerciseId(snap.orderedSets, snap.currentSet?.exerciseId) ?: return
        _state.value = snap.copy(selectedExerciseId = next)
    }

    /** Completes the first PENDING set. Returns it, or null if none were pending. */
    suspend fun completeCurrentSet(dao: WorkoutSessionDao, status: SetStatus): ExerciseSet? {
        val snap = _state.value ?: return null
        val idx = snap.currentIndex
        if (idx < 0) return null
        val updated = completeSet(snap.orderedSets[idx], status)
        dao.updateSet(updated)
        _state.value = snap.copy(
            orderedSets = snap.orderedSets.toMutableList().apply { this[idx] = updated },
        )
        return updated
    }

    /**
     * Sets [weightKg] on every set in [sets], persisting each and mirroring it into the shared
     * snapshot so a later watch completion can't write the old weight back. Returns the updated sets.
     */
    suspend fun applyWeightToSets(
        dao: WorkoutSessionDao,
        sets: List<ExerciseSet>,
        weightKg: Double?,
    ): List<ExerciseSet> = sets.map { s ->
        s.copy(weightKg = weightKg).also {
            dao.updateSet(it)
            applyExternalSetUpdate(it)
        }
    }

    /** Reflect a set change made elsewhere (e.g. the UI) into the shared snapshot. */
    fun applyExternalSetUpdate(updated: ExerciseSet) {
        val snap = _state.value ?: return
        val i = snap.orderedSets.indexOfFirst { it.id == updated.id }
        if (i < 0) return
        // A set completed on the phone means that's the exercise being worked on; follow it.
        val completedHere = snap.orderedSets[i].status == SetStatus.PENDING && updated.status != SetStatus.PENDING
        _state.value = snap.copy(
            orderedSets = snap.orderedSets.toMutableList().apply { this[i] = updated },
            selectedExerciseId = if (completedHere) updated.exerciseId else snap.selectedExerciseId,
        )
    }

    fun clear() {
        _state.value = null
    }
}
