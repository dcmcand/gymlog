# Pebble Time 2 Interop Spike - Results

Spike plan: `docs/superpowers/plans/2026-07-10-pebble-time-2-interop-spike.md`
Feature spec: `docs/superpowers/specs/2026-07-10-pebble-time-2-watch-integration-design.md`
Run: 2026-07-10, on real hardware (Pebble Time 2, watch id `C114131106GG`; Android phone; Fedora dev box).

## Verdict: GO - build the watchapp in Alloy (JavaScript)

Bidirectional `AppMessage` between an **Alloy (JS) watchapp** and a **native third-party
Android app** via **PebbleKitAndroid2** is confirmed working on hardware. Both spec
unknowns are resolved. No fallback to C is needed.

## Setup used

- Toolchain: `uv tool install pebble-tool` (`pebble` v5.0.39) + `pebble sdk install latest` (SDK 4.17).
- Phone side: the official PebbleKitAndroid2 **sample** Android app (`io.rebble.pebblekit2.sample`, key `1`, UUID `0054f75d-e60a-4932-8f8d-fe5c7dd365f6`), built with its `jvmToolchain`/`compileOptions` bumped 11 -> 21 (no JDK 11 locally), installed via adb.
- Watch side: minimal Alloy watchapp scaffolded with `pebble new-project --alloy`, converted to a watchapp (`watchapp.watchface=false`), **`src/pkjs/` deleted**, UUID + `companionApp.android.apps[].package` mirrored to the sample, `messageKeys: {"MSG": 1}`, and a `pebble/message` + `pebble/button` + Poco embeddedjs. Built with `pebble build` and sideloaded by pushing the `.pbw` to `/sdcard/Download` and opening it with the Core app (`coredevices.coreapp`).

## Evidence (verbatim logcat)

Watch -> phone (SELECT press sent `MSG`=7 on wire key 1):
```
11:35:19.596 AppMessagePush(uuid=0054f75d-..., key=1, type=1, data=[7])   # inbound from watch, Core app
11:35:19.616 D/PebbleListenerService: Received {1=Int32(value=7)} from app 0054f75d-... on the watch C114131106GG
```

Phone -> watch (sample "send"):
```
11:35:14.641 sending AppMessagePush(uuid=0054f75d-..., key=1, type=1, data="Hello at 11:35:14...")
11:35:14.863 D/PebbleKitSample: Message sent. Result: {WatchIdentifier(value=C114131106GG)=Success}
```
Watch display observed: `Ready -> GOT -> SEND` across the two tests.

## Unknowns resolved

1. **No `pkjs` required (embeddedjs-only).** Inbound works immediately with `src/pkjs/`
   deleted. Outbound also works with no pkjs, and reaches the third-party Android app
   (not consumed by a pkjs). The native `pebble/message` layer gates outbound on the
   outbox being "writable", which becomes true after the **first inbound message** (see
   `messageReceived` setting `pkjsReady` in the SDK's `pebble-appmessage.c`). Practical
   consequence for the feature: **the watch can only send after it has received at least
   one message** - naturally satisfied because GymLog pushes workout context before the
   user would press Easy/Hard.

2. **Key alignment.** Passing `keys` to the `Message` constructor as an **array** maps
   names to wire integers `10000 + index` (a silent-mismatch trap). Passing a **`Map`
   with explicit integers** (`new Map([["MSG", 1]])`) sends on that exact wire integer.
   Confirmed: watch wrote `MSG`, sample received wire key `1`.

## Send pattern (for the full watchapp)

`Message.write()` must be called when the outbox is writable, and the `onWritable`
callback is effectively one-shot. The reliable pattern proven here: call
`message.write(new Map([...]))` **directly from the input handler** (button press),
wrapped in try/catch (the native call throws "not writable" if the outbox is not ready).
This works because an inbound message has already made the outbox writable.

## Implications for the full-feature plan

- Watchapp language: **Alloy**, embeddedjs-only, **no `pkjs`**.
- Protocol: pinned integer keys via `Map` form on the watch, matching hardcoded
  `UInt` keys in the GymLog `PebbleBridge` (PebbleKitAndroid2 `PebbleDictionaryItem`).
- Ordering invariant: GymLog must push at least one context/timer message to the watch
  before the watch's Easy/Hard commands can be sent. The bridge should push current
  context on workout start / app-open so the outbox is writable by the time a set is done.
- Toolchain + sideload flow is proven: `pebble build` + push `.pbw` + open with Core app.
  (The Wi-Fi/USB "Developer Connection" for `pebble install --phone` / `pebble logs` was
  flaky in this environment - "connection lost"; sideloading the `.pbw` is the reliable
  install path here, and `adb logcat` + the watch display are sufficient for verification.)
