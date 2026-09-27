# F-Droid submission prep - design

Date: 2026-09-27
Status: draft for review

## Context

Sub-project 2 of 3 in the store plan (sub-project 1, the move to application ID
`io.github.dcmcand.gymlog` with data export/import, shipped as v1.5 and v2.0). The goal here is
to get every file F-Droid needs into shape so that submitting is a copy-paste once the account
question is settled. The official F-Droid repo takes submissions only as a GitLab merge request
to `fdroiddata`; the user has no GitLab account yet, so opening the MR is out of scope. The same
files also serve an IzzyOnDroid request (via Codeberg), which needs nothing extra.

## Decision: reproducible builds, developer-signed APK

F-Droid will build from source, verify its build matches the APK signed by us and attached to
the GitHub release, and publish **our** APK (F-Droid "Reproducible Builds": `Binaries` +
`AllowedAPKSigningKeys`). GitHub, F-Droid and IzzyOnDroid installs then share one signature and
can update each other, so no user has to reinstall and migrate again after the 2.0 move.

### Evidence gathered during design (2026-09-27)

- Two `VERSION_NAME=2.0 ./gradlew assembleRelease` builds of tag `v2.0` from different clone
  paths produced byte-identical unsigned APKs (same sha256).
- The CI-built, signed v2.0 GitHub APK equals the local unsigned build byte-for-byte outside the
  APK Signing Block: 147 entries in the same order, identical entry data and central directory.
  The build is already reproducible on Temurin JDK 21 (local 21.0.10, CI Temurin 21).
- The published v2.0 APK is signed with the v2 scheme only (minSdk 31), signer certificate
  SHA-256 `6574c5fd7658265792b342c53246205f743fdcbfa35ddf0255e0885e30b9e4b7`.
- Its signing block holds three entries: the v2 signature (0x7109871a), **DEPENDENCY_INFO**
  (0x504b4453, 8039 bytes) and verity padding. Per Android's docs the dependency info is
  "compressed, encrypted by a Google Play signing key, and stored in the signing block". With
  reproducible builds F-Droid ships our signing block unchanged, so this proprietary blob must
  not be in it. This is the only blocker found.
- Dependencies: the release runtime classpath contains only AndroidX, JetBrains/Kotlin, Guava,
  Okio, Touchlab (Kermit), Vico and PebbleKit2 (Apache-2.0), all from Google Maven or Maven
  Central. No Play Services, Firebase or other proprietary SDK (F-Droid Inclusion Policy).
- No anti-features apply: the watch integration is optional, and the companion apps PebbleKit2
  talks to (the Core app, GPLv3, and microPebble) are free software; `NonFreeDep` is for apps
  that "need a non-libre app to work".

## Changes

### 1. App repository, released as 2.0.1 (versionCode 20001)

- `app/build.gradle.kts`: add
  ```kotlin
  dependenciesInfo {
      includeInApk = false // Google-encrypted blob in the signing block; F-Droid/IzzyOnDroid reject it
  }
  ```
  `includeInBundle` is left at its default (on) for the later Google Play AAB.
- `fastlane/metadata/android/en-US/images/icon.png`: 512x512 PNG rendered from the adaptive
  launcher icon (foreground vector `drawable/ic_launcher_foreground.xml` on background
  `#1B5E20`), listed in F-Droid's Quick Start Guide. Rendered once with ImageMagick from an SVG
  transcription of the vector; the PNG is committed.
- `fastlane/metadata/android/en-US/short_description.txt`: remove the trailing period (the guide:
  "under 80 characters, no trailing period").
- `fastlane/metadata/android/en-US/changelogs/20001.txt`: short changelog (500 characters max).
- Version bump to 2.0.1 / 20001 and tag `v2.0.1` (the existing release process). The first
  F-Droid build is 2.0.1 because the v2.0 APK contains the dependency blob.

### 2. F-Droid metadata draft in this repo

`fdroid/io.github.dcmcand.gymlog.yml`, the file that will be copied to `metadata/` in
`fdroiddata`:

```yaml
Categories:
  - Workout
License: MIT
AuthorName: David McAndrew
SourceCode: https://github.com/dcmcand/gymlog
IssueTracker: https://github.com/dcmcand/gymlog/issues
Changelog: https://github.com/dcmcand/gymlog/releases

AutoName: GymLog

RepoType: git
Repo: https://github.com/dcmcand/gymlog.git
Binaries: https://github.com/dcmcand/gymlog/releases/download/v%v/app-release.apk

Builds:
  - versionName: 2.0.1
    versionCode: 20001
    commit: v2.0.1
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: 6574c5fd7658265792b342c53246205f743fdcbfa35ddf0255e0885e30b9e4b7

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 2.0.1
CurrentVersionCode: 20001
```

The exact layout is whatever `fdroid rewritemeta` produces; the file is committed in that
normalized form. No `AntiFeatures` key.

`fdroid/README.md`: how to submit (fork `fdroiddata` on GitLab, add the file as
`metadata/io.github.dcmcand.gymlog.yml`, commit "New App: io.github.dcmcand.gymlog", open the
MR), plus a short request text for IzzyOnDroid's Codeberg maintenance repo. Notes that
reproducibility depends on building releases with the same JDK major (21) and that the
`dependenciesInfo` switch must stay.

### 3. Out of scope

Creating GitLab/Codeberg accounts or opening the MR/request; Google Play (sub-project 3);
translations; the deferred minors from sub-project 1.

## Success criteria

1. `fdroid lint io.github.dcmcand.gymlog` reports nothing, and `fdroid rewritemeta` leaves the
   committed file unchanged.
2. `fdroid build io.github.dcmcand.gymlog:20001` succeeds inside F-Droid's buildserver container
   (`registry.gitlab.com/fdroid/fdroidserver:buildserver`, run with the local container
   runtime) against the pushed `v2.0.1` tag.
3. The unsigned APK from criterion 2 matches the signed v2.0.1 GitHub release APK outside the
   signing block (entries, order, entry data and central directory identical), i.e. F-Droid
   would publish our APK.
4. The v2.0.1 release APK's signing block contains only the v2 signature and padding: no
   DEPENDENCY_INFO entry.
5. `fastlane` metadata: `icon.png` is 512x512, `short_description.txt` is under 80 characters
   with no trailing period, `changelogs/20001.txt` is 500 characters or fewer.
6. Unit tests, lint and `assembleDebug` pass; the app behaves as 2.0 (no functional change).
