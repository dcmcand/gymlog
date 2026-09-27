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
        if extra:
            print(f"FAIL: entry names differ: {extra[:10]}")
        else:
            i = next(i for i, (a, b) in enumerate(zip(ns, nu)) if a != b)
            print(f"FAIL: entry order differs at #{i}: {ns[i]} vs {nu[i]}")
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
