# Pebble Time 2 Watch Integration - Rest Timer & Set Completion

Related hardware: Pebble Time 2 (PebbleOS, platform "Emery", Core Devices 2025 revival).

## Goal

Let the user see the rest timer and workout context on a paired Pebble Time 2, and
complete the current set (Easy/Hard) from the watch - without pulling the phone out
of a pocket. Must preserve GymLog's offline, no-INTERNET privacy stance.

## Scope (agreed)

Watch shows: live rest countdown, current exercise name, target weight x reps, and
set number. Watch buttons: **Easy** / **Hard** complete the current set, which starts
the next rest. Difficulty is captured because it feeds the app's weight-suggestion
logic.

## Approach & phasing

The watchapp is written in **Alloy (JavaScript)**, the modern Pebble Time 2 path.

Research verdict (cited sources in the brainstorming session): the AppMessage protocol
is transport-level (UUID + integer dictionary keys) and `PebbleKitAndroid2` is
watchapp-language-agnostic, so an Alloy watchapp receiving messages from a third-party
native Android app is **plausible but undocumented** - no official example shows a
native Android app driving an *Alloy* (vs C) watchapp. Two concrete unknowns:

1. Whether Alloy's `Message` inbox/outbox works with **no `pkjs`** (per Pebble's
   message-routing precedence, if `src/pkjs/index.js` exists the outbound message may
   be consumed by PebbleKit JS instead of being forwarded to the companion Android app).
2. Whether Alloy's string-named `messageKeys` map to the same integer keys that
   `PebbleKitAndroid2` sends on the wire (silent-break risk).

**Therefore Phase 0 is a throwaway interop spike** that must pass before the real
feature is built:

- Minimal **no-`pkjs`** Alloy watchapp (`emery` target): a `Message` that logs
  `this.read()` on `onReadable`, and on a button press does `this.write(...)`.
- Minimal Android app using `io.rebble.pebblekit2:client:1.2.0`: a
  `BasePebbleListenerService` that logs `onMessageReceived(uuid, data, watch)` and a
  button calling `DefaultPebbleSender().sendDataToPebble(UUID, ...)`.
- **Pass = both directions observed in logs**, with integer keys aligned.
- **Fallback:** if Alloy interop cannot be made to work, switch the watchapp to **C**
  (the documented, example-backed path). The entire phone-side design below is
  identical either way.

## Architecture

Three pieces.

### 1. Alloy watchapp (`pebble/` subdirectory)

- Displays: countdown, exercise name, target text ("60kg x 5"), set label ("Set 2 of 4").
- Runs its **own local countdown** from a duration sent once by the phone (not a
  per-second stream), re-syncing on start/extend/stop/finish messages.
- Two buttons -> Easy / Hard command messages.
- `package.json` declares `pebble.companionApp.android.apps[].package = com.gymlog.app`
  so the companion link is brokered to GymLog.
- No `src/pkjs/` (embeddedjs-only), per the routing-precedence finding.

### 2. `PebbleBridge` (new, in `:app`)

The only new phone-side integration surface.

- Uses `PebbleKitAndroid2` `PebbleSender` to push `{context, timer}` to the watchapp
  UUID whenever the active workout context or `RestTimerService.timerState` changes.
- A `BasePebbleListenerService` (declared in the manifest with the
  `io.rebble.pebblekit2.RECEIVE_DATA_FROM_WATCH` intent-filter) receives Easy/Hard
  commands and calls `ActiveWorkoutStore.completeCurrentSet(...)`.
- `sendDataToPebble()` is a `suspend` function; the bridge owns a small coroutine scope.

### 3. `ActiveWorkoutStore` (new shared singleton - the source of truth)

Today the rest timer is a singleton foreground service (good: the watch's timer display
sources from `RestTimerService.timerState`), but the workout **context + set-completion
logic live inside `ActiveWorkoutScreen`'s Compose state**, unreachable from a background
Pebble listener. The watch must complete a set while the Activity is not foregrounded.

`ActiveWorkoutStore` is a singleton (StateFlow, mirroring how `RestTimerService`
exposes timer state) that:

- Owns the in-progress session's sets as the source of truth (loaded from Room).
- Derives the **current set** and the watch context payload.
- Exposes `completeCurrentSet(status: Easy|Hard)` which writes the set to the DB
  (status + target reps) and starts the rest timer via `RestTimerService`.
- Is collected by **both** `ActiveWorkoutScreen` (UI) and `PebbleBridge`.

`ActiveWorkoutScreen` is refactored to read/mutate through this store instead of its
private `ActiveWorkoutState`. Side benefit: this fixes a latent bug where the screen's
in-memory state can drift from the DB (the current screen loads once and mutates
in-memory without observing the DB).

### Current-set model

The current set is the **first set with status `PENDING`**, scanning exercises in list
order, then sets within an exercise in order. Completing it advances to the next
`PENDING`. If none remain, the watch shows a "workout done / nothing pending" state.
Completing sets out of order on the phone simply changes which set is "first pending".

### Message protocol (integer keys, pinned on both sides)

Directly mitigates the key-alignment risk by fixing the integer keys in both the Alloy
`package.json` and the Android `PebbleDictionary` builder.

Phone -> watch:

| Key | Name | Type | Meaning |
|-----|------|------|---------|
| 0 | exerciseName | string | current exercise display name |
| 1 | targetText | string | e.g. "60kg x 5" |
| 2 | setLabel | string | e.g. "Set 2 of 4" |
| 3 | durationSec | uint | rest length to count down from (0 when idle) |
| 4 | running | uint8 | 1 = timer running, 0 = idle/done |

Watch -> phone:

| Key | Name | Type | Meaning |
|-----|------|------|---------|
| 10 | cmd | uint8 | 1 = complete Easy, 2 = complete Hard |

### Permissions & privacy

`io.rebble.pebblekit2:client:1.2.0` (Maven Central; library `minSdk 24`, compatible with
GymLog's `minSdk 31`) requires **no `INTERNET` and no Bluetooth permission** in GymLog -
it brokers all watch traffic through the official Pebble app via bound-service /
ContentProvider IPC. GymLog only declares a `<queries>` entry and a listener `<service>`.
The offline / no-internet privacy stance is fully preserved.

### Repo structure & toolchain

- Watchapp lives in a `pebble/` subdirectory of this repo (its own Alloy toolchain), so
  the message-key contract sits beside the Android code that depends on it.
- Android code stays in the `:app` module.
- User state: watch paired + official Pebble companion app installed; **Alloy/Pebble SDK
  toolchain not yet set up** - the plan includes toolchain installation as an explicit
  step.

## Journeys (definition of done)

Evidence cells are empty at spec time and filled only at the verification gate with
fresh, in-session evidence. Items are end-user capabilities and hold regardless of the
Alloy-vs-C outcome of Phase 0.

| # | Item | Proof | Check method | Evidence |
|---|------|-------|--------------|----------|
| 1 | Pebble shows the live rest countdown ticking in real time | During a rest, the watch seconds decrement ~1/s to 0, matching the phone within ~1s, even though the phone sends one duration message per rest (not a per-second stream) | narrated: start a rest; photograph/video the watch counting down; logcat/pkjs log shows a single duration message per rest, not per-second | *(empty)* |
| 2 | Pebble shows current exercise, target weight x reps, "Set X of Y" | Watch screen shows the active exercise name, target like "60kg x 5", and "Set 2 of 4" matching the phone for a known set | narrated: photograph the watch beside the phone for a known set | *(empty)* |
| 3 | Easy/Hard on the watch records the current set with that difficulty + target reps | After pressing Hard, the first-PENDING set in the DB has status=HARD and repsCompleted = its target | automated: `ActiveWorkoutStoreTest.completeCurrentSet` sets status/reps on the first PENDING set; + narrated: press Hard on the watch, confirm via phone history/logcat | *(empty)* |
| 4 | Watch completion starts the next rest timer on both devices | The press invokes `RestTimerService` (phone bottom bar/notification counts down) and the watch begins a fresh countdown | automated: store test asserts `RestTimerService.start` invoked on completion; + narrated: observe both devices start counting | *(empty)* |
| 5 | Watch completion works with the phone locked / app backgrounded | With screen off and GymLog not foreground, pressing Easy on the watch records the set via the listener service (no Activity) | narrated: lock phone, press watch; `adb logcat` shows the listener received the command and the DB updated; result visible on unlock | *(empty)* |
| 6 | Returning to the phone reflects the watch-made change | After a backgrounded watch completion, opening `ActiveWorkoutScreen` shows that set completed and the timer running, with no stale state | narrated: after item 5, foreground the app; screenshot showing completed set + running timer | *(empty)* |
| 7 | Completing a set on the phone updates the watch | Completing a set in the app advances the watch to the next set's context and restarts the watch countdown | narrated: complete a set on the phone; observe the watch update within ~1-2s | *(empty)* |
| 8 | All sets complete -> watch shows a done/idle state | With no PENDING set left, the watch shows "workout done"/nothing-pending, not the last set's stale data | narrated: complete the final set; observe the watch idle/done screen | *(empty)* |
| 9 | No active workout / watch absent -> app unaffected, no crash | Using GymLog with no workout active and/or no watch paired behaves exactly as before; Pebble code never crashes the app | automated: full `./gradlew test` green + build succeeds; + narrated: run the app end-to-end with the watch off, no crash (clean logcat) | *(empty)* |
| 10 | No INTERNET/Bluetooth permission added | The final merged `AndroidManifest` declares neither `INTERNET` nor any `BLUETOOTH*` permission | automated: a check asserting the merged manifest contains no INTERNET/BLUETOOTH permission | *(empty)* |

## Risks

- **Alloy interop unverified** (Phase 0 gate above). Mitigation: spike first; fall back to
  a C watchapp with an identical phone-side design.
- **Key alignment** between Alloy string keys and PebbleKitAndroid2 integer keys.
  Mitigation: pin integer keys on both sides; the spike verifies alignment.
- **`ActiveWorkoutStore` refactor** touches the most-used screen. Mitigation: the store
  is a focused extraction of existing load/complete logic; the screen keeps its current
  behavior; covered by unit tests on the store.
- **Companion-app dependency:** the feature requires the official Pebble app installed
  and the watch paired; degrade gracefully (journey 9) when absent.

## Out of scope (YAGNI)

Extend/skip/dismiss rest from the watch; difficulty beyond Easy/Hard; rep or weight
editing from the watch; exercise navigation from the watch; a Pebble watchface; making
history visually distinguish watch-completed vs phone-completed sets; configurable rest
duration.
