# Google Play submission prep - design

Date: 2026-09-27
Status: draft, updated 2026-10-02 after the Play developer account was created (personal, identity
verified); awaiting user review, then writing-plans

## Context

Sub-project 3 of 3 in the store plan (1: new application ID + export/import, shipped in 1.5/2.0;
2: F-Droid reproducible builds, shipped in 2.0.1 and verified with fdroidserver). The goal is to
have everything Google Play needs ready, so the closed test and the production application are
the only remaining steps. The user has a **personal** Play developer account with a verified
identity (2026-10-02), so the closed-test requirement below applies.

Out of scope: creating the Play account and paying the fee, recruiting testers, filling in the
Console forms (the user does this; we provide the answers), and automated Play uploads (needs a
Play API service account; possible later).

## Decisions

- **Signing:** Play App Signing with a **Google-generated app signing key**. Play installs will
  have a different signature from GitHub/F-Droid installs; switching between them means export,
  uninstall, install, import. Accepted by the user.
- **Upload key:** the existing release key (already in CI secrets) signs the AAB. Google only ever
  receives its public certificate (from the first upload), and it is never used to sign what
  Play users install.
- **AAB delivery:** CI builds a signed `app-release.aab` on every release tag and publishes it as
  a workflow artifact, not a release asset, so the public GitHub release stays APK-only.
- **Rest timer foreground service stays `specialUse`.** `shortService` is limited to "about 3
  minutes" (Android FGS types docs) and an extended rest exceeds that. Play reviews the
  free-form `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`, so it must explain the use.
- **Privacy policy:** required. Play Console Help (Data safety): "Even developers with apps that do
  not collect any user data must complete this form and provide a link to their privacy policy."
- **Closed test is the long pole.** 14 days of 12+ continuously opted-in testers, then a review of
  up to about 7 days. The code changes are small, so 2.1.0 ships as one release, as soon as
  possible. The user recruits testers and registers the package name (below) in parallel.
- **Package name registration (Android developer verification):** done by the user in Play Console
  with the existing release certificate. The ownership-proof APK is a one-off local build that is
  uploaded to the Console only. The token file is never committed, so the published APKs and
  F-Droid's reproducible build are unaffected.

## Requirements verified during design (2026-09-27)

- Target API: from August 31, 2026 new apps and updates must target API 36; the app targets 36.
- Store listing (Play Console Help "Add preview assets"): icon "32-bit PNG (with alpha)",
  512x512, max 1024 KB; feature graphic "JPEG or 24-bit PNG (no alpha)", 1024x500, mandatory;
  screenshots "JPEG or 24-bit PNG (no alpha)", 320-3840 px, "the maximum dimension of your
  screenshot can't be more than twice as long as the minimum dimension", 2 to 8 per device type;
  short description 80 characters.
- Current assets: icon is 16-bit RGBA (must become 8-bit per channel RGBA); screenshots are
  1080x2424 RGBA (2.24:1 and alpha: both fail); no feature graphic.
- New personal accounts (created after November 13, 2023) must run a closed test with at least 12
  testers opted in continuously for 14 days, then "Apply for production" (review "typically
  takes seven days or less"). Organization accounts are exempt.
- All apps must complete the Health apps declaration in App content.
- Privacy policy URL is required for every app, including apps that collect no data (Play Console
  Help, Data safety section).

Verified 2026-10-02 (Play Console Help "Registering Android package names", "Adding additional
keys"; developer.android.com "Android developer verification"):

- Android developer verification: apps must be registered to a verified developer to install on
  certified devices. It started in Brazil, Indonesia, Singapore and Thailand (deadline September 30,
  2026) and will expand. This covers the GitHub and F-Droid APKs too, not only Play installs.
- Registration is a separate step: creating a Play app "doesn't automatically trigger
  registration". The user goes to Android developer verification > Register package name.
- `io.github.dcmcand.gymlog` has been seen on Android before (GitHub and F-Droid installs), so
  ownership proof is required: the SHA-256 of the signing certificate, plus a release APK signed
  with that key that contains `assets/adi-registration.properties` holding a snippet tied to the
  developer account. A rationale is required only if the certificate is not listed as eligible
  (install thresholds).
- More signing keys can be added to a registered package ("Adding additional keys"). This covers
  the Play app signing key alongside the release key.

## Changes

### 0. Package name registration (user, with one helper script)

- The user registers `io.github.dcmcand.gymlog` under Android developer verification with the
  release certificate (SHA-256 `6574c5fd7658265792b342c53246205f743fdcbfa35ddf0255e0885e30b9e4b7`).
- `scripts/build_ownership_proof.sh SNIPPET_FILE` (needs the same keystore env vars as a release
  build): copies the snippet to `app/src/main/assets/adi-registration.properties`, runs
  `assembleRelease`, copies the APK to `build/ownership-proof.apk`, and deletes the asset file
  even if the build fails (`trap`). `.gitignore` lists the asset path so it can never be committed.
- Once Play App Signing exists (first AAB upload), the user adds the Play app signing certificate
  as a further key if the Console does not do this itself.

### 1. Build and CI

- `.github/workflows/release.yml`, job `release`, after the APK build and its signing-block
  check: `./gradlew bundleRelease` with the same keystore env vars, then
  `actions/upload-artifact` with name `app-release-aab` and path
  `app/build/outputs/bundle/release/app-release.aab`. The GitHub release step is unchanged
  (APK only).
- `app/build.gradle.kts`: no signing change (the existing release signing config signs both APK
  and AAB). `dependenciesInfo` keeps `includeInApk = false` and leaves `includeInBundle` at its
  default (on) for Play.

### 2. Manifest

`RestTimerService` gains:

```xml
<property
    android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    android:value="Rest timer between gym sets: a countdown the user starts by completing a set, shown as an ongoing notification (and on a paired watch) until the rest ends or the user stops or extends it." />
```

### 3. Privacy policy

- `PRIVACY.md` at the repo root, plain language: no accounts; no internet permission; no data
  collected, shared or sold; all data stays on the device; export files go only where the user
  saves them; the optional Pebble integration talks to the Pebble app on the same phone over the
  system's app-to-app channel (the watch connection itself is the Pebble app's); no analytics or
  ads; contact via GitHub issues; effective date.
- Public URL: `https://github.com/dcmcand/gymlog/blob/main/PRIVACY.md`.
- Settings screen: a "Privacy policy" row that opens that URL with `Intent.ACTION_VIEW`
  (no permission needed). If no app can open it (`ActivityNotFoundException`), show a snackbar
  with the URL instead of crashing. The URL lives in one constant.

### 4. Store listing assets (`fastlane/metadata/android/en-US/images/`)

- `featureGraphic.png`: 1024x500, 24-bit PNG, no alpha: the app's green (`#1B5E20`), the white
  dumbbell from the launcher icon, "GymLog" and a short tagline in white.
- `icon.png`: re-saved as 8-bit per channel RGBA, 512x512, under 1024 KB.
- `phoneScreenshots/0[1-4].png`: cropped to 1080x2160 (removing status bar and navigation areas,
  not app content) and saved as 24-bit PNG without alpha. Same files serve F-Droid.

### 5. Play Console answers

`docs/play-console.md` with ready answers: app name, category (Health & Fitness), contact
details (user fills email), store listing text (from fastlane), privacy policy URL, ads (none),
app access (no login), content rating questionnaire answers, target audience (18+, not designed
for children), Data safety (no data collected or shared; data stays on device; no encryption in
transit applicable), Health apps declaration (fitness/workout tracking, on-device only, no health
data shared, no Health Connect), foreground service declaration for `specialUse` with the same
justification and a note that a demo video may be requested, and the closed-test and
production-access steps (12 testers, 14 consecutive days, the three application sections).

### 6. Release

Ships as 2.1.0 (versionCode 20100; the Settings row is a user-visible addition) with a changelog.

## Journeys

| # | Item | Proof | Check method | Evidence |
|---|------|-------|--------------|----------|
| 1 | A release tag produces a signed AAB as a workflow artifact; the GitHub release stays APK-only | The `v2.1.0` release run has an `app-release-aab` artifact containing `app-release.aab`; `gh release view v2.1.0` lists only `app-release.apk` | narrated: `gh run view` artifact list + `gh release view --json assets` | |
| 2 | The AAB is signed with the existing key (as upload key) and is the same app as the APK | `jarsigner -verify -verbose -certs` on the AAB shows the certificate with SHA-256 `6574c5fd7658265792b342c53246205f743fdcbfa35ddf0255e0885e30b9e4b7`; a universal APK from `bundletool build-apks --mode=universal` has the same package, versionCode, versionName and permissions as the release APK | narrated: jarsigner/keytool output + `aapt2 dump badging` and `aapt2 dump permissions` diff | |
| 3a | The AAB is valid and carries the declarations Play checks | `bundletool validate` passes; `bundletool dump manifest` shows targetSdk 36 and the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property on `RestTimerService` | automated: `StoreMetadataTest` pins the manifest property; narrated: bundletool output | |
| 3b | Play Console accepts the AAB on a closed-test track | Upload succeeds with no errors or policy blocks on the FGS declaration or target API | narrated: user's Console screenshot after upload; open until the Play account exists | |
| 4 | The F-Droid/GitHub APK is unchanged where it matters | The v2.1.0 release APK's signing block has only signature and padding (`apk_check.py signing-block`), and a local unsigned build of `v2.1.0` matches it (`apk_check.py compare`) | automated: release.yml guard + `StoreMetadataTest`; narrated: compare output | |
| 5 | A privacy policy is public, and Settings links to it | `PRIVACY.md` renders at `https://github.com/dcmcand/gymlog/blob/main/PRIVACY.md`; tapping "Privacy policy" in Settings opens that URL in the browser | automated: test pins the URL constant to the repo file path; narrated: adb tap + `dumpsys activity` showing the browser with the URL | |
| 6 | The rest timer works as before | Completing a set starts the timer notification, which counts down and ends normally; no crash | narrated: on-device run, notification screenshot + logcat with no FATAL | |
| 7 | Listing assets meet Play's rules and F-Droid still accepts them | featureGraphic 1024x500 no alpha; icon 512x512 8-bit RGBA under 1024 KB; 2-8 screenshots, long side at most twice the short, no alpha; `fdroid lint` still clean | automated: `StoreMetadataTest` checks sizes and alpha; narrated: `fdroid lint` output | |
| 8 | `docs/play-console.md` answers every Console form | Sections for Health apps, Data safety, content rating, target audience, ads, category, `specialUse` justification, closed test and production steps, each with concrete answers | narrated: walk the doc against the Console's App content list | |
| 9 | No new permissions; still no internet | Release APK permissions equal v2.0.2's; no `INTERNET` | automated: `ManifestPermissionsTest`; narrated: `aapt2 dump permissions` diff | |
| 10 | The package name is registered to the user's verified developer account with the release key | Play Console's Android developer verification page lists `io.github.dcmcand.gymlog` as registered with certificate `6574c5fd...b7`; `git status` after `build_ownership_proof.sh` shows no `adi-registration.properties` | narrated: user's Console screenshot; `git status` output | |
