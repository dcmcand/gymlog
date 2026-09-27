#!/usr/bin/env python3
"""check_release_version.py TAG: fail unless TAG (e.g. v2.0.1) matches the version in
app/build.gradle.kts. F-Droid rebuilds each tag from source and must get the same APK as CI,
so the tag, versionName and versionCode (major*10000 + minor*100 + patch) have to agree."""
import re
import sys


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    tag = argv[1]
    gradle = open("app/build.gradle.kts").read()
    name = re.search(r'(?m)^\s*versionName\s*=\s*"([^"]+)"\s*$', gradle)
    code = re.search(r"(?m)^\s*versionCode\s*=\s*(\d+)\s*$", gradle)
    if not name or not code:
        print("FAIL: versionName/versionCode must be plain literals in app/build.gradle.kts")
        return 1
    m = re.fullmatch(r"v(\d+)\.(\d+)(?:\.(\d+))?", tag)
    if not m:
        print(f"FAIL: tag {tag!r} is not vMAJOR.MINOR[.PATCH]")
        return 1
    major, minor, patch = (int(g or 0) for g in m.groups())
    want_name, want_code = tag[1:], major * 10000 + minor * 100 + patch
    if name.group(1) != want_name or int(code.group(1)) != want_code:
        print(f"FAIL: tag {tag} expects versionName {want_name!r} / versionCode {want_code}, "
              f"build.gradle.kts has {name.group(1)!r} / {code.group(1)}")
        return 1
    print(f"OK: {tag} matches versionName {want_name} / versionCode {want_code}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
