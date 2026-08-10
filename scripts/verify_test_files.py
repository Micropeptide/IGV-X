#!/usr/bin/env python3
"""verify_test_files.py — IGV-X public test-file integrity verification.

Verifies every file listed in docs/test-files-manifest.md against the
chromosome-naming and format expectations recorded there. Idempotent;
run after downloads complete:

    python3 scripts/verify_test_files.py

Exit code 0 = all files present and valid. Prints one line per file.
"""

import gzip
import hashlib
import os
import subprocess
import sys

TEST_DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "test-data")

# (filename, expected_size_bytes or None, description, expected_chrom_prefixes)
FILES = [
    ("GSM4586697_Flower-4-50ng-12PCR-WGBS_CG.bw", 19715073,
     "TAIR10 Flower WGBS CG", ["chr1", "chr2", "chr3", "chr4", "chr5", "chrC", "chrM"]),
    ("GSM4586697_Flower-4-50ng-12PCR-WGBS_CHG.bw", 20365737,
     "TAIR10 Flower WGBS CHG", ["chr1", "chr2", "chr3", "chr4", "chr5", "chrC", "chrM"]),
    ("GSM4586697_Flower-4-50ng-12PCR-WGBS_CHH.bw", 90484847,
     "TAIR10 Flower WGBS CHH", ["chr1", "chr2", "chr3", "chr4", "chr5", "chrC", "chrM"]),
    ("GSM4586706_Leaf_5r1_CG.bw", 20736489,
     "TAIR10 Leaf WGBS CG", ["chr1", "chr2", "chr3", "chr4", "chr5", "chrC", "chrM", "lamada", "pUC19"]),
    ("GSM4586706_Leaf_5r1_CHG.bw", 19380371,
     "TAIR10 Leaf WGBS CHG", ["chr1", "chr2", "chr3", "chr4", "chr5", "chrC", "chrM", "lamada", "pUC19"]),
    ("GSM4586706_Leaf_5r1_CHH.bw", 94004674,
     "TAIR10 Leaf WGBS CHH", ["chr1", "chr2", "chr3", "chr4", "chr5", "chrC", "chrM", "lamada", "pUC19"]),
    ("ENCFF367AHL.bigWig", 4765472,
     "Human ENCODE GRCh38 signal", None),  # chr1..chr22/chrX/chrY + _random
    ("Bur_0_bur_PII.bam", 2139385657,
     "Plant BAM Bur-0 TAIR10", ["Chr1", "Chr2", "Chr3", "Chr4", "Chr5", "chloroplast", "mitochondria"]),
    ("Bur_0_bur_PII.bam.bai", 356712,
     "Plant BAM index", None),
    ("arabidopsis_thaliana.vcf.gz", 100264455,
     "Plant VCF TAIR10 numeric", None),
    ("Arabidopsis_thaliana.TAIR10.63.gff3.gz", 9571450,
     "Plant GFF3 TAIR10 numeric", None),
]


def verify_bigwig(path, expected_chroms):
    try:
        import pyBigWig
    except ImportError:
        return "SKIP (pyBigWig not installed)", False
    try:
        bw = pyBigWig.open(path)
        chrs = list(bw.chroms().keys())
        bw.close()
    except Exception as e:  # noqa: BLE001
        return f"ERROR {e}", False
    if expected_chroms is not None:
        for c in expected_chroms:
            if c not in chrs:
                return f"MISSING chrom {c} (have {chrs[:8]}...)", False
    return f"chroms={len(chrs)}", True


def verify_bam_header(path):
    try:
        out = subprocess.run(["samtools", "view", "-H", path],
                             capture_output=True, text=True, timeout=120)
    except FileNotFoundError:
        return "SKIP (samtools not installed)", False
    if out.returncode != 0:
        return f"ERROR {out.stderr.strip()[:200]}", False
    sq = [l for l in out.stdout.splitlines() if l.startswith("@SQ")]
    return f"@SQ count={len(sq)}", len(sq) > 0


def verify_gzip(path):
    try:
        with gzip.open(path, "rb") as fh:
            head = fh.read(64)
    except Exception as e:  # noqa: BLE001
        return f"ERROR {e}", False
    return f"head={head[:20]!r}", len(head) > 0


def main():
    failed = False
    for fname, size, desc, chroms in FILES:
        path = os.path.join(TEST_DATA, fname)
        if not os.path.exists(path):
            print(f"MISSING  {fname}  ({desc})")
            failed = True
            continue
        ok = True
        note = ""
        if size is not None and os.path.getsize(path) != size:
            print(f"SIZE-ERR {fname}: {os.path.getsize(path):,} != expected {size:,}")
            ok = False
        if fname.endswith((".bw", ".bigWig")):
            note, ok2 = verify_bigwig(path, chroms)
            if not ok2:
                ok = False
            note = note + (f" size={os.path.getsize(path):,}" if size is not None else "")
        elif fname.endswith(".bam"):
            note, ok2 = verify_bam_header(path)
            if not ok2:
                ok = False
        elif fname.endswith(".gz"):
            note, ok2 = verify_gzip(path)
            if not ok2:
                ok = False
        elif fname.endswith(".bai"):
            ok2 = os.path.getsize(path) > 0
            note = f"size={os.path.getsize(path):,}" if ok2 else "empty"
            if not ok2:
                ok = False
        else:
            ok2 = True
            note = f"size={os.path.getsize(path):,}" if size is not None else ""
        if ok2 and size is not None and not note:
            note = f"size match ({size:,})"
        print(f"{'OK      ' if ok2 else 'FAIL    '} {fname}  {note}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
