# GymLog

A local-only Android app for tracking gym workouts. Define exercises, build workout templates, log your sessions with quick weight adjustments, and track progress over time.

## Features

- **Exercise Library** - Create weight and cardio exercises
- **Workout Templates** - Combine exercises into reusable workout plans with target sets, reps, and weight
- **Active Workout Tracking** - Log each set with tap-to-increment weight buttons (1.25, 2.5, 5, 10, 20 kg), mark sets as completed/partial/failed
- **Auto-fill Last Weight** - Remembers your previous session's weight for each exercise
- **Cardio Support** - Track distance and duration for rowing, running, etc.
- **Calendar View** - See which days you worked out at a glance
- **Progress Charts** - Line charts showing weight or distance progression per exercise
- **Fully Offline** - All data stored locally on device, no account required

## Screenshots

_Coming soon_

## Tech Stack

- Kotlin
- Jetpack Compose with Material 3
- Room database
- Compose Navigation
- Vico charts
- Min SDK 31 (Android 12+)

## Building

Requires JDK 21 and Android SDK with API 36.

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Testing

```bash
./gradlew test
```

## License

[MIT](LICENSE)
