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
packed=$(mktemp)
unzip -p build/ownership-proof.apk assets/adi-registration.properties > "$packed"
echo "Snippet inside the APK ($(wc -c < "$packed") bytes, between the markers):"
echo "-----"
cat "$packed"
echo "-----"
# These print like a correct snippet but may make the Console reject the proof.
[[ "$(head -c 3 "$packed" | od -An -tx1 | tr -d ' \n')" != "efbbbf" ]] ||
    echo "WARNING: the snippet starts with a UTF-8 BOM; re-save it without one if the Console rejects the APK."
! grep -q $'\r' "$packed" ||
    echo "WARNING: the snippet has CR (Windows) line endings; re-save it with LF if the Console rejects the APK."
rm -f "$packed"
echo "Upload $root/build/ownership-proof.apk in Play Console. Do not publish it."
