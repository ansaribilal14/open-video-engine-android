#!/bin/sh
# Gate: EVERY PT_LOAD segment of an Android .so must be 16KB-aligned.
#
# Android 15+ devices with 16KB memory pages refuse to load 4KB-aligned
# libraries — v0.1.1 shipped exactly such a lib (root cause of the reported
# "engine bridge failure — nothing was created"). release.yml already gates
# on the FIRST LOAD segment via readelf; this script is the stricter form:
# every PT_LOAD segment, no shell-awk parsing fragility, reusable for the
# packaged-APK check and local builds.
#
# Usage: check-16kb.sh <path-to.so> [<path-to.so> ...]
# Exit 0 = all libs conform; exit 1 = any violation.

if [ "$#" -eq 0 ]; then
    echo "usage: $0 <lib.so> [...]" >&2
    exit 1
fi

if ! python3 - "$@" <<'EOF'
import struct, sys

fail = False
for path in sys.argv[1:]:
    print(f"checking {path}")
    try:
        b = open(path, "rb").read()
    except OSError as e:
        print(f"  FAIL: cannot read ({e})", file=sys.stderr)
        fail = True
        continue
    if b[:4] != b"\x7fELF" or b[4] != 2 or b[5] != 1:
        print("  FAIL: not a little-endian ELF64", file=sys.stderr)
        fail = True
        continue
    e_phoff = struct.unpack_from("<Q", b, 32)[0]
    e_phentsize = struct.unpack_from("<H", b, 54)[0]
    e_phnum = struct.unpack_from("<H", b, 56)[0]
    n = 0
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from("<I", b, off)[0]
        if p_type != 1:  # PT_LOAD
            continue
        p_align = struct.unpack_from("<Q", b, off + 8 + 40)[0]
        n += 1
        if p_align < 16384:
            print(f"  FAIL: PT_LOAD align {p_align} < 16384 — 16KB-page devices refuse to load", file=sys.stderr)
            fail = True
    if n == 0:
        print("  FAIL: no PT_LOAD segments parsed", file=sys.stderr)
        fail = True
    elif not fail:
        print(f"  ok: {n} PT_LOAD segments, all >= 16384")

sys.exit(1 if fail else 0)
EOF
then
    echo "16KB-ALIGNMENT GATE FAILED" >&2
    exit 1
fi
echo "16KB-ALIGNMENT-OK"
