# Configurable Weight Increment per Exercise

Issue: [#22](https://github.com/dcmcand/gymlog/issues/22)

## Problem

The weight `+`/`-` buttons in the active workout always step by 2.5 kg, and `suggestWeight` rounds to the nearest 2.5 kg. That step suits barbell loading but is wrong for dumbbells, where 1 or 2 kg increments are typical.

## Goal

Let each exercise carry its own weight increment. The +/- buttons and suggested-weight rounding both honor the per-exercise value, defaulting to 2.5 kg so existing exercises behave exactly as before.

## Scope

In scope:
- New `weightIncrementKg` field on `Exercise` (Double, default 2.5).
- Room v7 migration to add the column.
- Increment input in the Add Exercise dialog for weight-type exercises.
- `CompactExerciseCard` +/- buttons use the per-exercise increment.
- `suggestWeight` takes an increment parameter; round-helper becomes generic.
- Unit tests for suggestion behavior with non-default increments.

Out of scope:
- Editing the increment on an existing exercise (depends on issue #24 for an edit dialog).
- Showing the increment value anywhere in the active workout UI.
- Per-exercise increment for cardio (cardio has no weight).

## Design

### Data layer

`app/src/main/java/com/gymlog/app/data/Exercise.kt`

Add a new field:

```kotlin
@ColumnInfo(defaultValue = "2.5")
val weightIncrementKg: Double = 2.5
```

### Database

`app/src/main/java/com/gymlog/app/data/GymLogDatabase.kt`

- Bump `@Database(... version = 7 ...)`.
- Add migration:

```kotlin
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercises ADD COLUMN weightIncrementKg REAL NOT NULL DEFAULT 2.5")
    }
}
```

- Register in `addMigrations(...)`.

### Weight suggestion

`app/src/main/java/com/gymlog/app/data/WeightSuggestion.kt`

- Rename `roundToNearest2Point5(kg)` to `roundToNearest(kg, step)`.
- Add `incrementKg: Double = 2.5` parameter to `suggestWeight`.
- Replace the hardcoded `+ 2.5` / `- 2.5` nudges at the bottom of `suggestWeight` with `+ incrementKg` / `- incrementKg`.
- All call sites pass `exercise.weightIncrementKg`.

### Active workout UI

`app/src/main/java/com/gymlog/app/ui/workout/CompactExerciseCard.kt`

- The card already has access to its `Exercise`. Replace the hardcoded `2.5` at the two button click handlers with `exercise.weightIncrementKg`.
- No visual change. The increment is not labeled on screen.

### Add Exercise dialog

`app/src/main/java/com/gymlog/app/ui/exercises/ExerciseListScreen.kt` (`AddExerciseDialog`)

- For `WEIGHT` type only: append an "Increment (kg)" `OutlinedTextField` after the existing name/type fields.
- Default string value "2.5". Numeric keyboard.
- Validation on confirm: must parse to a Double > 0. If invalid, treat as invalid input (button disabled, same pattern as the existing `isValid` check).
- Pass parsed value into the new `Exercise(...)` constructor.

### Tests

`app/src/test/java/com/gymlog/app/WeightSuggestionTest.kt`

Add table-driven cases:
- Step-up path with increment = 1.0 nudges by 1.0.
- Step-down path with increment = 2.0 nudges by 2.0.
- Default (no override) still nudges by 2.5.
- `roundToNearest(kg, step)` direct cases covering step = 1.0, 2.0, 2.5.

## Migration safety

`ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT 2.5` is a single-statement SQLite operation. All existing rows receive 2.5. No data loss. The Room `defaultValue = "2.5"` annotation matches so schema validation passes.

## Risks

- Forgetting a `suggestWeight` call site: the new parameter has a default of 2.5, so existing callers still compile and behave identically. The behavioral change only happens at sites that opt in by passing `exercise.weightIncrementKg`. Mitigation: audit all call sites during implementation and convert them.
- Users typing nonsense like "0" or "abc": handled by the confirm-button validation.
