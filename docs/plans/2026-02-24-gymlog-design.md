# GymLog - Design Document

## Goal

A local-only Android app for tracking gym workouts. Define exercises, combine them into workout templates, log sets during gym sessions with quick weight increment buttons, and view progress over time via calendar and charts.

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- Room database for local persistence
- Compose Navigation (bottom nav + full-screen flows)
- Vico for line charts
- Min SDK 31 (Android 12+), Target SDK 36
- Same build toolchain as vibro (AGP 9.0.1, Gradle 9.1.0, JDK 21)

## Data Model

### Exercise
- id (Long, auto-generated)
- name (String)
- type (enum: WEIGHT, CARDIO)

### WorkoutTemplate
- id (Long, auto-generated)
- name (String)

### WorkoutTemplateExercise
- id (Long, auto-generated)
- templateId (FK -> WorkoutTemplate)
- exerciseId (FK -> Exercise)
- targetSets (Int)
- targetReps (Int, nullable - weight exercises)
- targetWeightKg (Double, nullable - weight exercises)
- targetDistanceM (Int, nullable - cardio)
- targetDurationSec (Int, nullable - cardio)
- sortOrder (Int)

### WorkoutSession
- id (Long, auto-generated)
- templateId (FK -> WorkoutTemplate)
- date (LocalDate)
- status (enum: IN_PROGRESS, COMPLETED)
- startedAt (Instant)
- completedAt (Instant, nullable)

### ExerciseSet
- id (Long, auto-generated)
- sessionId (FK -> WorkoutSession)
- exerciseId (FK -> Exercise)
- setNumber (Int)
- weightKg (Double, nullable - weight exercises)
- repsCompleted (Int, nullable - weight exercises)
- distanceM (Int, nullable - cardio)
- durationSec (Int, nullable - cardio)
- status (enum: COMPLETED, PARTIAL, FAILED, PENDING)

## UI Screens

### 1. Home / Calendar (default tab)
- Monthly calendar grid with colored dots on workout days
- Tap a day to see workout summary for that day
- "New Workout" FAB at the bottom

### 2. New Workout (full-screen flow)
- Step 1: Pick a template from the list
- Step 2: Active workout screen
  - List of exercises from the template
  - Each exercise expands to show its sets
  - Each set row: weight display with +/- increment chips (1.25, 2.5, 5, 10, 20 kg), reps count, status buttons (completed/partial/failed)
  - Weight defaults to last session's weight for that exercise
  - For cardio: distance (meters) and duration (mm:ss) fields
  - Can add/remove sets beyond the template default
- "Finish Workout" button saves and returns to calendar

### 3. Workout Templates (tab)
- List of all templates
- Create new template: name + pick exercises from library + set targets
- Edit existing template
- Delete template

### 4. Exercise Library (tab)
- List of all exercises grouped by type (weight/cardio)
- Add new exercise (name + type)
- Tap exercise to see progress chart
- Delete/edit exercise

### 5. Exercise Progress (navigated to from library or workout)
- Line chart: X = date, Y = max weight (weight) or distance (cardio)
- Shows data points from all sessions containing that exercise

## Navigation

Bottom nav bar with 3 tabs:
1. Calendar (home)
2. Templates
3. Exercises

New Workout and Exercise Progress are full-screen destinations above the nav bar.

## Key Behaviors

- Weight increment buttons: row of chips (1.25, 2.5, 5, 10, 20). Tap to add, long-press to subtract.
- "Last weight" auto-fill: query most recent ExerciseSet for that exercise where status != PENDING.
- Mid-workout persistence: session saved as IN_PROGRESS. If app is reopened with an in-progress session, prompt to resume or discard.
- Charts use Vico library for simple line charts with date axis.

## Project Location

`/home/chuck/devel/android/gymlog/`
