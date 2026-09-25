# Data export/import and app ID change - design

Date: 2026-09-25
Status: draft for review

## Context

GymLog is going to be published on F-Droid and Google Play. The current application ID,
`com.gymlog.app`, is already taken on Google Play by an unrelated app, so the ID has to change.
On Android a new application ID is a different app, and GymLog has no way to move data between
installs (`android:allowBackup="false"`, no internet, no export). Changing the ID today would
strand every user's workout history.

This spec is sub-project 1 of 3:

1. **Data export/import + app ID change** (this spec)
2. F-Droid submission (separate spec)
3. Google Play submission (separate spec)

## Goals

- Users can export all GymLog data to a single, human-readable JSON file and restore it.
- Export/import is also the long-term backup and phone-migration story for an offline app.
- The app moves to application ID `io.github.dcmcand.gymlog` without losing anyone's data.
- The Pebble watchapp keeps working through the transition.

## Non-goals

- Merging an import into existing data (import always replaces).
- CSV or spreadsheet export (possible later, separate feature).
- Automatic or scheduled backups, cloud sync.
- Renaming the Kotlin package / `namespace` (`com.gymlog.app` stays).
- Store listings, signing, AABs, privacy policy (sub-projects 2 and 3).

## Release sequence

Export has to ship under the old ID before the ID changes, so users can export from the app
they already have:

1. **v1.5** (still `com.gymlog.app`): adds Settings with Export and Import.
2. **v2.0** (`io.github.dcmcand.gymlog`): the ID change. Installs alongside the old app; the
   user exports from 1.5, imports into 2.0, checks their data, then uninstalls the old app.
   v2.0 is the build submitted to the stores.

## Design

### 1. App ID change (v2.0)

- `app/build.gradle.kts`: `applicationId = "io.github.dcmcand.gymlog"`. `namespace` stays
  `com.gymlog.app`, so no source moves and `BuildConfig`/`R` keep their package.
- `pebble/package.json` `companionApp.android.apps` lists both `com.gymlog.app` and
  `io.github.dcmcand.gymlog` so the watchapp talks to whichever is installed. The old entry is
  removed in a later release.
- Package-name audit (done during design): all intents are explicit
  (`Intent(context, RestTimerService::class.java)`, `Intent(context, MainActivity::class.java)`
  in `RestTimerNotification.kt`), there are no content providers, and the
  `com.gymlog.app.action.*` strings in `RestTimerService.kt` are in-app action names only. None
  depend on the application ID. The plan re-runs this audit before the change.
- `CLAUDE.md` is updated (app ID; also fixes its stale "Room version 6", which is now 7).

### 2. Export/import core (v1.5)

New package `com.gymlog.app.data.backup`, plain Kotlin with no Android UI code.

**Dependency:** kotlinx.serialization. Per the official README, the Gradle plugin
`org.jetbrains.kotlin.plugin.serialization` must match the Kotlin compiler version (2.2.10, the
existing `kotlin` catalog entry). The runtime `org.jetbrains.kotlinx:kotlinx-serialization-json`
is versioned separately; 1.9.0 is the release based on Kotlin 2.2 (1.10+ require Kotlin 2.3).
Chosen over `org.json` (stubbed in JVM unit tests, so untestable without Robolectric) and over
annotating Room entities (would couple the file format to the schema).

**`BackupModels.kt`:** `@Serializable` DTOs, separate from the Room entities:

```json
{
  "formatVersion": 1,
  "exportedAt": "2026-09-25T10:00:00Z",
  "appVersion": "1.5",
  "exercises": [ { "id": 1, "name": "bench press", "type": "WEIGHT", "cardioFixedDimension": null,
                   "fixedValue": null, "level": null, "distanceDisplayKm": false, "weightIncrementKg": 2.5 } ],
  "workouts": [ { "id": 1, "name": "Push" } ],
  "workoutExercises": [ { "id": 1, "workoutId": 1, "exerciseId": 1, "targetSets": 5, "targetReps": 8,
                          "targetWeightKg": 40.0, "targetDistanceM": null, "targetDurationSec": null, "sortOrder": 0 } ],
  "sessions": [ { "id": 1, "workoutId": 1, "date": "2026-09-23", "status": "COMPLETED",
                  "startedAt": "2026-09-23T17:00:00Z", "completedAt": "2026-09-23T18:00:00Z" } ],
  "sets": [ { "id": 1, "sessionId": 1, "exerciseId": 1, "setNumber": 1, "weightKg": 42.5,
              "repsCompleted": 8, "distanceM": null, "durationSec": null, "status": "EASY" } ]
}
```

- Enums are stored by name; `LocalDate` and `Instant` as ISO-8601 strings; IDs as-is.
- Mapping functions between entities and DTOs live next to the DTOs.

**`BackupCodec`:** `encode(data): String` (pretty-printed) and `decode(json): BackupData`.
Decoding validates before anything touches the database and fails with a typed error:

- not JSON, or JSON that isn't a GymLog backup -> `NotABackup`
- `formatVersion` greater than supported -> `NewerVersion`
- any dangling reference -> `Corrupt` (sets -> sessions and exercises; workoutExercises ->
  workouts and exercises; sessions -> workouts, where a null `workoutId` is valid)
- unknown JSON keys are ignored (`ignoreUnknownKeys = true`) so additive changes stay compatible
  within a format version.

**`BackupDao`** (new Room DAO): full-table reads for the five tables, delete-all, list inserts,
and `@Transaction suspend fun replaceAll(data)`: delete all rows, then insert parents before
children (exercises, workouts, workoutExercises, sessions, sets). Any failure rolls the whole
transaction back and the existing data is untouched. Inserting explicit IDs advances SQLite's
autoincrement past the highest imported ID, so later inserts don't collide.

**`BackupService`:** `export(...)` returns the JSON string; `import(...)` refuses with
`WorkoutInProgress` if this device has an in-progress session, otherwise decodes, validates and
calls `replaceAll`, then clears `ActiveWorkoutStore`. An in-progress session inside the file is
imported as-is and can be resumed.

### 3. Settings screen and UI (v1.5)

- `Screen.Settings("settings")`; a gear icon in the Calendar `TopAppBar` `actions`
  (`CalendarScreen.kt:95`) navigates to it. Pushed screen with a back arrow, not a bottom tab.
- `ui/settings/SettingsScreen.kt`, following the app's pattern (no ViewModel, DAOs via
  `GymLogDatabase.getDatabase(context)`):
  - **Export data:** `ActivityResultContracts.CreateDocument("application/json")` with suggested
    name `gymlog-backup-YYYY-MM-DD.json`; writes via `contentResolver`; snackbar
    "Exported N workouts".
  - **Import data:** `ActivityResultContracts.OpenDocument`; reads and validates the file, then
    shows a confirmation dialog: "Replace all data? This device's N workouts will be replaced by
    the file's M workouts. This can't be undone." Confirm runs the import; snackbar on success.
  - **Version:** `GymLog <BuildConfig.VERSION_NAME>`.
- Errors are plain-language snackbars and never modify data: not a GymLog backup; made by a newer
  GymLog (update first); backup is damaged; finish or discard your current workout first;
  couldn't read/write the file.
- No new permissions: the system document picker grants access to the chosen file only.
- Export/Import buttons are disabled with a progress indicator while working; database and file
  work runs off the main thread.

### 4. Testing

JUnit 4, table-driven, JVM only, per the repo's conventions:

- `BackupCodecTest`: round trip with every field type populated (all enums, null and non-null
  optionals, cardio, dates/instants); table-driven rejection cases (not JSON, not a backup,
  newer `formatVersion`, set -> missing session, set -> missing exercise, workoutExercise ->
  missing workout, session -> missing workout) plus null `workoutId` accepted; unknown keys
  ignored.
- Golden file `app/src/test/resources/backup-v1.json` must always decode (guards against
  accidental format changes).
- `BackupServiceTest`: import refused during an in-progress workout; `replaceAll` receives the
  decoded data; `ActiveWorkoutStore` cleared after import. Uses a proxy-based fake DAO, as in
  `ActiveWorkoutStoreTest`.
- Not unit-testable here (no instrumented tests): real Room transaction/rollback and the document
  pickers. Covered by the narrated journeys below.

## Journeys

| # | Item | Proof | Check method | Evidence |
|---|------|-------|--------------|----------|
| 1 | Open Settings from a gear on the Calendar tab; it shows the app version | Tapping the gear on Calendar opens Settings showing "GymLog 1.5" (v1.5 build) | narrated: on the phone, tap gear; capture `adb exec-out screencap` screenshot of Settings | |
| 2 | Export all data to a JSON file I choose, readable in a text editor | Export writes `gymlog-backup-<date>.json` to the chosen location (Downloads in the check); the pulled file is pretty-printed JSON with `formatVersion: 1` and table counts equal to the device DB's row counts | narrated: export on phone; `adb pull` the file; `jq` key/counts vs `sqlite3` `SELECT COUNT(*)` per table on the pulled DB | |
| 3 | Importing into a fresh install restores everything: exercises, workouts, sessions, sets, settings, history and progress charts | After import into a fresh install, every table's rows are identical to the source DB; history and a progress chart show the same data | automated: `BackupCodecTest` round trip + golden file; narrated: `sqlite3` dump of all 5 tables from source and destination DBs, `diff` is empty; screenshot of one progress chart on both installs | |
| 4 | Import asks before replacing; cancelling changes nothing | Picking a valid file shows the confirmation with both workout counts; Cancel leaves every table dump identical | narrated: screenshot of dialog; `sqlite3` table dump before and after Cancel, `diff` empty | |
| 5 | A bad file shows a clear error and changes nothing | Importing (a) a non-JSON file, (b) a backup with `formatVersion: 99`, (c) a backup with a set pointing at a missing session each shows its specific message; DB unchanged | automated: `BackupCodecTest` rejection table; narrated: push the three files, import each, screenshot each snackbar, `sqlite3` dump unchanged | |
| 6 | Cannot import during an in-progress workout, and I am told why | With a workout in progress, Import shows "Finish or discard your current workout first"; DB unchanged | automated: `BackupServiceTest` in-progress case; narrated: screenshot of the message | |
| 7 | A workout in progress at export time can be resumed after import | Export with an in-progress session, import into a fresh install, Calendar offers Resume and the active workout shows the same completed/pending sets | narrated: screenshots before export and after import/resume | |
| 8 | Renamed app installs alongside the old one and data moves across via export/import | `adb shell pm list packages` shows both `com.gymlog.app` and `io.github.dcmcand.gymlog`; after importing the 1.5 export into 2.0, all 5 tables diff clean between the two apps' DBs | narrated: `pm list packages` output; `sqlite3` dumps from both apps and empty `diff` | |
| 9 | Watchapp works with the renamed app (open mid-workout, Easy/Hard, skip, extend) | With only `io.github.dcmcand.gymlog` handling the watch, logcat shows context pushed on watchapp open, `10=1`/`10=2` completions recorded in the new app's DB, `10=4` cycling, `10=3` extending rest | narrated: decoded logcat transcript (same decoder used for 1.4) + `sqlite3` row showing the watch-completed set | |
| 10 | Rest timer notification opens the active workout in the renamed app | Completing a set starts the timer notification; tapping it opens the renamed app's active workout screen | narrated: screenshot of notification and of the screen it opens; `dumpsys activity` top activity shows `io.github.dcmcand.gymlog` | |
| 11 | No new permissions; still no internet | Merged manifest permissions for v1.5 and v2.0 equal 1.4's; no `INTERNET` | automated: existing `ManifestPermissionsTest`; narrated: `aapt2 dump permissions` on the 1.4, 1.5 and 2.0 APKs, compared | |
