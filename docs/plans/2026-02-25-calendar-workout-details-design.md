# Calendar Workout Details Design

**Goal:** Let users tap a date on the calendar to see their workouts, then tap a workout to see full set-by-set details.

**Approach:** Inline expansion on the calendar screen with navigation to a read-only detail screen.

## Calendar Screen Changes

Replace the current plain-text summary below the calendar grid with clickable session cards:

- One card per session on the selected date
- Each card shows: template name, session status, exercise count
- Tapping a card navigates to `WorkoutDetailScreen(sessionId)`
- "No workouts" message remains for dates with no sessions

## New WorkoutDetailScreen

Read-only screen showing full workout breakdown:

- Top bar with "Workout Details" title and back button
- Header: template name, date, session status
- For each exercise in the session:
  - Exercise name as section header
  - Table of sets: set number, weight/distance, reps/duration, status icon
- Weight exercises show: set #, weight (kg), reps, status (check/minus/x icons)
- Cardio exercises show: set #, distance (m), duration (sec), status

## Navigation

- New route: `Screen.WorkoutDetail("workout_detail/{sessionId}")`
- Calendar screen gets new callback: `onWorkoutClick: (Long) -> Unit`
- Navigation wired in `GymLogNavigation`

## Data

Existing DAO methods are sufficient:
- `sessionDao.getSessionsForDate(date)` - get sessions for a date
- `sessionDao.getSetsForSession(sessionId)` - get all sets
- `sessionDao.getById(sessionId)` - get session details
- `exerciseDao.getById(exerciseId)` - get exercise names
- `templateDao.getById(templateId)` - get template name
