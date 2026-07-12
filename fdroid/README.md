# F-Droid submission

GymLog qualifies for [F-Droid](https://f-droid.org): MIT-licensed, fully offline (no
`INTERNET` permission), no Google Play Services. F-Droid builds from source and, once
included, **auto-builds every new `v*` git tag** (pull-based - nothing is pushed to it).

`com.gymlog.app.yml` here is the metadata to submit. It is **not** used by this repo; copy
it into the F-Droid data repo via a merge request.

## One-time inclusion

1. Cut a release first so the referenced tag exists: bump `versionCode` in
   `app/build.gradle.kts`, commit, then `git tag v1.1 && git push --tags`.
2. Fork `https://gitlab.com/fdroid/fdroiddata` and create a branch.
3. Copy `com.gymlog.app.yml` to `metadata/com.gymlog.app.yml` in your fork.
4. Validate locally with `fdroidserver`:
   ```
   fdroid lint com.gymlog.app
   fdroid build com.gymlog.app
   ```
5. Open a merge request against `fdroiddata`.

## Each subsequent release (fully automatic once included)

Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`, commit, and push a
matching `v<versionName>` tag. `UpdateCheckMode: Tags` + `AutoUpdateMode: Version` make
F-Droid detect the new tag, read the new `versionCode` literal, and build + publish it.
No further merge requests needed.

Note: F-Droid signs the release with its own key (this repo's `signingConfig` is skipped
when `KEYSTORE_FILE` is unset), so F-Droid builds are a distinct signature from the Play/
GitHub-release APKs - expected and fine.
