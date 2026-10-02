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
