# Google Play Console: answers and steps

Ready answers for every Play Console form GymLog needs, plus the account-side steps. Checked
against Play Console Help on 2026-10-02; the Console wording wins if it differs.

Sources:
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Health apps declaration: https://support.google.com/googleplay/android-developer/answer/14738291
- Foreground service declaration: https://support.google.com/googleplay/android-developer/answer/13392821
- Testing requirements (personal accounts): https://support.google.com/googleplay/android-developer/answer/14151465
- Package name registration: https://support.google.com/googleplay/android-developer/answer/16761053
  and extra keys: https://support.google.com/googleplay/android-developer/answer/16762301

Package: `io.github.dcmcand.gymlog`. Upload key certificate SHA-256:
`6574c5fd7658265792b342c53246205f743fdcbfa35ddf0255e0885e30b9e4b7`.

## Order of work

1. **Register the package name** (Android developer verification). Do this first; it also
   covers the GitHub and F-Droid APKs.
2. **Create the app** and fill in App content and the store listing (below).
3. **Recruit 12+ testers** (personal account: "a minimum of 12 testers who have been opted in
   continuously for at least 14 days").
4. **Upload the 2.1.0 AAB** to a closed testing track and send testers the opt-in link.
5. **After 14 days** with 12+ testers opted in the whole time: Dashboard > Apply for production.
   Review "usually takes seven days or less".
6. **Production release** with the same AAB (or a newer one).

## 1. Package name registration

1. Play Console > Android developer verification > Register package name >
   `io.github.dcmcand.gymlog`. (Creating the Play app "doesn't automatically trigger
   registration".)
2. Give the SHA-256 above as the certificate.
3. The package has been installed before (GitHub, F-Droid), so the Console asks for ownership
   proof and shows a snippet for `adi-registration.properties`. Save it to a file exactly as
   shown (no extra lines), outside the repo, e.g. `~/gymlog-adi-snippet.txt`.
4. Build the proof APK with the release keystore:
   `KEYSTORE_FILE=... KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... scripts/build_ownership_proof.sh ~/gymlog-adi-snippet.txt`
   The script prints the snippet it put in the APK; check it matches the Console.
5. Upload `build/ownership-proof.apk`. Do not publish it anywhere.
6. If the Console asks for a rationale: "This package name has been distributed since 2026 on
   GitHub Releases and F-Droid (reproducible builds signed with this key). Changing it would
   force every existing user to reinstall and migrate their data."
7. `git status` must show no `adi-registration.properties` (the script removes it).
8. After the first AAB upload creates the Play app signing key: if the registration page does not
   list it already, add its SHA-256 (Test and release > App integrity > App signing) as an
   additional key.

## 2. Create app

- App name: GymLog. Default language: English (United States). App or game: App. Free or paid:
  Free.
- Declarations: Developer Program Policies and US export laws: accept.

## 3. App content

| Form | Answer |
|------|--------|
| Privacy policy | https://github.com/dcmcand/gymlog/blob/main/PRIVACY.md |
| App access | All functionality is available without any special access (no login). |
| Ads | No, the app does not contain ads. |
| Content rating | IARC questionnaire: pick the non-game category for utility/other apps (not a game, not social, not news). Answer No to violence, sexual content, profanity, controlled substances, gambling, user-generated content, user-to-user interaction, sharing location, and digital purchases. Expected rating: Everyone / PEGI 3. |
| Target audience and content | 18 and over only. Not designed to appeal to children. |
| News app | No. |
| Data safety | Does the app collect or share any of the required user data types: **No**. Play: "Collect" means "transmitting data from your app off a user's device", and data "only processed locally on the user's device and not sent off device does not need to be disclosed". The export file is written only where the user chooses and is never sent by the app. Pebble: Play counts an "On-device transfer to another app" as sharing, except a transfer "based on a specific user-initiated action, where the user reasonably expects the data to be shared". GymLog sends the current set and rest timer to the Pebble app only after the user turns on "Pebble watch" in Settings (off by default, explained next to the switch), so that exception applies. |
| Advertising ID | No, the app does not use an advertising ID. |
| Government app | No. |
| Financial features | None. |
| Health apps | Feature: **Activity and Fitness** (records exercise routines and workouts). No medical features. No Health Connect. Health data stays on the device and is not shared. |
| Foreground service permissions | Type: **Special use**. Description: the subtype text below. User impact if deferred or interrupted: the rest countdown would stop or be delayed while the screen is off, so the user would miss the end of their rest between sets. Video link: required. Record the emulator or phone screen while completing a set, showing the ongoing countdown notification and the rest ending; upload it to YouTube as unlisted and paste the link. |

Special use subtype (same text as the manifest property):

> Rest timer between gym sets: a countdown the user starts by completing a set, shown as an ongoing notification (and on a paired watch) until the rest ends or the user stops or extends it.

Why not `shortService`: Android limits it to "about 3 minutes", and a rest (especially after
the user extends it) can run longer.

## 4. Store listing

- Category: Health & Fitness. Tags: pick the closest offered (e.g. workout, fitness tracking).
- Contact email: <user fills in>. Website: https://github.com/dcmcand/gymlog
- Short description: `fastlane/metadata/android/en-US/short_description.txt`
- Full description: `fastlane/metadata/android/en-US/full_description.txt`
- App icon: `fastlane/metadata/android/en-US/images/icon.png`
- Feature graphic: `fastlane/metadata/android/en-US/images/featureGraphic.png`
- Phone screenshots: `fastlane/metadata/android/en-US/images/phoneScreenshots/01.png` to `04.png`

## 5. Closed test

- Test and release > Testing > Closed testing > create a track (e.g. "Closed testers").
  Countries: all.
- Testers: an email list or a Google Group of 12+ people with Google accounts; give them the
  opt-in link, and they install from Play. They must stay opted in for 14 days in a row:
  "Testers who opt in, test for fewer than 14 days, and then opt out do not count".
- Release: upload `app-release.aab` from the `v2.1.0` release run's `app-release-aab` artifact
  (`gh run download <run-id> -n app-release-aab`; artifacts expire after 90 days). Accept Play
  App Signing with a Google-generated key (the default).
- Release notes: `fastlane/metadata/android/en-US/changelogs/20100.txt`.
- Play-installed GymLog has a different signature from GitHub/F-Droid installs. Switching means
  Export data, uninstall, install from Play, Import data. Testers already on the GitHub/F-Droid
  build need to do this.

## 6. Apply for production

Dashboard > Apply for production. Draft answers:

- **About your closed test:** how testers were recruited (<user fills in>), how they used it
  (logged real workouts, rest timer between sets, backup export/import), feedback received and
  what changed (<user fills in from the 14 days>).
- **About your app:** a free, offline workout log for people who lift: log sets and weights,
  get a weight suggestion from past sessions, rest timer between sets, progress charts,
  optional Pebble watch app. No accounts, ads or internet access. Intended for adults who train
  in a gym.
- **Production readiness:** what changed during testing (<user fills in>), how you know it is
  ready (testers' workouts logged without crashes, Play pre-launch report clean).
