#!/usr/bin/env python3
"""chunk_download.py — flaky-host resilient chunked download with per-chunk retry.

Downloads Bur_0_bur_PII.bam from the ENA mirror in N chunks, each fetched
with fresh range connections and retried from scratch until it matches the
expected size; then concatenates and verifies md5 against the manifest.
Resumable: chunks already complete are skipped.

Usage: python3 chunk_download.py   (run in IGV-X/test-data)
"""

import hashlib
import os
import subprocess
import sys
import time

URL = "https://ftp.ebi.ac.uk/vol1/run/ERR031/ERR031531/Bur_0_bur_PII.bam"
TOTAL = 2139385657
EXPECTED_MD5 = "629891ecac02bb32f7e16d63c63bb243"
OUT = "Bur_0_bur_PII.bam"
CHUNKS = 24
PER_ATTEMPT_TIMEOUT = 600
MAX_ATTEMPTS = 40


def main():
    size = (TOTAL + CHUNKS - 1) // CHUNKS
    ok = True
    for i in range(CHUNKS):
        start = i * size
        end = min(start + size - 1, TOTAL - 1)
        part = f"/tmp/bam_part_{i:02d}"
        want = end - start + 1
        if os.path.exists(part) and os.path.getsize(part) == want:
            print(f"PART {i} already ok ({start}-{end})", flush=True)
            continue
        done = False
        for attempt in range(1, MAX_ATTEMPTS + 1):
            if os.path.exists(part):
                os.remove(part)
            cmd = [
                "curl", "-sL", "--fail", "--retry", "3", "--retry-delay", "2", "--retry-all-errors",
                "-H", f"Range: bytes={start}-{end}", "-o", part, URL,
            ]
            try:
                r = subprocess.run(cmd, capture_output=True, text=True, timeout=PER_ATTEMPT_TIMEOUT)
                rc = r.returncode
            except subprocess.TimeoutExpired:
                rc = "TIMEOUT"
            got = os.path.getsize(part) if os.path.exists(part) else 0
            if rc == 0 and got == want:
                print(f"PART {i} ok on attempt {attempt} ({start}-{end})", flush=True)
                done = True
                break
            print(f"PART {i} attempt {attempt} rc={rc} got={got}/{want}", flush=True)
            time.sleep(2)
        if not done:
            print(f"PART {i} GAVE UP", flush=True)
            ok = False
            break
    if not ok:
        print("FAILED: not all chunks downloaded", flush=True)
        sys.exit(1)
    print("CONCAT", flush=True)
    with open(OUT, "wb") as out:
        for i in range(CHUNKS):
            with open(f"/tmp/bam_part_{i:02d}", "rb") as fh:
                out.write(fh.read())
    print(f"SIZE {os.path.getsize(OUT)}", flush=True)
    md5 = hashlib.md5()
    with open(OUT, "rb") as fh:
        for block in iter(lambda: fh.read(1 << 20), b""):
            md5.update(block)
    got_md5 = md5.hexdigest()
    print(f"MD5 {got_md5}", flush=True)
    if got_md5 == EXPECTED_MD5:
        print("MD5_MATCH", flush=True)
    else:
        print("MD5_MISMATCH", flush=True)
        sys.exit(2)


if __name__ == "__main__":
    main()
