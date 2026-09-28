"""Tests for apk_check.py (stdlib only): python3 -m unittest discover -s scripts -p 'test_*.py'"""
import contextlib
import io
import os
import struct
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(__file__))
import apk_check  # noqa: E402

V2, DEP, PAD = 0x7109871A, 0x504B4453, 0x42726577


def make_zip(entries):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for name, data in entries:
            z.writestr(name, data)
    return buf.getvalue()


def add_signing_block(zipdata, pairs, size=None, trailing_size=None):
    """Insert an APK Signing Block before the central directory, like apksigner does."""
    end = zipdata.rfind(b"PK\x05\x06")
    cd = struct.unpack("<I", zipdata[end + 16:end + 20])[0]
    body = b"".join(struct.pack("<QI", len(v) + 4, i) + v for i, v in pairs)
    real = len(body) + 24  # pairs + trailing size field + magic
    lead = struct.pack("<Q", real if size is None else size)
    tail = struct.pack("<Q", real if trailing_size is None else trailing_size) + apk_check.MAGIC
    block = lead + body + tail
    eocd = zipdata[end:end + 16] + struct.pack("<I", cd + len(block)) + zipdata[end + 20:]
    return zipdata[:cd] + block + zipdata[cd:end] + eocd


class ApkCheckTest(unittest.TestCase):
    def run_tool(self, *args):
        paths = []
        for data in args:
            f = tempfile.NamedTemporaryFile(suffix=".apk", delete=False)
            f.write(data)
            f.close()
            paths.append(f.name)
        out = io.StringIO()
        try:
            with contextlib.redirect_stdout(out):
                cmd = "signing-block" if len(paths) == 1 else "compare"
                code = apk_check.main(["apk_check.py", cmd, *paths])
        finally:
            for p in paths:
                os.unlink(p)
        return code, out.getvalue()

    unsigned = make_zip([("a.txt", b"hello"), ("b.txt", b"world")])

    def test_signing_block_cases(self):
        cases = [
            ("v2 signature and padding", add_signing_block(self.unsigned, [(V2, b"sig"), (PAD, b"\0" * 8)]), 0, "OK"),
            ("dependency blob", add_signing_block(self.unsigned, [(V2, b"sig"), (DEP, b"x")]), 1, "DEPENDENCY_INFO"),
            ("unsigned", self.unsigned, 1, "no APK Signing Block"),
            ("no signature in block", add_signing_block(self.unsigned, [(PAD, b"\0" * 8)]), 1, "no signature"),
            ("block size below the 24-byte minimum", add_signing_block(self.unsigned, [], size=16, trailing_size=16), 1, "malformed"),
            ("size fields disagree", add_signing_block(self.unsigned, [(V2, b"sig")], size=999), 1, "malformed"),
            ("absurd size", add_signing_block(self.unsigned, [(V2, b"sig")], size=10**12, trailing_size=10**12), 1, "malformed"),
        ]
        for name, data, code, text in cases:
            with self.subTest(name):
                got, out = self.run_tool(data)
                self.assertEqual(got, code, out)
                self.assertIn(text, out)

    def test_compare_cases(self):
        signed = add_signing_block(self.unsigned, [(V2, b"sig")])
        dup = make_zip([("a.txt", b"hello"), ("b.txt", b"world"), ("b.txt", b"world")])
        cases = [
            ("identical", signed, self.unsigned, 0, "OK"),
            ("different contents", signed, make_zip([("a.txt", b"hello"), ("b.txt", b"WORLD")]), 1, "contents differ"),
            ("different order", signed, make_zip([("b.txt", b"world"), ("a.txt", b"hello")]), 1, "order differs"),
            ("duplicate entry", signed, dup, 1, "entry count differs"),
            ("second argument is signed", signed, signed, 1, "is signed"),
        ]
        for name, a, b, code, text in cases:
            with self.subTest(name):
                with warnings_off():
                    got, out = self.run_tool(a, b)
                self.assertEqual(got, code, out)
                self.assertIn(text, out)


@contextlib.contextmanager
def warnings_off():
    import warnings
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")  # zipfile warns about the deliberate duplicate name
        yield


if __name__ == "__main__":
    unittest.main()
