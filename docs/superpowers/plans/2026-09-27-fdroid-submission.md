# F-Droid Submission Prep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Get every file F-Droid (and IzzyOnDroid) needs into shape, ship 2.0.1 without Google's dependency blob, and prove F-Droid's own tooling reproduces our signed GitHub APK.

**Architecture:** One build setting removes the DEPENDENCY_INFO block from APK signing blocks; a small Python tool (`scripts/apk_check.py`) guards that in CI and compares signed vs unsigned APKs. Store-listing files get fixed under `fastlane/`, and the F-Droid metadata is drafted in `fdroid/`. Verification runs the official `docker-executable-fdroidserver` image against the pushed `v2.0.1` tag.

**Tech Stack:** Gradle/AGP 9.0.1 Kotlin DSL, GitHub Actions, Python 3 (stdlib only), ImageMagick (one-off icon render), fdroidserver in Docker/Podman, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-27-fdroid-submission-design.md`

## Global Constraints

- Application ID `io.github.dcmcand.gymlog`; release signing certificate SHA-256 `6574c5fd7658265792b342c53246205f743fdcbfa35ddf0255e0885e30b9e4b7`.
- APKs must not carry DEPENDENCY_INFO: `dependenciesInfo { includeInApk = false }`. Leave `includeInBundle` at its default (on) for the later Play AAB.
- Release 2.0.1 = `versionCode = 20001`, `versionName` default `"2.0.1"`, tag `v2.0.1`.
- fastlane rules (F-Droid Quick Start Guide): `short_description.txt` under 80 characters, no trailing period; `changelogs/<versionCode>.txt` 500 characters max; `images/icon.png` present (512x512 here).
- F-Droid metadata: `Categories: [Workout]`, `License: MIT`, `AuthorName: David McAndrew`, `Binaries: https://github.com/dcmcand/gymlog/releases/download/v%v/app-release.apk`, `AutoUpdateMode: Version`, `UpdateCheckMode: Tags`, no `AntiFeatures`.
- Tests: JUnit 4, table-driven (`data class Case` + loop), backtick names; run with `./gradlew testDebugUnitTest`. Unit tests run with `app/` as the working directory (see `ManifestPermissionsTest`).
- No em dashes in code, comments, docs or copy. `CLAUDE.md` is gitignored and never committed.
- Pushing a tag publishes the GitHub release and the watchapp: always ask the user before merging to main or pushing a tag. Opening the fdroiddata MR / IzzyOnDroid request is out of scope (user has no account yet).

## Review Focus

1. A future release accidentally re-enables the dependency blob (e.g. someone deletes the `dependenciesInfo` block or AGP changes the default): the release must fail loudly, not publish an APK F-Droid will reject. Pinned by the `release.yml` guard step running `scripts/apk_check.py signing-block` (Task 1).
2. `apk_check.py signing-block` on an APK with no signing block at all (unsigned): must report that clearly and exit non-zero, not crash with a struct error. Pinned in Task 1 Step 3.
3. `apk_check.py compare` on two APKs that differ in one entry's contents, or in entry order: must exit non-zero and name the difference. Pinned in Task 1 Step 3.
4. A future changelog over 500 characters, or a short description regaining a trailing period: must fail CI before release. Pinned by `StoreMetadataTest` checking every file in `changelogs/` (Task 2).
5. The container build uses a JDK other than 21 and silently produces a different APK: must be detected, not waved through. Pinned by Task 5 Step 2 (check the JDK first, add a `sudo:` JDK 21 install to the recipe if needed) and Step 4 (compare).

---

## File Structure

| File | Responsibility |
|------|----------------|
| `app/build.gradle.kts` | `dependenciesInfo` switch (Task 1); version 2.0.1 (Task 4) |
| `scripts/apk_check.py` | `signing-block <apk>`: fail if any signing-block entry other than signatures/padding; `compare <signed> <unsigned>`: fail unless identical outside the signing block |
| `.github/workflows/release.yml` | Guard step after `assembleRelease` |
| `app/src/test/java/com/gymlog/app/StoreMetadataTest.kt` | Build flag + fastlane rules |
| `fastlane/metadata/android/en-US/images/icon.png`, `short_description.txt`, `changelogs/20001.txt` | Store listing |
| `fdroid/io.github.dcmcand.gymlog.yml` | fdroiddata metadata draft |
| `fdroid/README.md` | Submission steps (F-Droid, IzzyOnDroid) |

---

### Task 1: Drop the dependency blob and guard releases against it

**Advances:** spec success criteria 4 and 6 (and the tooling for 3)

**Files:**
- Modify: `app/build.gradle.kts` (inside `android { }`)
- Create: `scripts/apk_check.py`
- Modify: `.github/workflows/release.yml` (after the "Build release APK" step)
- Test: `app/src/test/java/com/gymlog/app/StoreMetadataTest.kt`

**Interfaces:**
- Produces: `python3 scripts/apk_check.py signing-block <apk>` (exit 0 = only signature/padding blocks; exit 1 with a message otherwise, including "no APK Signing Block"); `python3 scripts/apk_check.py compare <signed.apk> <unsigned.apk>` (exit 0 = identical outside the signing block; exit 1 naming the first difference). `StoreMetadataTest` class that Task 2 extends.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/gymlog/app/StoreMetadataTest.kt`:
```kotlin
package com.gymlog.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Store and F-Droid requirements that are easy to break by accident. Unit tests run with the
 * module dir (`app/`) as the working directory.
 */
class StoreMetadataTest {

    @Test
    fun `apks carry no google-encrypted dependency blob`() {
        // F-Droid ships our signing block as-is for reproducible builds, and rejects this blob.
        val gradle = File("build.gradle.kts").readText()
        assertTrue(Regex("""dependenciesInfo\s*\{[^}]*includeInApk\s*=\s*false""").containsMatchIn(gradle))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.gymlog.app.StoreMetadataTest"`
Expected: FAIL (assertion, no `dependenciesInfo` block yet).

- [ ] **Step 3: Write the APK tool and prove it on known APKs**

`scripts/apk_check.py`:
```python
#!/usr/bin/env python3
"""APK checks for F-Droid reproducible builds (stdlib only).

  apk_check.py signing-block APK      fail if the APK Signing Block holds anything but
                                      signatures and padding (e.g. AGP's DEPENDENCY_INFO)
  apk_check.py compare SIGNED UNSIGNED fail unless the two are identical outside the signing block
"""
import struct
import sys
import zipfile

ALLOWED = {
    0x7109871A: "APK Signature Scheme v2",
    0xF05368C0: "APK Signature Scheme v3",
    0x1B93AD61: "APK Signature Scheme v3.1",
    0x42726577: "verity padding",
}
KNOWN = {0x504B4453: "DEPENDENCY_INFO (Google-encrypted dependency metadata)", 0x6DFF800D: "source stamp"}
MAGIC = b"APK Sig Block 42"


def _eocd(data):
    end = data.rfind(b"PK\x05\x06")
    if end < 0:
        raise SystemExit("not a zip/APK")
    return struct.unpack("<I", data[end + 16:end + 20])[0], end


def _signing_block(data):
    """Returns (start, cd_offset) of the signing block, or None if there is none."""
    cd, _ = _eocd(data)
    if cd < 24 or data[cd - 16:cd] != MAGIC:
        return None
    size = struct.unpack("<Q", data[cd - 24:cd - 16])[0]
    return cd - (size + 8), cd


def signing_block(path):
    data = open(path, "rb").read()
    blk = _signing_block(data)
    if blk is None:
        print(f"{path}: no APK Signing Block (unsigned APK?)")
        return 1
    start, cd = blk
    p, end, bad = start + 8, cd - 24, []
    while p < end:
        length, block_id = struct.unpack("<QI", data[p:p + 12])
        name = ALLOWED.get(block_id) or KNOWN.get(block_id) or "unknown"
        print(f"  {block_id:#010x} {name} ({length - 4} bytes)")
        if block_id not in ALLOWED:
            bad.append(name)
        p += 8 + length
    if bad:
        print(f"{path}: FAIL, signing block contains: {', '.join(bad)}")
        return 1
    print(f"{path}: OK, signatures and padding only")
    return 0


def compare(signed, unsigned):
    zs, zu = zipfile.ZipFile(signed), zipfile.ZipFile(unsigned)
    ns, nu = [i.filename for i in zs.infolist()], [i.filename for i in zu.infolist()]
    if ns != nu:
        extra = sorted(set(ns) ^ set(nu))
        print(f"FAIL: entry lists differ (order or names); symmetric difference: {extra[:10]}")
        return 1
    for a, b in zip(zs.infolist(), zu.infolist()):
        if a.CRC != b.CRC or a.file_size != b.file_size:
            print(f"FAIL: contents differ: {a.filename}")
            return 1
    ds, du = open(signed, "rb").read(), open(unsigned, "rb").read()
    blk = _signing_block(ds)
    if blk is None:
        print(f"FAIL: {signed} has no signing block")
        return 1
    start, cd_s = blk
    cd_u, end_u = _eocd(du)
    _, end_s = _eocd(ds)
    if ds[:start] != du[:cd_u] or ds[cd_s:end_s] != du[cd_u:end_u]:
        print("FAIL: raw entry data or central directory differ")
        return 1
    print(f"OK: {len(ns)} entries identical outside the signing block")
    return 0


def main(argv):
    if len(argv) == 3 and argv[1] == "signing-block":
        return signing_block(argv[2])
    if len(argv) == 4 and argv[1] == "compare":
        return compare(argv[2], argv[3])
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
```

Prove each behavior (`S` = this session's scratch directory; `gh release download v2.0 -p app-release.apk -O $S/v2.0.apk`):
```bash
python3 scripts/apk_check.py signing-block $S/v2.0.apk; echo exit=$?
```
Expected: lists the v2 signature, DEPENDENCY_INFO and padding, prints `FAIL, signing block contains: DEPENDENCY_INFO ...`, exit=1.
```bash
git worktree add $S/v2.0-src v2.0
(cd $S/v2.0-src && VERSION_NAME=2.0 ./gradlew assembleRelease -q)   # no KEYSTORE_FILE, so unsigned
U=$S/v2.0-src/app/build/outputs/apk/release/app-release-unsigned.apk
python3 scripts/apk_check.py signing-block $U; echo exit=$?
python3 scripts/apk_check.py compare $S/v2.0.apk $U; echo exit=$?
git worktree remove --force $S/v2.0-src
```
Expected: signing-block prints `no APK Signing Block (unsigned APK?)`, exit=1; compare prints `OK: 147 entries identical outside the signing block`, exit=0.
Negative compare cases (Review Focus 3), before removing the worktree: make two copies of `$U` with Python `zipfile`, `$S/changed.apk` with a single entry's bytes changed (rewrite `META-INF/com/android/build/gradle/app-metadata.properties` with one extra character) and `$S/reordered.apk` with two entries swapped in order; `compare $S/v2.0.apk $S/changed.apk` and `compare $S/v2.0.apk $S/reordered.apk` must print `FAIL: contents differ: ...` / `FAIL: entry lists differ ...` and exit=1.

- [ ] **Step 4: Turn off the dependency blob**

`app/build.gradle.kts`, inside `android { }` after `buildFeatures { ... }`:
```kotlin
    dependenciesInfo {
        // AGP otherwise stores Google-encrypted dependency metadata in the APK signing block.
        // F-Droid publishes our signed APK as-is (reproducible builds) and rejects that blob.
        // The bundle keeps it (default) for Google Play.
        includeInApk = false
    }
```

- [ ] **Step 5: Verify with a signed local release**

Run the unit test: `./gradlew testDebugUnitTest --tests "com.gymlog.app.StoreMetadataTest"` → PASS.
Build a signed release with a throwaway key (never committed; scratch dir):
```bash
keytool -genkeypair -keystore $S/throwaway.jks -storepass throwaway -keypass throwaway -alias t -keyalg RSA -keysize 2048 -validity 1 -dname CN=throwaway
KEYSTORE_FILE=$S/throwaway.jks KEYSTORE_PASSWORD=throwaway KEY_ALIAS=t KEY_PASSWORD=throwaway ./gradlew assembleRelease -q
python3 scripts/apk_check.py signing-block app/build/outputs/apk/release/app-release.apk; echo exit=$?
```
Expected: only `APK Signature Scheme v2` and `verity padding` listed, `OK, signatures and padding only`, exit=0.

- [ ] **Step 6: Guard the release workflow**

`.github/workflows/release.yml`, a new step right after "Build release APK":
```yaml
      - name: Check APK signing block (F-Droid reproducible builds)
        run: python3 scripts/apk_check.py signing-block app/build/outputs/apk/release/app-release.apk
```

- [ ] **Step 7: Run everything and commit**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` → all pass.
```bash
git add app/build.gradle.kts scripts/apk_check.py .github/workflows/release.yml app/src/test/java/com/gymlog/app/StoreMetadataTest.kt
git commit -m "Keep Google's dependency blob out of APKs and guard releases against it"
```

---

### Task 2: Store listing files

**Advances:** spec success criterion 5

**Files:**
- Create: `fastlane/metadata/android/en-US/images/icon.png`
- Modify: `fastlane/metadata/android/en-US/short_description.txt`
- Test: `app/src/test/java/com/gymlog/app/StoreMetadataTest.kt` (add tests)

**Interfaces:**
- Consumes: `StoreMetadataTest` from Task 1.

- [ ] **Step 1: Write the failing tests**

Add to `StoreMetadataTest` (and `import java.io.DataInputStream`):
```kotlin
    private val listing = File("../fastlane/metadata/android/en-US")

    @Test
    fun `fastlane text follows the F-Droid limits`() {
        val short = File(listing, "short_description.txt").readText().trim()
        data class Case(val name: String, val ok: Boolean)
        val cases = mutableListOf(
            Case("short description under 80 characters (${short.length})", short.length < 80),
            Case("short description has no trailing period", !short.endsWith(".")),
            Case("title present", File(listing, "title.txt").readText().isNotBlank()),
        )
        File(listing, "changelogs").listFiles()!!.forEach { f ->
            val n = f.readText().trim().length
            cases += Case("changelog ${f.name} at most 500 characters ($n)", n <= 500)
        }
        for (c in cases) assertTrue(c.name, c.ok)
    }

    @Test
    fun `store icon is a 512 px square png`() {
        val icon = File(listing, "images/icon.png")
        assertTrue("icon.png exists", icon.exists())
        DataInputStream(icon.inputStream()).use { input ->
            val header = ByteArray(16).also { input.readFully(it) }
            assertTrue("PNG signature", header.copyOfRange(1, 4).decodeToString() == "PNG")
            val width = input.readInt()
            val height = input.readInt()
            assertTrue("512x512 (was ${width}x$height)", width == 512 && height == 512)
        }
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.gymlog.app.StoreMetadataTest"`
Expected: FAIL on "short description has no trailing period" and "icon.png exists".

- [ ] **Step 3: Fix the short description**

`short_description.txt` becomes (no trailing period, no trailing newline issues; keep a single final newline):
```text
Simple offline workout tracker. No accounts, no cloud, no tracking
```

- [ ] **Step 4: Render the icon**

Transcribe `app/src/main/res/drawable/ic_launcher_foreground.xml` into an SVG in a scratch dir: `viewBox="0 0 108 108"`, a full-size `<rect fill="#1B5E20"/>` background (color from `values/ic_launcher_background.xml`), then one `<path>` per vector `<path>` with the same `pathData` as `d`, and `strokeColor`/`strokeWidth`/`fillColor`/`strokeLineCap` mapped to `stroke`/`stroke-width`/`fill`/`stroke-linecap` (`#00000000` → `none`). Adaptive icons are cropped to the inner 72dp, so render the 18..90 region: `viewBox="18 18 72 72"`. Then:
```bash
magick -background none -density 1024 $S/icon.svg -resize 512x512 fastlane/metadata/android/en-US/images/icon.png
```
Look at the PNG (open it with the Read tool) and compare with the launcher icon on the phone: white dumbbell on dark green, not clipped.

- [ ] **Step 5: Run and commit**

Run: `./gradlew testDebugUnitTest` → all pass.
```bash
git add fastlane/metadata/android/en-US/images/icon.png fastlane/metadata/android/en-US/short_description.txt app/src/test/java/com/gymlog/app/StoreMetadataTest.kt
git commit -m "Add store icon and fix short description for F-Droid"
```

---

### Task 3: F-Droid metadata draft and submission notes

**Advances:** spec success criterion 1

**Files:**
- Create: `fdroid/io.github.dcmcand.gymlog.yml`
- Create: `fdroid/README.md`

**Interfaces:**
- Produces: the metadata file Task 5 builds from.

- [ ] **Step 1: Write the metadata**

`fdroid/io.github.dcmcand.gymlog.yml`:
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

- [ ] **Step 2: Lint and normalize it with the official tooling**

Scratch fdroiddata-style dir `$S/fdroiddata` with `metadata/` holding a copy of the file, then:
```bash
IMG=registry.gitlab.com/fdroid/docker-executable-fdroidserver:master
RUN="docker run --rm -u $(id -u):$(id -g) -v /home/chuck/Android:/opt/android-sdk -e ANDROID_HOME=/opt/android-sdk -v $S/fdroiddata:/repo $IMG"
$RUN init -v            # only if the image requires a config.yml; accept defaults
$RUN lint io.github.dcmcand.gymlog -v
$RUN rewritemeta io.github.dcmcand.gymlog -v
diff $S/fdroiddata/metadata/io.github.dcmcand.gymlog.yml fdroid/io.github.dcmcand.gymlog.yml
```
Expected: lint prints no warnings or errors. If rewritemeta changes the file, copy the normalized version back into `fdroid/` (that normalized form is what gets committed) and re-run lint. A lint complaint about the tag `v2.0.1` not existing yet is expected before Task 4; any other complaint is fixed here.

- [ ] **Step 3: Write the submission notes**

`fdroid/README.md`:
```markdown
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
- `python3 scripts/apk_check.py compare <signed.apk> <unsigned.apk>` checks a local unsigned
  build against a published APK.
```

- [ ] **Step 4: Commit**

```bash
git add fdroid/io.github.dcmcand.gymlog.yml fdroid/README.md
git commit -m "Add F-Droid metadata draft and submission notes"
```

---

### Task 4: Release 2.0.1

**Advances:** spec success criteria 4 and 6 (release side)

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 20001`, `versionName` default `"2.0.1"`)
- Create: `fastlane/metadata/android/en-US/changelogs/20001.txt`

- [ ] **Step 1: Bump the version and write the changelog**

`app/build.gradle.kts`: `versionCode = 20001`, `versionName = System.getenv("VERSION_NAME") ?: "2.0.1"`.

`fastlane/metadata/android/en-US/changelogs/20001.txt`:
```text
Preparing GymLog for F-Droid: release builds no longer embed Google's encrypted dependency metadata, and the store listing has an icon. No changes to how the app works.
```

- [ ] **Step 2: Run everything**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug` (StoreMetadataTest checks the new changelog length).
Expected: all pass.

- [ ] **Step 3: Commit, PR, and hand over for the release decision**

```bash
git add app/build.gradle.kts fastlane/metadata/android/en-US/changelogs/20001.txt docs/superpowers/plans/2026-09-27-fdroid-submission.md
git commit -m "Prepare 2.0.1 for F-Droid"
```
Run the whole-branch review (see the executing skill), then ask the user whether to push, open the PR, squash-merge and push tag `v2.0.1`. After the tag: watch `release.yml`; the new guard step must pass (success criterion 4, CI side) and the GitHub release must have `app-release.apk`.

---

### Task 5: Prove F-Droid reproduces the release

**Advances:** spec success criteria 2 and 3

**Files:**
- Modify (only if needed): `fdroid/io.github.dcmcand.gymlog.yml` (JDK `sudo:` lines)
- Modify: `docs/superpowers/plans/2026-09-27-fdroid-submission.md` (evidence)

- [ ] **Step 1: Download the published APK and check its signing block**

```bash
gh release download v2.0.1 -p app-release.apk -O $S/v2.0.1.apk
python3 scripts/apk_check.py signing-block $S/v2.0.1.apk
/home/chuck/Android/build-tools/36.0.0/apksigner verify --print-certs $S/v2.0.1.apk | grep SHA-256
```
Expected: `OK, signatures and padding only`; certificate SHA-256 equals the `AllowedAPKSigningKeys` value.

- [ ] **Step 2: Check the container's JDK**

Run: `docker run --rm --entrypoint java registry.gitlab.com/fdroid/docker-executable-fdroidserver:master -version`
If it is not 21, add to the build entry in both `$S/fdroiddata/metadata/...yml` and `fdroid/io.github.dcmcand.gymlog.yml`:
```yaml
    sudo:
      - apt-get update
      - apt-get install -y -t trixie openjdk-21-jdk-headless
      - update-java-alternatives -a
```
(adjust the package/suite to what the image's Debian release provides; `fdroid lint` must stay clean), and ledger the ruling.

- [ ] **Step 3: Build with fdroidserver**

With the metadata in `$S/fdroiddata/metadata/` and `$RUN` from Task 3:
```bash
$RUN build io.github.dcmcand.gymlog:20001 -v
ls $S/fdroiddata/unsigned/ $S/fdroiddata/tmp/ 2>/dev/null
```
Expected: build succeeds; an unsigned `io.github.dcmcand.gymlog_20001.apk` lands in `unsigned/` (or the build log names where). If fdroidserver itself downloads `Binaries` and reports the reproducibility result, record that output.

- [ ] **Step 4: Compare**

Run: `python3 scripts/apk_check.py compare $S/v2.0.1.apk $S/fdroiddata/unsigned/io.github.dcmcand.gymlog_20001.apk`
Expected: `OK: <N> entries identical outside the signing block`, exit 0. If it fails, use the named difference to find the cause (systematic-debugging), fix it in the build config, and repeat from a new patch release; do not weaken the comparison.

- [ ] **Step 5: Record evidence and commit**

Add an "Evidence" section to the end of this plan with the outputs of Steps 1-4 and the Task 3 lint result, then:
```bash
git add docs/superpowers/plans/2026-09-27-fdroid-submission.md fdroid/io.github.dcmcand.gymlog.yml
git commit -m "Record F-Droid reproducibility evidence for 2.0.1"
```
Ask the user before pushing this commit (docs-only; can go to main via a small PR).
