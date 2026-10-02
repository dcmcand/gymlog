# Google Play Submission Prep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship GymLog 2.1.0 with everything Google Play needs (signed AAB from CI, `specialUse` FGS declaration, privacy policy linked from Settings, compliant listing assets, ready Console answers), plus a helper that builds the package-ownership proof APK for Android developer verification.

**Architecture:** The release workflow gains a `bundleRelease` step whose AAB goes to a workflow artifact, not the GitHub release. The manifest declares the FGS subtype. A one-off shell script injects the Console's ownership snippet into a release APK and always removes it again. Listing assets are fixed in `fastlane/` (shared with F-Droid) and pinned by `StoreMetadataTest`. A privacy policy lives at the repo root and Settings links to it.

**Tech Stack:** Gradle/AGP 9.0.1 Kotlin DSL, Jetpack Compose Material 3, GitHub Actions (`actions/upload-artifact@v7`), bundletool 1.18.3, Python 3 (stdlib for scripts and tests, Pillow 12 for the one-off feature graphic), bash, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-27-play-store-prep-design.md`

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

## Global Constraints

- Application ID `io.github.dcmcand.gymlog`; release (upload) certificate SHA-256 `6574c5fd7658265792b342c53246205f743fdcbfa35ddf0255e0885e30b9e4b7`.
- Release 2.1.0 = `versionCode = 20100`, `versionName = "2.1.0"` (plain literals), tag `v2.1.0`, changelog `fastlane/metadata/android/en-US/changelogs/20100.txt` (500 characters max).
- The GitHub release stays APK-only. The AAB is a workflow artifact named `app-release-aab` with path `app/build/outputs/bundle/release/app-release.aab`.
- `dependenciesInfo { includeInApk = false }` stays; `includeInBundle` stays at its default (on).
- `RestTimerService` stays `android:foregroundServiceType="specialUse"` and gains the property `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` with value: `Rest timer between gym sets: a countdown the user starts by completing a set, shown as an ongoing notification (and on a paired watch) until the rest ends or the user stops or extends it.`
- Privacy policy URL, in exactly one constant: `https://github.com/dcmcand/gymlog/blob/main/PRIVACY.md`.
- Listing assets (Play Console Help "Add preview assets"): icon 512x512 "32-bit PNG (with alpha)" max 1024 KB; feature graphic 1024x500 "JPEG or 24-bit PNG (no alpha)"; screenshots "JPEG or 24-bit PNG (no alpha)", 320-3840 px, long side at most twice the short side, 2 to 8; short description 80 characters; full description 4000 characters.
- No new permissions; never `INTERNET`.
- `app/src/main/assets/adi-registration.properties` is gitignored and must never be committed; the ownership-proof APK is uploaded to the Console only, never published.
- Tests: JUnit 4, table-driven (`data class Case` + loop), backtick names, unit tests run with `app/` as the working directory; script tests are stdlib `unittest` run by `python3 -m unittest discover -s scripts -p 'test_*.py' -v`.
- Run `./gradlew test` (all unit tests) before every commit; CI also runs the script tests.
- No em dashes anywhere (code, comments, docs, copy). `CLAUDE.md` is gitignored and never committed. No Claude/AI attribution in commits or PRs.
- Work on branch `feat/play-prep` (rename the current `docs/play-spec-update`, which holds the updated spec; it goes in the first commit). Ask the user once before the first commit whether per-task commits are OK. Fresh-eyes review before the PR. Ask before merging, before tagging, and before any push of a tag (a tag publishes the GitHub release, the watchapp, and triggers F-Droid's update).

## Review Focus

1. The bundle step silently produces no AAB (path change in a future AGP, or the step is skipped): the release must fail, not finish green with no artifact. Pinned by `if-no-files-found: error` and a `StoreMetadataTest` case (Task 1).
2. Someone adds the AAB to the GitHub release `files:` list "for convenience": the public release would then carry a second, Play-only artifact. Pinned by a `StoreMetadataTest` case asserting the release step mentions no `.aab` (Task 1).
3. `build_ownership_proof.sh` fails mid-build (wrong keystore password, Gradle error) or is interrupted: the snippet must still be removed, so the next commit or F-Droid build can't pick it up. Pinned by a script test with a failing fake Gradle (Task 2), plus the `.gitignore` entry.
4. The snippet file the user saves has extra bytes (editor-added newline, BOM) or is empty: the script must refuse an empty file, copy bytes unchanged, and show what went into the APK so a Console rejection is easy to diagnose. Pinned by script tests (Task 2).
5. No browser on the device (some degoogled ROMs): tapping "Privacy policy" must show the URL in a snackbar, not crash. Pinned by the `ActivityNotFoundException` branch (Task 3) and its emulator check (Task 6) using a disabled browser.

---

## File Structure

| File | Responsibility |
|------|----------------|
| `.github/workflows/release.yml` | `bundleRelease` + `upload-artifact` steps (Task 1) |
| `app/src/main/AndroidManifest.xml` | FGS subtype property on `RestTimerService` (Task 1) |
| `app/src/test/java/com/gymlog/app/StoreMetadataTest.kt` | Pins workflow, manifest property (Task 1); feature graphic, screenshots, description limits (Task 4) |
| `scripts/build_ownership_proof.sh` | One-off ownership-proof APK build (Task 2) |
| `scripts/test_build_ownership_proof.py` | Script tests with a fake Gradle (Task 2) |
| `.gitignore` | Ignore the snippet asset (Task 2) |
| `PRIVACY.md` | Privacy policy (Task 3) |
| `app/src/main/java/com/gymlog/app/ui/settings/PrivacyPolicy.kt` | `PRIVACY_POLICY_URL` constant (Task 3) |
| `app/src/test/java/com/gymlog/app/ui/settings/PrivacyPolicyTest.kt` | URL and policy content checks (Task 3) |
| `app/src/main/java/com/gymlog/app/ui/settings/SettingsScreen.kt` | "Privacy policy" row (Task 3) |
| `README.md` | Privacy section links the policy (Task 3); release note on the AAB (Task 6) |
| `scripts/make_feature_graphic.py` | Renders `featureGraphic.png` (Task 4) |
| `fastlane/metadata/android/en-US/images/featureGraphic.png`, `phoneScreenshots/0[1-4].png` | Listing assets (Task 4) |
| `docs/play-console.md` | Console answers and the account steps (Task 5) |
| `app/build.gradle.kts`, `fastlane/.../changelogs/20100.txt` | 2.1.0 (Task 6) |

## Docs referenced (checked 2026-10-02)

- Android FGS types, `specialUse`: manifest form `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="explanation_for_special_use"/>` inside `<service>`; "These values and corresponding use cases are reviewed when you submit your app in the Google Play Console". `shortService` "can only run for a short period of time (about 3 minutes)". https://developer.android.com/develop/background-work/services/fgs/service-types
- `actions/upload-artifact@v7` inputs `name`, `path`, `if-no-files-found` (`error` fails the action), `retention-days` (1 to 90). https://github.com/actions/upload-artifact
- bundletool: `build-apks --bundle=... --output=... --mode=universal` (debug-signs when no `--ks`). `validate` and `dump manifest` exist in the CLI; confirm flags with `java -jar bundletool.jar help validate` / `help dump`. https://developer.android.com/tools/bundletool
- Play testing requirement: "a minimum of 12 testers who have been opted in continuously for at least 14 days", then "apply for production access on the Dashboard"; review "usually takes seven days or less". https://support.google.com/googleplay/android-developer/answer/14151465
- Data safety: "Even developers with apps that do not collect any user data must complete this form and provide a link to their privacy policy." https://support.google.com/googleplay/android-developer/answer/10787469
- Package name registration: start under Android developer verification > Register package name (creating a Play app "doesn't automatically trigger registration"); existing packages prove ownership with the certificate SHA-256 plus a release APK signed by that key containing `assets/adi-registration.properties`; a rationale only if the certificate is not listed as eligible. https://support.google.com/googleplay/android-developer/answer/16761053 ; extra keys: https://support.google.com/googleplay/android-developer/answer/16762301

---

### Task 1: Signed AAB from CI and the FGS subtype declaration

Advances journeys: 1, 2, 3a, 4

**Files:**
- Modify: `.github/workflows/release.yml` (between "Check APK signing block" and "Create GitHub Release")
- Modify: `app/src/main/AndroidManifest.xml` (the `RestTimerService` `<service>`)
- Test: `app/src/test/java/com/gymlog/app/StoreMetadataTest.kt`

**Interfaces:**
- Consumes: existing `StoreMetadataTest`.
- Produces: workflow artifact `app-release-aab`; manifest property. Task 6 verifies both on the real release.

- [ ] **Step 1: Write the failing tests**

Add to `StoreMetadataTest` (below `version is a plain literal that F-Droid can read`):

```kotlin
    @Test
    fun `release workflow uploads a signed aab as an artifact only`() {
        val workflow = File("../.github/workflows/release.yml").readText()
        // The GitHub release step runs from the gh-release action to the next job.
        val releaseStep = workflow.substringAfter("softprops/action-gh-release").substringBefore("publish-pebble:")
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("bundle is built", workflow.contains("run: ./gradlew bundleRelease")),
            Case("bundle is built after the APK guard", workflow.indexOf("bundleRelease") > workflow.indexOf("apk_check.py signing-block")),
            Case("bundle goes to a workflow artifact", workflow.contains("uses: actions/upload-artifact@v7")),
            Case("artifact name", workflow.contains("name: app-release-aab")),
            Case("artifact path", workflow.contains("path: app/build/outputs/bundle/release/app-release.aab")),
            Case("a missing bundle fails the release", workflow.contains("if-no-files-found: error")),
            Case("release step found", releaseStep.length < workflow.length),
            Case("github release still ships the apk", releaseStep.contains("files: app/build/outputs/apk/release/app-release.apk")),
            Case("github release ships no aab", !releaseStep.contains(".aab")),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }

    @Test
    fun `rest timer declares its special use for play review`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        // The service element with a body (a self-closing tag has no room for the property).
        val service = Regex("""(?s)<service\b[^>]*android:name="\.service\.RestTimerService"[^>]*[^/]>.*?</service>""")
            .find(manifest)?.value.orEmpty()
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("RestTimerService has a body", service.isNotEmpty()),
            Case("still specialUse (shortService is capped at about 3 minutes)", service.contains("""android:foregroundServiceType="specialUse"""")),
            Case("subtype property declared", service.contains("""android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"""")),
            Case("subtype explains the rest timer to the reviewer", Regex("""android:value="Rest timer between gym sets:[^"]{60,}"""").containsMatchIn(service)),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "com.gymlog.app.StoreMetadataTest"`
Expected: FAIL on "bundle is built" and "RestTimerService has a body".

- [ ] **Step 3: Add the manifest property**

Replace the self-closing `RestTimerService` element in `app/src/main/AndroidManifest.xml` with:

```xml
        <service
            android:name=".service.RestTimerService"
            android:foregroundServiceType="specialUse"
            android:exported="false">
            <!-- Play Console reviews this free-form text for the specialUse type. -->
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Rest timer between gym sets: a countdown the user starts by completing a set, shown as an ongoing notification (and on a paired watch) until the rest ends or the user stops or extends it." />
        </service>
```

- [ ] **Step 4: Add the bundle steps to `release.yml`**

Insert after the "Check APK signing block (F-Droid reproducible builds)" step:

```yaml
      # Google Play gets an AAB signed with this same key, which Play treats as the upload key
      # (Play App Signing re-signs installs). It is a workflow artifact, not a release asset, so
      # the public release stays APK-only.
      - name: Build release AAB (Google Play)
        env:
          KEYSTORE_FILE: /tmp/release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew bundleRelease

      - name: Upload AAB artifact
        uses: actions/upload-artifact@v7
        with:
          name: app-release-aab
          path: app/build/outputs/bundle/release/app-release.aab
          if-no-files-found: error
          retention-days: 90
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew test`
Expected: PASS (whole suite, including `ManifestPermissionsTest`).

- [ ] **Step 6: Check the bundle locally with bundletool**

```bash
mkdir -p build/tools
curl -fL -o build/tools/bundletool.jar https://github.com/google/bundletool/releases/download/1.18.3/bundletool-all-1.18.3.jar
BT="java -jar build/tools/bundletool.jar"
$BT help validate; $BT help dump      # confirm flag names before relying on them
./gradlew bundleRelease               # no KEYSTORE_FILE: unsigned bundle, fine for validation
$BT validate --bundle=app/build/outputs/bundle/release/app-release.aab
$BT dump manifest --bundle=app/build/outputs/bundle/release/app-release.aab | grep -E 'targetSdkVersion|PROPERTY_SPECIAL_USE_FGS_SUBTYPE|RestTimerService'
```
Expected: `validate` exits 0; the dump shows `targetSdkVersion="36"`, the `RestTimerService` element and the property with the full value. Save the output for Task 6's evidence. Also run `python3 scripts/apk_check.py signing-block` on a throwaway-signed `assembleRelease` APK if one is at hand; otherwise Task 6 covers it on the real release.

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/release.yml app/src/main/AndroidManifest.xml app/src/test/java/com/gymlog/app/StoreMetadataTest.kt
git commit -m "Build a signed AAB for Google Play and declare the rest timer's special use"
```

---

### Task 2: Ownership-proof APK helper for Android developer verification

Advances journeys: 10

**Files:**
- Create: `scripts/build_ownership_proof.sh` (executable)
- Create: `scripts/test_build_ownership_proof.py`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `scripts/build_ownership_proof.sh SNIPPET_FILE` with the release signing env vars set. Exit 0 and `build/ownership-proof.apk` on success; non-zero on any failure. Always removes `app/src/main/assets/adi-registration.properties` (and `app/src/main/assets/` if it created it). `GRADLE` env var overrides the Gradle command (tests use a fake).

- [ ] **Step 1: Write the failing tests**

`scripts/test_build_ownership_proof.py`:

```python
"""Tests for build_ownership_proof.sh: python3 -m unittest discover -s scripts -p 'test_*.py'

Each test copies the script into a scratch "repo" and points GRADLE at a fake that writes an APK
(a zip holding whatever is in app/src/main/assets) or fails, so no real build runs.
"""
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "build_ownership_proof.sh")
ASSET = os.path.join("app", "src", "main", "assets", "adi-registration.properties")

FAKE_GRADLE_OK = """#!/usr/bin/env python3
import os, zipfile
out = "app/build/outputs/apk/release"
os.makedirs(out, exist_ok=True)
with zipfile.ZipFile(os.path.join(out, "app-release.apk"), "w") as z:
    assets = "app/src/main/assets"
    for name in (os.listdir(assets) if os.path.isdir(assets) else []):
        z.write(os.path.join(assets, name), "assets/" + name)
"""
FAKE_GRADLE_FAIL = "#!/usr/bin/env python3\nraise SystemExit(1)\n"


class BuildOwnershipProofTest(unittest.TestCase):
    def new_root(self):
        """A scratch repo holding only the script; removed at the end of the test."""
        self.root = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.root)
        os.makedirs(os.path.join(self.root, "scripts"))
        shutil.copy(SCRIPT, os.path.join(self.root, "scripts"))
        self.snippet = os.path.join(self.root, "snippet.txt")

    def run_script(self, gradle_src, snippet=b"token-abc\n", args=None, env_extra=None):
        if snippet is not None:
            with open(self.snippet, "wb") as f:
                f.write(snippet)
        gradle = os.path.join(self.root, "fake_gradle")
        with open(gradle, "w") as f:
            f.write(gradle_src)
        os.chmod(gradle, 0o755)
        env = dict(os.environ, GRADLE=gradle, KEYSTORE_FILE="/dev/null")
        env.update(env_extra or {})
        cmd = ["bash", os.path.join(self.root, "scripts", "build_ownership_proof.sh")]
        cmd += [self.snippet] if args is None else args
        return subprocess.run(cmd, cwd=self.root, env=env, capture_output=True, text=True)

    def test_cases(self):
        cases = [
            # name, gradle, snippet bytes, args, env, expect_ok
            ("builds and cleans up", FAKE_GRADLE_OK, b"token-abc\n", None, {}, True),
            ("snippet bytes kept exactly (no newline)", FAKE_GRADLE_OK, b"token-abc", None, {}, True),
            ("failed build still cleans up", FAKE_GRADLE_FAIL, b"token-abc\n", None, {}, False),
            ("empty snippet refused", FAKE_GRADLE_OK, b"", None, {}, False),
            ("missing argument refused", FAKE_GRADLE_OK, None, [], {}, False),
            ("no signing env refused", FAKE_GRADLE_OK, b"token-abc\n", None, {"KEYSTORE_FILE": ""}, False),
        ]
        for name, gradle, snippet, args, env, expect_ok in cases:
            with self.subTest(name):
                self.new_root()
                r = self.run_script(gradle, snippet, args, env)
                self.assertEqual(expect_ok, r.returncode == 0, r.stdout + r.stderr)
                self.assertFalse(os.path.exists(os.path.join(self.root, ASSET)), "snippet left behind")
                self.assertFalse(os.path.exists(os.path.join(self.root, "app", "src", "main", "assets")), "assets dir left behind")
                proof = os.path.join(self.root, "build", "ownership-proof.apk")
                self.assertEqual(expect_ok, os.path.exists(proof))
                if expect_ok:
                    with zipfile.ZipFile(proof) as z:
                        self.assertEqual(snippet, z.read("assets/adi-registration.properties"))

    def test_existing_asset_is_not_overwritten(self):
        self.new_root()
        existing = os.path.join(self.root, ASSET)
        os.makedirs(os.path.dirname(existing))
        with open(existing, "wb") as f:
            f.write(b"someone else's\n")
        r = self.run_script(FAKE_GRADLE_OK)
        self.assertNotEqual(0, r.returncode)
        with open(existing, "rb") as f:
            self.assertEqual(b"someone else's\n", f.read())


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run them to see them fail**

Run: `python3 -m unittest discover -s scripts -p 'test_build_ownership_proof.py' -v`
Expected: FAIL/ERROR (script does not exist).

- [ ] **Step 3: Write the script**

`scripts/build_ownership_proof.sh` (then `chmod +x`):

```bash
#!/usr/bin/env bash
# Builds the one-off APK that proves to Play Console (Android developer verification > Register
# package name) that we hold the release key for io.github.dcmcand.gymlog. The Console shows a
# snippet for assets/adi-registration.properties: save it to a file exactly as shown, then
#
#   KEYSTORE_FILE=... KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... \
#     scripts/build_ownership_proof.sh SNIPPET_FILE
#
# and upload build/ownership-proof.apk in the Console. Never publish that APK. The snippet is
# removed again even if the build fails, and .gitignore keeps it out of commits.
set -euo pipefail

snippet=${1:?usage: $0 SNIPPET_FILE}
[[ -s "$snippet" ]] || { echo "snippet file is missing or empty: $snippet" >&2; exit 1; }
[[ -n "${KEYSTORE_FILE:-}" ]] || { echo "set KEYSTORE_FILE and the other release signing env vars" >&2; exit 1; }

root=$(cd "$(dirname "$0")/.." && pwd)
assets="$root/app/src/main/assets"
asset="$assets/adi-registration.properties"
[[ ! -e "$asset" ]] || { echo "refusing to overwrite existing $asset" >&2; exit 1; }

created_assets=false
[[ -d "$assets" ]] || created_assets=true
cleanup() {
    rm -f "$asset"
    if $created_assets; then rmdir "$assets" 2>/dev/null || true; fi
}
trap cleanup EXIT

mkdir -p "$assets"
cp "$snippet" "$asset"

cd "$root"
# clean: never reuse an APK built without (or with an older) snippet.
${GRADLE:-./gradlew} clean assembleRelease

mkdir -p build
cp app/build/outputs/apk/release/app-release.apk build/ownership-proof.apk
echo "Snippet inside the APK (between the markers):"
echo "-----"
unzip -p build/ownership-proof.apk assets/adi-registration.properties
echo "-----"
echo "Upload $root/build/ownership-proof.apk in Play Console. Do not publish it."
```

Note: the `rm -f` in `cleanup` is safe because the refuse-to-overwrite check runs before the trap is set.

- [ ] **Step 4: Ignore the asset**

Append to `.gitignore`:

```
# Play Console ownership snippet (scripts/build_ownership_proof.sh); never commit it
app/src/main/assets/adi-registration.properties
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `python3 -m unittest discover -s scripts -p 'test_*.py' -v`
Expected: PASS for the new tests and the existing `test_apk_check.py`. If `unzip` is missing on the CI runner, the success cases will fail: ubuntu-latest ships it, but check the CI run.

- [ ] **Step 6: Commit**

```bash
git add scripts/build_ownership_proof.sh scripts/test_build_ownership_proof.py .gitignore
git commit -m "Add a helper that builds the Play package-ownership proof APK"
```

---

### Task 3: Privacy policy and the Settings link

Advances journeys: 5

**Files:**
- Create: `PRIVACY.md`
- Create: `app/src/main/java/com/gymlog/app/ui/settings/PrivacyPolicy.kt`
- Create: `app/src/test/java/com/gymlog/app/ui/settings/PrivacyPolicyTest.kt`
- Modify: `app/src/main/java/com/gymlog/app/ui/settings/SettingsScreen.kt` (Column body, below the progress indicator)
- Modify: `README.md` (Privacy section)

**Interfaces:**
- Produces: `const val PRIVACY_POLICY_URL: String` in package `com.gymlog.app.ui.settings`. Task 5 copies the same URL into `docs/play-console.md`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/gymlog/app/ui/settings/PrivacyPolicyTest.kt`:

```kotlin
package com.gymlog.app.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Play requires a privacy policy URL; Settings links to the same file. Runs from `app/`. */
class PrivacyPolicyTest {

    private val repoBlobPrefix = "https://github.com/dcmcand/gymlog/blob/main/"

    @Test
    fun `settings link points at the policy file in the repo`() {
        val path = PRIVACY_POLICY_URL.removePrefix(repoBlobPrefix)
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("URL is on the main branch of the repo", PRIVACY_POLICY_URL.startsWith(repoBlobPrefix)),
            Case("URL names a file that exists ($path)", File("../$path").isFile),
            Case("URL is the root PRIVACY.md", path == "PRIVACY.md"),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }

    @Test
    fun `policy states the claims the store forms rely on`() {
        val policy = File("../PRIVACY.md").readText()
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("no internet permission", policy.contains("no internet permission", ignoreCase = true)),
            Case("nothing collected, shared or sold", policy.contains("does not collect, share or sell", ignoreCase = true)),
            Case("data stays on the device", policy.contains("stays on your device", ignoreCase = true)),
            Case("no analytics or ads", policy.contains("no analytics", ignoreCase = true) && policy.contains("no ads", ignoreCase = true)),
            Case("pebble integration covered", policy.contains("Pebble")),
            Case("contact given", policy.contains("https://github.com/dcmcand/gymlog/issues")),
            Case("effective date given", Regex("""Effective date: \d{4}-\d{2}-\d{2}""").containsMatchIn(policy)),
            Case("no em dashes", !policy.contains('\u2014')),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "com.gymlog.app.ui.settings.PrivacyPolicyTest"`
Expected: compile failure, `PRIVACY_POLICY_URL` unresolved.

- [ ] **Step 3: Add the constant**

`app/src/main/java/com/gymlog/app/ui/settings/PrivacyPolicy.kt`:

```kotlin
package com.gymlog.app.ui.settings

/** The policy Play Console links to; Settings opens the same page. */
const val PRIVACY_POLICY_URL = "https://github.com/dcmcand/gymlog/blob/main/PRIVACY.md"
```

- [ ] **Step 4: Write `PRIVACY.md`**

```markdown
# GymLog privacy policy

Effective date: 2026-10-02

GymLog is a workout log that works entirely on your phone. This policy covers the GymLog
Android app (`io.github.dcmcand.gymlog`) and its Pebble watch app, whether installed from
Google Play, F-Droid or GitHub.

## What GymLog collects

Nothing. GymLog does not collect, share or sell any personal or usage data.

- **No internet permission.** The app cannot connect to the internet, so nothing you enter can
  leave your phone through it.
- **No accounts.** There is nothing to sign up for or log in to.
- **No analytics, no ads, no crash reporting.** No third-party SDKs that track you.

## Where your data lives

Your exercises, workouts and history are stored in a database on your phone and stay on your
device. Android cloud backup is turned off for the app, so this data is not copied to your
Google account either.

If you use **Export data** in Settings, GymLog writes a backup file to the place you choose
(for example your Downloads folder or a cloud drive app). That file is then under your control;
GymLog does not send it anywhere. **Import data** reads a file you pick and replaces the data on
this phone.

## Pebble watch

If you use the optional Pebble watch app, GymLog passes the current exercise, set and rest timer
to the Pebble app on the same phone through Android's app-to-app messaging. The Pebble app
handles the Bluetooth connection to your watch. GymLog sends nothing beyond that phone-to-watch
link; see the Pebble app's own policy for how it handles data.

## Permissions

GymLog asks only for what the rest timer needs: showing notifications, vibrating, and running
the timer as a foreground service. It does not request location, contacts, camera, microphone,
storage-wide access or network access.

## Children

GymLog is not directed at children and collects no data from anyone.

## Changes

If this policy changes, the new version is published at this address with a new effective date.

## Contact

Questions: open an issue at https://github.com/dcmcand/gymlog/issues
```

- [ ] **Step 5: Run the test to see it pass**

Run: `./gradlew test --tests "com.gymlog.app.ui.settings.PrivacyPolicyTest"`
Expected: PASS.

- [ ] **Step 6: Add the Settings row**

In `SettingsScreen.kt` add imports `android.content.Intent` and `androidx.core.net.toUri` (core-ktx is already a dependency). After `if (busy) LinearProgressIndicator(...)` and before the version `Text`, add:

```kotlin
            Text("About", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
                    } catch (_: ActivityNotFoundException) {
                        // No browser (some ROMs ship none): show where to read it instead.
                        scope.launch { snackbar.showSnackbar("No browser found. Privacy policy: $PRIVACY_POLICY_URL") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Privacy policy") }
```

It stays enabled while `busy`: opening a browser does not interfere with a running export/import, and `BackHandler` still keeps the user on the screen.

- [ ] **Step 7: Link the policy from the README**

In `README.md`, under `## Privacy`, after "Your data stays on your device, period." add a blank line and:

```markdown
The full policy is in [PRIVACY.md](PRIVACY.md).
```

- [ ] **Step 8: Build and test**

Run: `./gradlew test assembleDebug`
Expected: PASS and a debug APK. (Tapping the row is checked on the emulator in Task 6.)

- [ ] **Step 9: Commit**

```bash
git add PRIVACY.md README.md app/src/main/java/com/gymlog/app/ui/settings/PrivacyPolicy.kt app/src/main/java/com/gymlog/app/ui/settings/SettingsScreen.kt app/src/test/java/com/gymlog/app/ui/settings/PrivacyPolicyTest.kt
git commit -m "Add a privacy policy and link it from Settings"
```

---

### Task 4: Store listing assets that pass Play's rules

Advances journeys: 7

**Files:**
- Create: `scripts/make_feature_graphic.py`
- Create: `fastlane/metadata/android/en-US/images/featureGraphic.png`
- Modify: `fastlane/metadata/android/en-US/images/phoneScreenshots/01.png` to `04.png`
- Test: `app/src/test/java/com/gymlog/app/StoreMetadataTest.kt`

**Interfaces:**
- Consumes: `StoreMetadataTest.listing`.
- Produces: `private fun pngInfo(file: File): PngInfo` in `StoreMetadataTest` (width, height, bitDepth, colorType), used by the icon test too.

Note: `icon.png` already is 512x512 8-bit RGBA (fixed in 2.0.1, pinned by `store icon is a 512 px square png`); this task only adds the size limit.

- [ ] **Step 1: Write the failing tests**

In `StoreMetadataTest`, add the helper and refactor the icon test to use it:

```kotlin
    private data class PngInfo(val width: Int, val height: Int, val bitDepth: Int, val colorType: Int)

    /** Reads the IHDR chunk. Color type 2 = RGB (no alpha), 6 = RGBA. */
    private fun pngInfo(file: File): PngInfo = DataInputStream(file.inputStream()).use { input ->
        val header = ByteArray(16).also { input.readFully(it) }
        assertTrue("${file.name}: PNG signature", header.copyOfRange(1, 4).decodeToString() == "PNG")
        PngInfo(input.readInt(), input.readInt(), input.readUnsignedByte(), input.readUnsignedByte())
    }

    @Test
    fun `store icon is a 512 px square png`() {
        val icon = File(listing, "images/icon.png")
        assertTrue("icon.png exists", icon.exists())
        val info = pngInfo(icon)
        data class Case(val name: String, val ok: Boolean)
        val cases = listOf(
            Case("512x512 (was ${info.width}x${info.height})", info.width == 512 && info.height == 512),
            // Play wants a "32-bit PNG (with alpha)": 8 bits per channel, RGBA (color type 6).
            Case("8 bits per channel (was ${info.bitDepth})", info.bitDepth == 8),
            Case("RGBA color type 6 (was ${info.colorType})", info.colorType == 6),
            Case("at most 1024 KB (was ${icon.length()} bytes)", icon.length() <= 1024 * 1024),
        )
        for (c in cases) assertTrue(c.name, c.ok)
    }

    @Test
    fun `feature graphic and screenshots follow the play rules`() {
        data class Case(val name: String, val ok: Boolean)
        val cases = mutableListOf<Case>()
        val feature = File(listing, "images/featureGraphic.png")
        cases += Case("featureGraphic.png exists", feature.exists())
        if (feature.exists()) {
            val f = pngInfo(feature)
            cases += Case("feature graphic 1024x500 (was ${f.width}x${f.height})", f.width == 1024 && f.height == 500)
            cases += Case("feature graphic 24-bit RGB, no alpha (bit depth ${f.bitDepth}, color type ${f.colorType})", f.bitDepth == 8 && f.colorType == 2)
        }
        val shots = File(listing, "images/phoneScreenshots").listFiles { f -> f.extension == "png" }.orEmpty()
        cases += Case("2 to 8 phone screenshots (was ${shots.size})", shots.size in 2..8)
        for (shot in shots.sortedBy { it.name }) {
            val s = pngInfo(shot)
            val (short, long) = minOf(s.width, s.height) to maxOf(s.width, s.height)
            cases += Case("${shot.name}: sides within 320-3840 px (${s.width}x${s.height})", short >= 320 && long <= 3840)
            cases += Case("${shot.name}: long side at most twice the short (${s.width}x${s.height})", long <= 2 * short)
            cases += Case("${shot.name}: 24-bit RGB, no alpha (bit depth ${s.bitDepth}, color type ${s.colorType})", s.bitDepth == 8 && s.colorType == 2)
        }
        val full = File(listing, "full_description.txt").readText().trim()
        cases += Case("full description at most 4000 characters (${full.length})", full.length <= 4000)
        for (c in cases) assertTrue(c.name, c.ok)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "com.gymlog.app.StoreMetadataTest"`
Expected: FAIL on "featureGraphic.png exists" and the screenshot ratio/alpha cases; the icon test still PASSES.

- [ ] **Step 3: Write the feature graphic script**

`scripts/make_feature_graphic.py`:

```python
#!/usr/bin/env python3
"""Renders the Play/F-Droid feature graphic (1024x500, RGB, no alpha). One-off, needs Pillow:

  python3 scripts/make_feature_graphic.py fastlane/metadata/android/en-US/images/featureGraphic.png

The dumbbell is the launcher icon's (app/src/main/res/drawable/ic_launcher_foreground.xml,
108-unit viewport) scaled up; the green is ic_launcher_background.
"""
import sys

from PIL import Image, ImageDraw, ImageFont

GREEN = (0x1B, 0x5E, 0x20)
WHITE = (255, 255, 255)
FONT = "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans-Bold.ttf"
# (x1, y1, x2, y2, stroke width) from ic_launcher_foreground.xml, round caps.
DUMBBELL = [(30, 54, 78, 54, 4), (24, 38, 24, 70, 6), (32, 42, 32, 66, 5), (84, 38, 84, 70, 6), (76, 42, 76, 66, 5)]


def round_line(draw, x1, y1, x2, y2, width):
    draw.line((x1, y1, x2, y2), fill=WHITE, width=width)
    r = width / 2
    for x, y in ((x1, y1), (x2, y2)):
        draw.ellipse((x - r, y - r, x + r, y + r), fill=WHITE)


def main(out):
    img = Image.new("RGB", (1024, 500), GREEN)
    draw = ImageDraw.Draw(img)
    # 5x scale; offsets put the dumbbell at x 55-385 (with caps) and centre it on y=250.
    scale, ox, oy = 5, -50, -20
    for x1, y1, x2, y2, w in DUMBBELL:
        round_line(draw, ox + x1 * scale, oy + y1 * scale, ox + x2 * scale, oy + y2 * scale, w * scale)
    draw.text((420, 250), "GymLog", font=ImageFont.truetype(FONT, 120), fill=WHITE, anchor="ls")
    draw.text((424, 320), "Private, offline workout log", font=ImageFont.truetype(FONT, 38), fill=WHITE, anchor="ls")
    img.save(out, "PNG")


if __name__ == "__main__":
    main(sys.argv[1])
```

- [ ] **Step 4: Render it and look at it**

```bash
python3 scripts/make_feature_graphic.py fastlane/metadata/android/en-US/images/featureGraphic.png
file fastlane/metadata/android/en-US/images/featureGraphic.png
```
Expected: `PNG image data, 1024 x 500, 8-bit/color RGB`. Open the PNG (Read tool) and check: the dumbbell is fully inside the frame with a margin, the text does not overlap it or run off the right edge, and nothing important sits in the outer ~15% (Play may crop or overlay the edges). Adjust `scale`/`ox`/text positions if not, re-render, re-check.

- [ ] **Step 5: Crop the screenshots to 1080x2160 without alpha**

The current files are 1080x2424 with the status bar at the top (about rows 0-150), a blank inset gap under it (the 2.0.2 title-bar fix removed that gap from the app), and the gesture bar at the bottom (about rows 2355-2424). Cropping rows 200-2360 removes only those.

```bash
python3 - <<'EOF'
from PIL import Image
d = "fastlane/metadata/android/en-US/images/phoneScreenshots"
for n in ("01", "02", "03", "04"):
    p = f"{d}/{n}.png"
    Image.open(p).convert("RGB").crop((0, 200, 1080, 2360)).save(p, "PNG")
EOF
file fastlane/metadata/android/en-US/images/phoneScreenshots/*.png
```
Expected: four `1080 x 2160, 8-bit/color RGB`. Open each one (Read tool): the top must start below the status bar and above the screen's title, and the bottom must keep the app's last element (bottom nav or Finish Workout button). If a crop cuts app content, restore that file with `git checkout` and pick that file's own offset (keep the height at 2160), then re-check.

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add scripts/make_feature_graphic.py fastlane/metadata/android/en-US/images app/src/test/java/com/gymlog/app/StoreMetadataTest.kt
git commit -m "Add the feature graphic and crop screenshots to Play's rules"
```

---

### Task 5: Play Console answers and the account steps

Advances journeys: 8, 3b, 10 (written instructions for the user-side steps)

**Files:**
- Create: `docs/play-console.md`

**Interfaces:**
- Consumes: `PRIVACY_POLICY_URL` value (Task 3), FGS subtype text (Task 1), `scripts/build_ownership_proof.sh` usage (Task 2), fastlane text.

- [ ] **Step 1: Re-check the Console forms against current help pages**

Before writing, open (WebFetch) and skim the current Play Console Help for: Data safety (answer 10787469), Health apps declaration (search "Health apps declaration form Play Console"), foreground service permissions declaration (search "Understanding foreground service and full-screen intent requirements"), content rating (IARC questionnaire), target audience and content, and app testing requirements (answer 14151465). Note any field or option name that differs from the doc below and use the Console's wording. Record the URLs at the top of the doc.

- [ ] **Step 2: Write `docs/play-console.md`**

```markdown
# Google Play Console: answers and steps

Ready answers for every Play Console form GymLog needs, plus the account-side steps. Checked
against Play Console Help on <date of Step 1>; the Console wording wins if it differs.

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
   `io.github.dcmcand.gymlog`.
2. Give the SHA-256 above as the certificate.
3. The package has been installed before (GitHub, F-Droid), so the Console asks for ownership
   proof and shows a snippet for `adi-registration.properties`. Save it to a file exactly as
   shown (no extra lines), outside the repo, e.g. `~/gymlog-adi-snippet.txt`.
4. Build the proof APK with the release keystore:
   `KEYSTORE_FILE=... KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... scripts/build_ownership_proof.sh ~/gymlog-adi-snippet.txt`
5. Upload `build/ownership-proof.apk`. Do not publish it anywhere.
6. If the Console asks for a rationale: "This package name has been distributed since 2026 on
   GitHub Releases and F-Droid (reproducible builds signed with this key). Changing it would
   force every existing user to reinstall and migrate their data."
7. `git status` must show no `adi-registration.properties` (the script removes it).
8. After the first AAB upload creates the Play app signing key: if the registration page does not
   list it already, add its SHA-256 (App integrity > App signing) under "Adding additional keys".

## 2. Create app

- App name: GymLog. Default language: English (United States). App or game: App. Free.
- Declarations: Developer Program Policies and US export laws: accept.

## 3. App content

| Form | Answer |
|------|--------|
| Privacy policy | https://github.com/dcmcand/gymlog/blob/main/PRIVACY.md |
| App access | All functionality is available without special access (no login). |
| Ads | No, the app does not contain ads. |
| Content rating | IARC questionnaire: category "All other app types" (not a game, not social). Answer No to violence, sexual content, profanity, controlled substances, gambling, user-generated content, user-to-user interaction, sharing location, and digital purchases. Expected rating: Everyone / PEGI 3. |
| Target audience | 18 and over only. Not designed to appeal to children. |
| News app | No. |
| Data safety | Collects or shares required user data types: **No**. (All data is processed on the device and never sent off it; Play does not count on-device-only data as collected.) Security practices sections are skipped when nothing is collected. |
| Advertising ID | No, the app does not use an advertising ID. |
| Government app | No. |
| Financial features | None. |
| Health apps | Declare the fitness feature that matches workout logging (e.g. "Activity and fitness" / workout tracking). Not a medical device. No Health Connect. Health data is stored only on the device and is not shared. |
| Foreground service permissions | Type: Special use. Description: the subtype text below. Impact if deferred: the rest countdown would stop or be delayed while the screen is off, so the user would miss the end of their rest. User-initiated: yes, it starts when the user completes a set. A video may be requested: record a screen capture of completing a set, the ongoing countdown notification, and the rest ending, and upload it unlisted. |

Special use subtype (same text as the manifest property):

> Rest timer between gym sets: a countdown the user starts by completing a set, shown as an ongoing notification (and on a paired watch) until the rest ends or the user stops or extends it.

Why not `shortService`: Android limits it to "about 3 minutes", and a rest (especially after
the user extends it) can run longer.

## 4. Store listing

- Category: Health & Fitness. Tags: workout, fitness tracker.
- Contact email: <user fills in>. Website: https://github.com/dcmcand/gymlog
- Short description: `fastlane/metadata/android/en-US/short_description.txt`
- Full description: `fastlane/metadata/android/en-US/full_description.txt`
- App icon: `fastlane/metadata/android/en-US/images/icon.png`
- Feature graphic: `fastlane/metadata/android/en-US/images/featureGraphic.png`
- Phone screenshots: `fastlane/metadata/android/en-US/images/phoneScreenshots/01.png` to `04.png`

## 5. Closed test

- Testing > Closed testing > create track (e.g. "Closed testers"). Countries: all.
- Testers: an email list or a Google Group of 12+ people with Google accounts; give them the
  opt-in link, and they install from Play. They must stay opted in for 14 days in a row;
  "Testers who opt in, test for fewer than 14 days, and then opt out do not count".
- Release: upload `app-release.aab` from the `v2.1.0` release run's `app-release-aab` artifact
  (`gh run download <run-id> -n app-release-aab`). Enroll in Play App Signing with a
  Google-generated key (default).
- Release notes: `fastlane/metadata/android/en-US/changelogs/20100.txt`.
- Note: Play-installed GymLog has a different signature from GitHub/F-Droid installs. Switching
  means Export data, uninstall, install, Import data. Testers already using the GitHub/F-Droid
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
- **Production readiness:** what changed during testing (<fill in>), how you know it is ready
  (testers' workouts logged without crashes, Play pre-launch report clean).
```

Replace `<date of Step 1>` with the actual date and any option names Step 1 found to differ. Leave the `<user fills in>` markers: those are facts only the user has (email, recruiting, feedback).

- [ ] **Step 3: Check the doc**

Run: `grep -n $'\u2014' docs/play-console.md; grep -c "PRIVACY.md" docs/play-console.md`
Expected: no em dash lines; at least one PRIVACY.md line. Walk the App content table against the Console's App content list from Step 1: every listed form has a row.

- [ ] **Step 4: Commit**

```bash
git add docs/play-console.md
git commit -m "Add Play Console answers and account steps"
```

---

### Task 6: Release 2.1.0 and verify

Advances journeys: 1, 2, 3a, 4, 5, 6, 7, 9 (evidence); 3b and 10 handed to the user

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode`, `versionName`)
- Create: `fastlane/metadata/android/en-US/changelogs/20100.txt`
- Modify: `README.md` (Build section: the AAB)
- Modify: this plan (Evidence column)

- [ ] **Step 1: Bump the version and write the changelog**

`app/build.gradle.kts`: `versionCode = 20100`, `versionName = "2.1.0"`.

`fastlane/metadata/android/en-US/changelogs/20100.txt`:

```
New: Settings links to the privacy policy. Getting ready for Google Play: the rest timer now tells Android (and Play's reviewers) what its notification is for, and the store listing has a feature graphic and tidied screenshots. No changes to your data.
```

README, under `### Build the release APK` after the existing note, add:

```markdown
Release tags also build a signed `app-release.aab` for Google Play. It is attached to the release workflow run as the `app-release-aab` artifact, not to the GitHub release.
```

- [ ] **Step 2: Full local checks**

Run: `./gradlew test lintDebug assembleDebug && python3 -m unittest discover -s scripts -p 'test_*.py' -v && python3 scripts/check_release_version.py v2.1.0`
Expected: all pass.

- [ ] **Step 3: Emulator check of an R8 release build**

Start AVD `gymlog36` (`emulator -avd gymlog36 -gpu swangle_indirect`; swiftshader segfaults). Build a release APK (`assembleRelease`) signed with a throwaway keystore, as in the 2.0.2 check, and install it (uninstall any differently signed GymLog on the AVD first, exporting its data if needed). Check and capture:
- Settings > Privacy policy opens the browser at the URL: `adb shell dumpsys activity activities | grep -m3 -E 'mResumedActivity|Intent.*PRIVACY'` (journey 5).
- With the browser disabled (`adb shell pm disable-user --user 0 com.android.chrome`, plus any other browser `adb shell cmd package query-activities -a android.intent.action.VIEW -d https://example.com` lists), tapping shows the snackbar with the URL and no crash; re-enable afterwards (Review Focus 5).
- Complete a set: rest timer notification counts down and ends; screenshot plus `adb logcat -d | grep -c 'FATAL EXCEPTION'` is 0 (journey 6).
- `$ANDROID_HOME/build-tools/36.0.0/aapt2 dump permissions` on this APK vs the v2.0.2 release APK (`gh release download v2.0.2 -p app-release.apk`): identical, no INTERNET (journey 9).

- [ ] **Step 4: Fresh-eyes review, commit, PR**

Run the fresh-eyes review over the whole branch diff (`git diff main...HEAD`), fix findings, then:

```bash
git add app/build.gradle.kts fastlane/metadata/android/en-US/changelogs/20100.txt README.md
git commit -m "2.1.0"
git -c credential.helper='!gh auth git-credential' push -u https://github.com/dcmcand/gymlog.git feat/play-prep
gh pr create --title "2.1.0: Google Play prep" --body "<summary of Tasks 1-6, journeys status>"
```
Wait for CI green. Ask the user to merge, then ask before tagging.

- [ ] **Step 5: Tag (after the user says yes) and collect the release evidence**

```bash
git checkout main && git pull
git tag v2.1.0 && git -c credential.helper='!gh auth git-credential' push https://github.com/dcmcand/gymlog.git v2.1.0
gh run watch "$(gh run list --workflow release.yml -L1 --json databaseId -q '.[0].databaseId')"
RUN=$(gh run list --workflow release.yml -L1 --json databaseId -q '.[0].databaseId')
gh run view "$RUN" --json jobs,status -q '.status'
gh api repos/dcmcand/gymlog/actions/runs/$RUN/artifacts -q '.artifacts[] | .name'     # journey 1
gh release view v2.1.0 --json assets -q '.assets[].name'                               # journey 1: only app-release.apk
D=build/release-2.1.0; mkdir -p $D
gh run download "$RUN" -n app-release-aab -D $D
gh release download v2.1.0 -p app-release.apk -D $D
jarsigner -verify -verbose -certs $D/app-release.aab | grep -E 'jar verified|SHA-256|CN='     # journey 2
keytool -printcert -jarfile $D/app-release.aab | grep SHA256                                  # expect 65:74:C5:FD:...:B7
BT="java -jar build/tools/bundletool.jar"
$BT validate --bundle=$D/app-release.aab                                                       # journey 3a
$BT dump manifest --bundle=$D/app-release.aab | grep -E 'targetSdkVersion|PROPERTY_SPECIAL_USE_FGS_SUBTYPE'
$BT build-apks --bundle=$D/app-release.aab --output=$D/universal.apks --mode=universal
unzip -o -d $D $D/universal.apks universal.apk
A2=$ANDROID_HOME/build-tools/36.0.0/aapt2
diff <($A2 dump badging $D/universal.apk | grep -E '^package:') <($A2 dump badging $D/app-release.apk | grep -E '^package:')  # journey 2
diff <($A2 dump permissions $D/universal.apk) <($A2 dump permissions $D/app-release.apk)
python3 scripts/apk_check.py signing-block $D/app-release.apk                                 # journey 4
```
Journey 4 compare: fresh clone of `v2.1.0` into a scratch dir, `./gradlew assembleRelease` with no keystore env, then `python3 scripts/apk_check.py compare $D/app-release.apk <clone>/app/build/outputs/apk/release/app-release-unsigned.apk`. Expected: identical outside the signing block.

Journey 7 `fdroid lint`: in `~/devel/fdroiddata` (`git pull` on master), run the container lint as in the F-Droid plan (`docs/superpowers/plans/2026-09-27-fdroid-submission.md` Task 3 Step 2, `$RUN lint io.github.dcmcand.gymlog -v`). The metadata file does not change; this only confirms nothing else broke. F-Droid's own checkupdates will pick up v2.1.0; watch `https://f-droid.org/repo/status/build.json` in the following days for a successful 20100 build.

- [ ] **Step 6: Record evidence, hand over 3b and 10**

Paste each command's output into the Evidence column of the Journeys table in this plan (journeys 1, 2, 3a, 4, 5, 6, 7, 9). Leave 3b and 10 open with "user step: see docs/play-console.md sections 1 and 5". Commit on a small branch and PR (`Record 2.1.0 Play prep evidence`), asking the user before merging. Run the `definition-of-done-journeys` verification gate before saying the work is complete: 3b and 10 stay open as user-owned items, stated as such.
