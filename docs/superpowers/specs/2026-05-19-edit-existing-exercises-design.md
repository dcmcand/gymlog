# Edit Existing Exercises

Issue: [#24](https://github.com/dcmcand/gymlog/issues/24)

## Problem

`ExerciseListScreen` only supports add and delete. There's no way to fix a typo in a name, change a cardio level, or adjust a weight increment (the new field from #22) without deleting the exercise — and delete cascades to every `ExerciseSet` ever logged against it.

## Goal

Add an edit path that updates the existing `Exercise` row by id. All linked `ExerciseSet` and `WorkoutExercise` rows continue pointing at it, so history is preserved.

## Scope

In scope:
- Pencil icon on each row in `ExerciseListScreen`, next to the trash icon.
- Reusable dialog that handles both add and edit modes.
- Editable fields in edit mode: `name`, `weightIncrementKg` (weight), `cardioFixedDimension`, `fixedValue`, `level`, `distanceDisplayKm` (cardio).
- Type (WEIGHT vs CARDIO) is locked in edit mode (chips disabled).

Out of scope:
- Changing exercise type after creation.
- Editing from the progress screen.

## Design

### Dialog refactor

Rename `AddExerciseDialog` to `ExerciseDialog`. Signature:

```kotlin
private fun ExerciseDialog(
    existing: Exercise? = null,
    onDismiss: () -> Unit,
    onConfirm: (Exercise) -> Unit
)
```

Behavior:
- All state initializers (`name`, `selectedType`, `fixedDimension`, `fixedValueText`, `distanceDisplayKm`, `levelText`, `incrementText`) pre-populate from `existing` when non-null, otherwise use existing defaults.
- For cardio fixed-value display: convert stored meters/seconds back to km/min as appropriate when pre-filling `fixedValueText`.
- Dialog title: `"Add Exercise"` if `existing == null` else `"Edit Exercise"`.
- Confirm button label: `"Add"` vs `"Save"`.
- Type filter chips disabled when `existing != null`.
- `buildExercise()` preserves `existing.id` (and `existing.weightIncrementKg` fallback) so the row updates in place rather than inserting a new one.

### Row UI

`ExerciseRow` gains a new `onEdit` callback and a pencil `IconButton` placed before the delete button in `trailingContent`.

### Screen wiring

`ExerciseListScreen`:
- New state: `var editingExercise by remember { mutableStateOf<Exercise?>(null) }`.
- Row's `onEdit = { editingExercise = exercise }`.
- One dialog block handles both flows:

```kotlin
if (showAddDialog || editingExercise != null) {
    ExerciseDialog(
        existing = editingExercise,
        onDismiss = { showAddDialog = false; editingExercise = null },
        onConfirm = { exercise ->
            scope.launch {
                if (editingExercise != null) exerciseDao.update(exercise)
                else exerciseDao.insert(exercise)
            }
            showAddDialog = false
            editingExercise = null
        }
    )
}
```

### Data layer

No changes. `ExerciseDao.update(...)` already exists.

## Risks

- Forgetting to copy `id` when editing means the update becomes an insert (Room with `OnConflictStrategy.REPLACE` would overwrite, but `@Update` uses primary key). Mitigation: `buildExercise()` always passes `existing?.id ?: 0L`.
- Cardio value field needs round-trip conversion (stored as meters; displayed as km when `distanceDisplayKm` is true). The existing buildExercise() converts on write; the new pre-fill must do the inverse on read.

## Tests

UI dialog is hard to unit-test without instrumented tests (none in this repo). Verification is manual:
- Edit a weight exercise's name, increment - row updates; sessions still resolve correctly.
- Edit a cardio exercise's level and distance value - displayName() reflects changes.
- Type chips visibly disabled in edit mode.
