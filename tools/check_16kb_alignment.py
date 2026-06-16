#!/usr/bin/env python3
"""Audit an APK's native libraries for 16 KB page-size compatibility.

For every `lib/<abi>/*.so` entry it reports two independent things:

  * ZIP    — whether the entry is STORED (uncompressed, page-aligned, loadable
             directly via mmap) or DEFLATED (compressed → must be extracted).
  * ELF    — the maximum p_align across the file's PT_LOAD segments. To run on a
             16 KB-page device every LOAD segment must be aligned to >= 16384.

A library is 16 KB-ready when it is STORED *and* its LOAD alignment is >= 16384.
"Unknown error" in Android's on-device check usually maps to a DEFLATED entry;
"LOAD segment not aligned" maps to an ELF whose alignment is 4096.

Usage: python tools/check_16kb_alignment.py <path-to.apk> [--abi arm64-v8a]
"""
import argparse
import struct
import sys
import zipfile

P_ALIGN_OK = 16 * 1024


def max_load_alignment(data: bytes) -> int | None:
    """Return the largest PT_LOAD p_align in an ELF blob, or None if not ELF."""
    if data[:4] != b"\x7fELF":
        return None
    is64 = data[4] == 2
    little = data[5] == 1
    end = "<" if little else ">"
    if is64:
        e_phoff = struct.unpack_from(end + "Q", data, 0x20)[0]
        e_phentsize = struct.unpack_from(end + "H", data, 0x36)[0]
        e_phnum = struct.unpack_from(end + "H", data, 0x38)[0]
    else:
        e_phoff = struct.unpack_from(end + "I", data, 0x1C)[0]
        e_phentsize = struct.unpack_from(end + "H", data, 0x2A)[0]
        e_phnum = struct.unpack_from(end + "H", data, 0x2C)[0]

    best = 0
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from(end + "I", data, off)[0]
        if p_type != 1:  # PT_LOAD
            continue
        p_align = struct.unpack_from(end + "Q" if is64 else end + "I",
                                     data, off + (0x30 if is64 else 0x1C))[0]
        best = max(best, p_align)
    return best


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("apk")
    ap.add_argument("--abi", default="arm64-v8a")
    args = ap.parse_args()

    bad = []
    with zipfile.ZipFile(args.apk) as z:
        libs = [i for i in z.infolist()
                if i.filename.startswith(f"lib/{args.abi}/") and i.filename.endswith(".so")]
        if not libs:
            print(f"No lib/{args.abi}/*.so entries found.")
            return 1
        width = max(len(i.filename.split("/")[-1]) for i in libs)
        print(f"{'library'.ljust(width)}  {'zip':9}  {'LOAD align':>10}  status")
        print("-" * (width + 32))
        for info in sorted(libs, key=lambda i: i.filename):
            name = info.filename.split("/")[-1]
            stored = info.compress_type == zipfile.ZIP_STORED
            align = max_load_alignment(z.read(info.filename))
            align_txt = "n/a" if align is None else (f"{align // 1024}K" if align else "0")
            ok = stored and align is not None and align >= P_ALIGN_OK
            status = "OK" if ok else "NEEDS FIX"
            if not ok:
                bad.append(name)
            print(f"{name.ljust(width)}  {'STORED' if stored else 'DEFLATED':9}  "
                  f"{align_txt:>10}  {status}")

    print()
    if bad:
        print(f"{len(bad)} library(ies) not 16 KB-ready: {', '.join(bad)}")
        return 2
    print("All native libraries are 16 KB-ready.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
