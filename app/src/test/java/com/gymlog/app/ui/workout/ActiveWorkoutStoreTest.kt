package com.gymlog.app.ui.workout

import com.gymlog.app.data.ExerciseDao
import com.gymlog.app.data.ExerciseSet
import com.gymlog.app.data.SessionStatus
import com.gymlog.app.data.SetStatus
import com.gymlog.app.data.WorkoutSession
import com.gymlog.app.data.WorkoutSessionDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate

private fun set(
    id: Long,
    num: Int,
    status: SetStatus,
    reps: Int? = 5,
    weight: Double? = 60.0,
    exerciseId: Long = 1,
) = ExerciseSet(
    id = id,
    sessionId = 1,
    exerciseId = exerciseId,
    setNumber = num,
    weightKg = weight,
    repsCompleted = reps,
    status = status,
)

// Only the members a test overrides are real; anything else fails loudly.
private inline fun <reified T : Any> unimplemented(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
        throw UnsupportedOperationException(m.name)
    } as T

/** In-memory session DAO holding one in-progress session's sets. */
private class FakeSessionDao(initial: List<ExerciseSet>) :
    WorkoutSessionDao by unimplemented<WorkoutSessionDao>() {
    val sets = initial.toMutableList()
    override suspend fun getById(id: Long) = WorkoutSession(
        id = id,
        workoutId = 1,
        date = LocalDate.of(2026, 9, 24),
        status = SessionStatus.IN_PROGRESS,
        startedAt = Instant.EPOCH,
    )
    override suspend fun getSetsForSession(sessionId: Long) = sets.toList()
    override suspend fun updateSet(set: ExerciseSet) {
        sets[sets.indexOfFirst { it.id == set.id }] = set
    }
}

private class FakeExerciseDao : ExerciseDao by unimplemented<ExerciseDao>() {
    override suspend fun getById(id: Long) = null
}

class ActiveWorkoutStoreTest {

    @After
    fun tearDown() = ActiveWorkoutStore.clear()

    @Test
    fun `completing from the watch after a phone weight change keeps the new weight`() = runBlocking {
        data class Case(val name: String, val sets: List<ExerciseSet>, val newWeight: Double?)
        val cases = listOf(
            Case(
                "all pending",
                listOf(set(1, 1, SetStatus.PENDING), set(2, 2, SetStatus.PENDING)),
                70.0,
            ),
            Case(
                "one already done",
                listOf(set(1, 1, SetStatus.EASY), set(2, 2, SetStatus.PENDING)),
                62.5,
            ),
            Case(
                "weight cleared",
                listOf(set(1, 1, SetStatus.PENDING)),
                null,
            ),
        )
        for (c in cases) {
            val dao = FakeSessionDao(c.sets)
            ActiveWorkoutStore.load(dao, FakeExerciseDao(), 1)

            val updated = ActiveWorkoutStore.applyWeightToSets(dao, c.sets, c.newWeight)
            ActiveWorkoutStore.completeCurrentSet(dao, SetStatus.HARD)

            assertEquals(c.name, c.sets.map { c.newWeight }, updated.map { it.weightKg })
            assertEquals(c.name, c.sets.map { c.newWeight }, dao.sets.map { it.weightKg })
            assertEquals(
                c.name,
                c.sets.map { c.newWeight },
                ActiveWorkoutStore.state.value?.orderedSets?.map { it.weightKg },
            )
        }
    }

    @Test
    fun `firstPendingIndex returns first PENDING in order`() {
        data class Case(val sets: List<ExerciseSet>, val expected: Int)
        val cases = listOf(
            Case(listOf(set(1, 1, SetStatus.EASY), set(2, 2, SetStatus.PENDING), set(3, 3, SetStatus.PENDING)), 1),
            Case(listOf(set(1, 1, SetStatus.PENDING)), 0),
            Case(listOf(set(1, 1, SetStatus.EASY), set(2, 2, SetStatus.HARD)), -1),
            Case(emptyList(), -1),
        )
        for (c in cases) assertEquals(c.expected, firstPendingIndex(c.sets))
    }

    @Test
    fun `completeSet sets status and keeps target reps`() {
        val s = set(1, 1, SetStatus.PENDING, reps = 5)
        assertEquals(SetStatus.HARD, completeSet(s, SetStatus.HARD).status)
        assertEquals(5, completeSet(s, SetStatus.HARD).repsCompleted)
    }

    @Test
    fun `watchContextFor formats weight reps and set label`() {
        val ctx = watchContextFor(
            "Bench Press",
            set(1, 2, SetStatus.PENDING, reps = 5, weight = 60.0),
            positionInExercise = 2,
            countInExercise = 4,
        )
        assertEquals("Bench Press", ctx.exerciseName)
        assertEquals("60kg x 5", ctx.targetText)
        assertEquals("Set 2 of 4", ctx.setLabel)
    }

    @Test
    fun `snapshot derives current set and watch context`() {
        val sets = listOf(
            set(1, 1, SetStatus.EASY, exerciseId = 1),
            set(2, 2, SetStatus.PENDING, reps = 8, weight = 40.0, exerciseId = 1),
        )
        val snap = ActiveWorkoutSnapshot(
            session = com.gymlog.app.data.WorkoutSession(
                id = 1,
                workoutId = 1,
                date = java.time.LocalDate.of(2026, 7, 10),
                status = com.gymlog.app.data.SessionStatus.IN_PROGRESS,
                startedAt = java.time.Instant.EPOCH,
            ),
            orderedSets = sets,
            exerciseNames = mapOf(1L to "Squat"),
        )
        assertEquals(1, snap.currentIndex)
        assertEquals(2L, snap.currentSet?.id)
        assertEquals("Squat", snap.watchContext()?.exerciseName)
        assertEquals("40kg x 8", snap.watchContext()?.targetText)
        assertEquals("Set 2 of 2", snap.watchContext()?.setLabel)
    }

    private fun snapshot(sets: List<ExerciseSet>, selected: Long? = null) = ActiveWorkoutSnapshot(
        session = WorkoutSession(
            id = 1,
            workoutId = 1,
            date = LocalDate.of(2026, 9, 24),
            status = SessionStatus.IN_PROGRESS,
            startedAt = Instant.EPOCH,
        ),
        orderedSets = sets,
        exerciseNames = emptyMap(),
        selectedExerciseId = selected,
    )

    // Workout order is exercise 9, then 3, then 5 (ids deliberately not ascending).
    private val workout = listOf(
        set(1, 1, SetStatus.EASY, exerciseId = 9),
        set(2, 2, SetStatus.PENDING, exerciseId = 9),
        set(3, 1, SetStatus.PENDING, exerciseId = 3),
        set(4, 2, SetStatus.PENDING, exerciseId = 3),
        set(5, 1, SetStatus.EASY, exerciseId = 5),
        set(6, 2, SetStatus.EASY, exerciseId = 5),
        set(7, 1, SetStatus.PENDING, exerciseId = 7),
    )

    @Test
    fun `nextPendingExerciseId cycles through unfinished exercises in workout order`() {
        data class Case(val name: String, val sets: List<ExerciseSet>, val current: Long?, val expected: Long?)
        val cases = listOf(
            Case("advances to next unfinished", workout, 9, 3),
            Case("skips finished exercise", workout, 3, 7),
            Case("wraps to the start", workout, 7, 9),
            Case("no current starts at first", workout, null, 9),
            Case("current finished moves past it", workout, 5, 7),
            Case("only one unfinished stays", listOf(set(1, 1, SetStatus.PENDING, exerciseId = 4)), 4, 4),
            Case("nothing pending", listOf(set(1, 1, SetStatus.EASY, exerciseId = 4)), 4, null),
            Case("empty", emptyList(), null, null),
        )
        for (c in cases) assertEquals(c.name, c.expected, nextPendingExerciseId(c.sets, c.current))
    }

    @Test
    fun `current set follows the selected exercise while it has pending sets`() {
        data class Case(val name: String, val selected: Long?, val expectedSetId: Long?)
        val cases = listOf(
            Case("no selection uses first pending", null, 2),
            Case("selected exercise with pending", 3, 3),
            Case("selected exercise finished falls back to first pending", 5, 2),
            Case("selected exercise not in session falls back", 42, 2),
        )
        for (c in cases) assertEquals(c.name, c.expectedSetId, snapshot(workout, c.selected).currentSet?.id)
    }

    @Test
    fun `selectNextExercise then completing records the set on the selected exercise`() = runBlocking {
        val dao = FakeSessionDao(workout)
        ActiveWorkoutStore.load(dao, FakeExerciseDao(), 1)

        ActiveWorkoutStore.selectNextExercise() // 9 -> 3 (machine taken)
        val done = ActiveWorkoutStore.completeCurrentSet(dao, SetStatus.EASY)

        assertEquals(3L, done?.id)
        assertEquals(SetStatus.EASY, dao.sets.first { it.id == 3L }.status)
        assertEquals(SetStatus.PENDING, dao.sets.first { it.id == 2L }.status)
        // Stays on the selected exercise for its next set.
        assertEquals(4L, ActiveWorkoutStore.state.value?.currentSet?.id)
    }

    @Test
    fun `reloading the store keeps the selected exercise`() = runBlocking {
        val dao = FakeSessionDao(workout)
        ActiveWorkoutStore.load(dao, FakeExerciseDao(), 1)
        ActiveWorkoutStore.selectNextExercise()

        ActiveWorkoutStore.load(dao, FakeExerciseDao(), 1) // e.g. a set was added on the phone

        assertEquals(3L, ActiveWorkoutStore.state.value?.selectedExerciseId)
    }

    @Test
    fun `completing a set on the phone selects that exercise for the watch`() = runBlocking {
        val dao = FakeSessionDao(workout)
        ActiveWorkoutStore.load(dao, FakeExerciseDao(), 1)

        ActiveWorkoutStore.applyExternalSetUpdate(workout.first { it.id == 3L }.copy(status = SetStatus.HARD))

        assertEquals(4L, ActiveWorkoutStore.state.value?.currentSet?.id)
    }
}
