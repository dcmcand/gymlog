# Pebble Time 2 Interop Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This is the Phase 0 throwaway spike** from the spec
> (`docs/superpowers/specs/2026-07-10-pebble-time-2-watch-integration-design.md`). Its
> only purpose is a go/no-go on whether an **Alloy (JS) watchapp** can exchange
> `AppMessage`s bidirectionally with a native Android app via **PebbleKitAndroid2**. It
> ships no product code. The full-feature plan is written *after* this spike resolves the
> Alloy-vs-C question.

**Goal:** Prove or disprove, on real hardware, that a no-`pkjs` Alloy watchapp on a Pebble Time 2 can (a) receive an `AppMessage` sent by a native Android app via PebbleKitAndroid2 and (b) send one back to it, with pinned integer message keys aligned.

**Architecture:** Reuse the official `PebbleKitAndroid2` **sample Android app** (already proven against a C watchapp) as the phone side, and point a **minimal Alloy watchapp** at it (same UUID, same integer message keys, `companionApp.apps[].package` = the sample's applicationId). If the sample app can talk to the Alloy watchapp in both directions, Alloy interop is proven and the full feature proceeds in Alloy; otherwise we fall back to a C watchapp with an identical phone-side design.

**Tech Stack:** `pebble-tool` (Python/`uv`) + Pebble Alloy SDK (Moddable, Piu UI, `pebble/message`), targeting platform `emery`; PebbleKitAndroid2 `io.rebble.pebblekit2:client:1.2.0`; the official Pebble Android companion app (Developer Connection over Wi-Fi).

## Global Constraints

- Watchapp target platform: `emery` (Pebble Time 2). Verbatim: `"targetPlatforms": ["emery"]`.
- Message keys are **pinned to explicit integers** in the watchapp `package.json` object form (e.g. `{"CMD": 10}`); the Android side hardcodes the same integers. Never rely on array-form auto-assigned keys for this cross-build interop.
- Watchapp has **no `src/pkjs/`** for the primary test (embeddedjs-only), per the message-routing-precedence finding in the spec. A `pkjs` is added only as an isolation step if outbound fails.
- PebbleKitAndroid2 requires **no `INTERNET` and no Bluetooth permission** in the consuming app; it brokers through the official Pebble app. The spike must not add either permission.
- The Fedora dev machine and the phone must be on the **same LAN**; install/logs go through `--phone <SERVER_IP>` from the phone's Developer Connection.
- This spike's artifacts are **throwaway** and live **outside the gymlog repo** (sibling dirs); nothing here is committed to the gymlog repository.

## Journeys (from the spec - this spike GATES these; it advances none directly)

The spike delivers no end-user journey; it de-risks the feature that will deliver all of
them. Every task below is therefore `Advances journeys: none`. Block copied verbatim for
traceability:

| # | Item | Proof | Check method | Evidence |
|---|------|-------|--------------|----------|
| 1 | Pebble shows the live rest countdown ticking in real time | During a rest, the watch seconds decrement ~1/s to 0, matching the phone within ~1s, even though the phone sends one duration message per rest (not a per-second stream) | narrated | *(empty)* |
| 2 | Pebble shows current exercise, target weight x reps, "Set X of Y" | Watch screen shows the active exercise name, target like "60kg x 5", and "Set 2 of 4" matching the phone for a known set | narrated | *(empty)* |
| 3 | Easy/Hard on the watch records the current set with that difficulty + target reps | After pressing Hard, the first-PENDING set in the DB has status=HARD and repsCompleted = its target | automated + narrated | *(empty)* |
| 4 | Watch completion starts the next rest timer on both devices | The press invokes `RestTimerService` and the watch begins a fresh countdown | automated + narrated | *(empty)* |
| 5 | Watch completion works with the phone locked / app backgrounded | With screen off and GymLog not foreground, pressing Easy on the watch records the set via the listener service | narrated | *(empty)* |
| 6 | Returning to the phone reflects the watch-made change | Opening `ActiveWorkoutScreen` after a backgrounded watch completion shows the set completed + timer running, no stale state | narrated | *(empty)* |
| 7 | Completing a set on the phone updates the watch | Completing a set in the app advances the watch context and restarts the watch countdown | narrated | *(empty)* |
| 8 | All sets complete -> watch shows a done/idle state | With no PENDING set left, the watch shows a done/nothing-pending screen | narrated | *(empty)* |
| 9 | No active workout / watch absent -> app unaffected, no crash | GymLog behaves as before with no workout/watch; Pebble code never crashes it | automated + narrated | *(empty)* |
| 10 | No INTERNET/Bluetooth permission added | Merged `AndroidManifest` declares neither `INTERNET` nor any `BLUETOOTH*` permission | automated | *(empty)* |

## Prerequisites (confirm before Task 1)

- Pebble Time 2 paired with the official Pebble Android app (user confirmed).
- Phone and Fedora machine on the same Wi-Fi network.
- JDK 21 available (already required by gymlog) for the Android sample build.

---

### Task 1: Install and verify the Alloy/Pebble toolchain

**Files:** none in-repo (installs user/global tooling).

**Interfaces:**
- Produces: a working `pebble` CLI and an installed Alloy SDK, used by all later watchapp tasks.

`Advances journeys: none (rationale: toolchain setup for a throwaway spike).`

- [ ] **Step 1: Install `uv` (if not present)**

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
# re-source your shell profile or open a new terminal so `uv` is on PATH
uv --version
```
Expected: prints a `uv` version (e.g. `uv 0.x.y`).

- [ ] **Step 2: Install the Pebble CLI and SDK**

```bash
uv tool install pebble-tool
pebble sdk install latest
```
Expected: `pebble-tool` installs; `pebble sdk install latest` downloads and activates an SDK.

- [ ] **Step 3: Install SDK system dependencies (Fedora)**

The docs list Ubuntu deps (`libsdl2-2.0-0 libglib2.0-0 libpixman-1-0 zlib1g libsndio7.0` + `nodejs`). Fedora equivalents (best-known; not in official docs):

```bash
sudo dnf install -y SDL2 glib2 pixman zlib sndio nodejs
```
Expected: installs or reports already-present. If a later `pebble build`/emulator step fails with a missing shared library, install the specific Fedora package that provides it and note it in the spike results.

- [ ] **Step 4: Verify the toolchain**

```bash
pebble sdk list
```
Expected: lists at least one installed SDK version, with one marked active. (Note: a `pebble --version` flag is not documented in the 2026 CLI; `pebble sdk list` is the documented health check.)

---

### Task 2: Build and run the PebbleKitAndroid2 sample Android app; capture its UUID/keys/package

**Files:**
- Clone: `~/devel/android/PebbleKitAndroid2/` (throwaway sibling of the gymlog repo)

**Interfaces:**
- Produces: `SAMPLE_UUID` (the watchapp UUID the sample targets), `SAMPLE_KEYS` (the integer AppMessage keys it sends/expects), and `SAMPLE_APP_ID` (the sample's `applicationId`) - all consumed by Task 3.

`Advances journeys: none (rationale: throwaway phone side of the interop spike).`

- [ ] **Step 1: Clone the repo**

```bash
git clone https://github.com/pebble-dev/PebbleKitAndroid2.git ~/devel/android/PebbleKitAndroid2
```
Expected: clone succeeds.

- [ ] **Step 2: Record the sample's UUID, message keys, and applicationId**

```bash
# watchapp UUID + companionApp package the sample expects
cat ~/devel/android/PebbleKitAndroid2/sample/watch/package.json
# the integer AppMessage key(s) the sample C watchapp uses
grep -rn "app_message\|MESSAGE_KEY\|_key\|0x" ~/devel/android/PebbleKitAndroid2/sample/watch/src/c/
# the Android sample's applicationId and what keys it sends/receives
grep -rn "applicationId" ~/devel/android/PebbleKitAndroid2/sample/
grep -rn "sendDataToPebble\|PebbleDictionaryItem\|onMessageReceived\|UUID.fromString" ~/devel/android/PebbleKitAndroid2/sample/
```
Expected: note down `SAMPLE_UUID`, the integer key(s) (e.g. `1`), and `SAMPLE_APP_ID`. Record these in the spike results file (Task 6).

- [ ] **Step 3: Build and install the sample Android app to the phone**

```bash
cd ~/devel/android/PebbleKitAndroid2 && ./gradlew :sample:installDebug
```
Expected: `BUILD SUCCESSFUL`; the sample app appears on the phone. (If the module path differs from `:sample`, use the name shown by `./gradlew projects`.)

- [ ] **Step 4: Grant the companion link**

Open the sample app; use its built-in permission dialog (`PebbleAppPermissionDialog` / the sample's "grant" button) to authorize it against the official Pebble app.
Expected: the sample reports the watch as connected / permission granted.

---

### Task 3: Scaffold the minimal no-`pkjs` Alloy watchapp, mirroring the sample

**Files:**
- Create: `~/devel/android/pebble-spike/` (throwaway sibling dir) via `pebble new-project`
- Edit: `~/devel/android/pebble-spike/package.json`
- Replace: `~/devel/android/pebble-spike/src/embeddedjs/main.js`
- Delete: `~/devel/android/pebble-spike/src/pkjs/`

**Interfaces:**
- Consumes: `SAMPLE_UUID`, `SAMPLE_KEYS`, `SAMPLE_APP_ID` from Task 2.
- Produces: a `.pbw` that logs inbound dicts and sends a message on SELECT.

`Advances journeys: none (rationale: throwaway watch side of the interop spike).`

- [ ] **Step 1: Scaffold**

```bash
cd ~/devel/android && pebble new-project --alloy pebble-spike
```
Expected: creates `pebble-spike/` with `src/embeddedjs/main.js`, `src/pkjs/index.js`, `src/c/mdbl.c`, `resources/`, `package.json`.

- [ ] **Step 2: Remove pkjs (test the embeddedjs-only path)**

```bash
rm -rf ~/devel/android/pebble-spike/src/pkjs
```
Expected: dir removed. (If a later `pebble build` fails specifically due to the missing pkjs, recreate `src/pkjs/index.js` with an empty module `// intentionally empty` and set `enableMultiJS` accordingly - record which was needed in Task 6, since "does Message work with no pkjs" is one of the two unknowns being tested.)

- [ ] **Step 3: Write `package.json`**

Set `uuid` to `SAMPLE_UUID`, `messageKeys` to the integers from Task 2 (shown here assuming the sample uses key `1`; adjust to the actual value), and `companionApp.android.apps[].package` to `SAMPLE_APP_ID`.

```json
{
  "name": "pebble-spike",
  "author": "spike",
  "version": "1.0.0",
  "pebble": {
    "displayName": "Spike",
    "uuid": "PASTE_SAMPLE_UUID_HERE",
    "sdkVersion": "3",
    "enableMultiJS": true,
    "targetPlatforms": ["emery"],
    "watchapp": { "watchface": false },
    "messageKeys": { "MSG": 1 },
    "companionApp": {
      "android": { "apps": [ { "package": "PASTE_SAMPLE_APP_ID_HERE" } ] }
    },
    "resources": { "media": [] }
  }
}
```
Expected: valid JSON; `uuid` and `messageKeys` exactly match the sample's.

- [ ] **Step 4: Write `src/embeddedjs/main.js`**

```javascript
import {} from "piu/MC";
import Message from "pebble/message";
import Button from "pebble/button";

const label = new Label(null, {
	left: 0, right: 0, top: 0, bottom: 0,
	style: new Style({ font: "bold 24px Gothic", color: "black" }),
	string: "Spike ready"
});

const application = new Application(null, {
	skin: new Skin({ fill: "white" }),
	contents: [ label ]
});

let pending = null;

const message = new Message({
	keys: ["MSG"],                       // name -> pinned int 1 from package.json
	onReadable() {
		const msg = this.read();
		msg.forEach((value, key) => {
			console.log(`SPIKE inbox ${key}=${value}`);
		});
		label.string = "Got from phone";
	},
	onWritable() {
		if (!pending) return;
		this.write(pending);
		console.log("SPIKE sent to phone");
		pending = null;
	}
});

new Button({
	types: ["select"],
	onPush(down, type) {
		if (!down) return;
		pending = new Map([["MSG", 7]]);   // echo value 7 back to the phone
		label.string = "Sending...";
	}
});
```
Expected: file saved. (If the sample uses a different key name/integer, change `messageKeys` in package.json and the `keys`/`Map` names here to match.)

- [ ] **Step 5: Compile-check in the emulator**

```bash
cd ~/devel/android/pebble-spike && pebble build && pebble install --emulator emery
```
Expected: `pebble build` succeeds and the app launches in the `emery` emulator showing "Spike ready". (This confirms the watchapp compiles and the no-`pkjs` build is valid before touching hardware.)

---

### Task 4: Sideload the Alloy watchapp to the physical Pebble Time 2

**Files:** none.

**Interfaces:**
- Consumes: the built `.pbw` from Task 3; `SERVER_IP` from the phone's Developer Connection.

`Advances journeys: none (rationale: deploy step of the throwaway spike).`

- [ ] **Step 1: Enable Developer Connection on the phone**

In the official Pebble Android app: overflow menu (three dots) -> Settings -> enable **Developer Mode** -> tap **Developer Connection** -> enable the toggle -> note the **Server IP**.
Expected: a Server IP is shown (call it `SERVER_IP`).

- [ ] **Step 2: Install to the watch and stream logs**

```bash
cd ~/devel/android/pebble-spike && pebble install --phone SERVER_IP -v
```
Expected: the app installs and launches on the Pebble Time 2 (screen shows "Spike ready"); the CLI stays attached streaming logs. Keep this terminal open for Task 5. (If it cannot connect: confirm same-LAN, Developer Connection still on, and firewall not blocking.)

---

### Task 5: Go/no-go - verify both directions on hardware

**Files:** none.

**Interfaces:**
- Consumes: running sample app (Task 2), running watchapp + attached `pebble logs` (Task 4).

`Advances journeys: none (rationale: the spike's verification; gates the whole feature).`

- [ ] **Step 1: Phone -> watch**

In a second terminal, keep `pebble logs --phone SERVER_IP` running (or reuse Task 4's). Trigger the sample Android app's "send" action (its send button).
Expected (PASS): the watch log prints `SPIKE inbox MSG=<value>` and the watch shows "Got from phone". Record the exact log line.

- [ ] **Step 2: Watch -> phone**

Start `adb logcat` filtered for the sample app, then press the **SELECT** (right-middle) button on the Pebble.

```bash
adb logcat | grep -iE "pebblekit|onMessageReceived|MSG|received"
```
Expected (PASS): logcat shows the sample's `onMessageReceived` firing with the key/value (`MSG`=7). The watch shows "Sending..." then, if the sample replies, "Got from phone". Record the exact log line.

- [ ] **Step 3: Record raw results**

Capture both log excerpts (watch inbox line + phone `onMessageReceived` line) verbatim for Task 6. Do not interpret yet - just capture.

---

### Task 6: Decide and record the outcome

**Files:**
- Create: `~/devel/android/gymlog/docs/superpowers/specs/2026-07-10-pebble-interop-spike-results.md`

**Interfaces:**
- Consumes: log excerpts from Task 5.
- Produces: a recorded go/no-go that determines the watchapp language for the full-feature plan.

`Advances journeys: none (rationale: records the gate result; the full-feature plan and its journeys follow from it).`

- [ ] **Step 1: Classify the result**

- **Both directions passed** -> Alloy interop confirmed. Full feature proceeds in **Alloy**.
- **Phone->watch passed, watch->phone failed (no pkjs)** -> recreate `src/pkjs/index.js` (empty module), rebuild, reinstall, and retry Task 5 Step 2. If it then passes, Alloy is viable **but requires a pkjs shim** (record this constraint). If it still fails -> Alloy outbound to a third-party app is not viable.
- **Phone->watch failed** (watch never logs inbox) -> most likely UUID or companionApp package mismatch: re-check `SAMPLE_UUID`/`SAMPLE_APP_ID` alignment (Task 2 vs Task 3) and retry once. If still failing -> Alloy inbound is not viable.
- **Not viable in Alloy after the above** -> the full-feature plan uses a **C watchapp** (documented path; the PebbleKitAndroid2 sample already proves C works). Phone-side design is unchanged.

- [ ] **Step 2: Write the results doc**

Record, in the results file: the exact log excerpts, whether pkjs was required, the confirmed integer key mapping, and the decision (Alloy / Alloy+pkjs / C). This file is committed to the gymlog repo (it informs the next plan).

```bash
cd ~/devel/android/gymlog
git add docs/superpowers/specs/2026-07-10-pebble-interop-spike-results.md
git commit -m "Record Pebble Time 2 interop spike results (Alloy vs C decision)"
```
Expected: committed. (Ask before committing per repo commit conventions.)

- [ ] **Step 3: Clean up throwaway artifacts**

```bash
rm -rf ~/devel/android/pebble-spike ~/devel/android/PebbleKitAndroid2
```
Expected: throwaway spike dirs removed (their learnings live in the results doc). Keep the installed `pebble` toolchain - the full feature needs it.

---

## Self-review notes

- **Spec coverage:** This plan covers only Phase 0 (the interop spike) from the spec's "Approach & phasing". The remaining spec sections (watchapp UI, `PebbleBridge`, `ActiveWorkoutStore`, protocol, journeys 1-10) are deliberately deferred to the full-feature plan, which is written after Task 6 fixes the Alloy-vs-C decision.
- **The two spec unknowns are both exercised:** "Message works with no pkjs" (Task 3 Step 2 + Task 6 Step 1) and "integer key alignment" (Global Constraints + Task 3 Step 3, keys pinned and mirrored from the sample).
- **Uncertain commands are empirical, not placeholders:** Fedora deps (Task 1 Step 3) and the no-pkjs build (Task 3 Step 2) each carry an explicit "if it fails, do X" fallback rather than a guess.
