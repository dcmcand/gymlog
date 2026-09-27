# F-Droid submission

GymLog is set up for F-Droid **reproducible builds**: F-Droid builds each tagged release from
source, checks the result matches the APK attached to the GitHub release, and then publishes
our signed APK. GitHub, F-Droid and IzzyOnDroid installs therefore share one signature and can
update each other.

## Official F-Droid (GitLab)

1. Fork https://gitlab.com/fdroid/fdroiddata and create a branch `io.github.dcmcand.gymlog`.
2. Copy `io.github.dcmcand.gymlog.yml` from this folder to `metadata/io.github.dcmcand.gymlog.yml`.
3. Commit with the message `New App: io.github.dcmcand.gymlog`, push, and open a merge request
   against fdroiddata `master`.
4. After merge, new versions are picked up automatically from `vX.Y[.Z]` tags
   (`UpdateCheckMode: Tags`, `AutoUpdateMode: Version`).

## IzzyOnDroid (Codeberg)

Open an issue at https://codeberg.org/IzzyOnDroid/repodata asking for inclusion:

> Please add GymLog (io.github.dcmcand.gymlog), a privacy-focused offline workout tracker
> (MIT, https://github.com/dcmcand/gymlog). Signed APKs are attached to every GitHub release
> as `app-release.apk`; fastlane metadata is in the repo. No trackers or proprietary libraries,
> and the APK carries no dependency-info block.

## Keeping releases reproducible

- Build releases with JDK 21 (CI uses Temurin 21).
- Keep `dependenciesInfo { includeInApk = false }` in `app/build.gradle.kts`; the release
  workflow fails if the signed APK's signing block contains anything but signatures.
- Keep `vcsInfo.include = false` on the release build type, so the APK doesn't depend on how
  the source was checked out (AGP otherwise embeds the git revision).
- `python3 scripts/apk_check.py compare <signed.apk> <unsigned.apk>` checks a local unsigned
  build against a published APK.
