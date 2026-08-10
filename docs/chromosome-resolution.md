# IGV-X — Chromosome-Name Resolution

This document describes the chromosome-name resolution layer in IGV-X:
why it exists, how it works, what is covered per file type, and how it is
verified. It is the technical companion to `whats-different.md` and the
`tests/` documentation.

## 1. Problem statement

Genomics files in the wild use many different chromosome-naming conventions
for the *same* reference genome. A few real examples:

| Genome | Convention A | Convention B |
|---|---|---|
| TAIR10 (A. thaliana) | `chr1`..`chr5`, `chrC`, `chrM` (lowercase) | `Chr1`..`Chr5`, `ChrC`, `ChrM` (capitalized) |
| TAIR10 (Ensembl Plants) | numeric `1`..`5`, `Mt`, `Pt` | `chr1`..`chr5`, `chrC`, `chrM` |
| TAIR10 (RefSeq) | `NC_003070.9` (accession) | `Chr1`..`Chr5` |
| Human GRCh38 | `chr1`..`chr22`, `chrX`, `chrY` | `1`..`22`, `X`, `Y` |
| Bur-0 BAM (Mott et al. 2011) | `Chr1`..`Chr5`, `chloroplast`, `mitochondria` | `chr1`..`chr5`, `chrC`, `chrM` |

When a session says "load `Chr1`" (or a file is queried for `chrC`) but the
file stores its data under a different spelling, a naive exact-match lookup
fails. In IGV 2.19.5 this failure took the form of a hard crash:
`BBFile.getIdForChr()` returned `null`, and the caller dereferenced it →
**NullPointerException** — observed on real TAIR10 WGBS sessions with
hundreds of methylation bigWigs (data from GEO GSE151616 uses
`chrC`/`chrM` with capital C/M against a `tair10` alias genome).

The IGV-X charter requirement is a **robust chromosome-name resolution
layer** (case-insensitive where appropriate, alias-aware, using genome
alias info — NOT global lowercase conversion), and the invariant:

> **A missing chromosome mapping must NEVER NPE. Degrade gracefully,
> diagnose honestly.**

## 2. Design principles

1. **Never rename user files.** Chromosome mismatch is solved by explicit
   mapping and lookup, not by rewriting the data or the file.
2. **Never modify source datasets.**
3. **Use genome alias info.** The genome (`.genome` file, UCSC alias table,
   RefSeq aliases, user-defined aliases) is the authoritative source of
   synonym knowledge; the layer consults it rather than inventing rules.
4. **Case-insensitive where appropriate.** Naming differences in IGV are
   overwhelmingly case-only (`chr1` vs `Chr1` vs `CHR1`). Resolution is
   case-insensitive for *lookup keys* when the genome/alias table says the
   names are equivalent; it is NOT a blanket lowercase conversion of data.
5. **Smallest maintainable fix.** Prefer a narrow, documented fix at the
   point of failure over a broad refactor.
6. **Missing mapping ⇒ graceful degradation + diagnostic, never a crash.**

## 3. Where resolution happens, per file type

The IGV codebase resolves chromosome names at several layers, and each file
type takes a different path. This asymmetry is the root reason the NPE
needed a file-side fix, and why every format must be tested individually.

### 3.1 bigWig / bigBed (the original NPE)

Path: `org.broad.igv.bbfile.BBFile` → `BBFileReader` → chromosome region
lookup through the file's own B+ tree of chromosome IDs.

- **Before (2.19.5):** `getIdForChr(String)` did an exact (case-sensitive)
  lookup in the file's chromosome-name→ID map. A query for `chrC` against
a file storing `ChrC` returned `null`, and `getIdForChr`'s caller
`getChromosomeID` dereferenced it → NPE.
- **IGV-X fix (commit 23e8e98f8):** `getIdForChr` now falls back to a
  **case-insensitive scan** of the file's chromosome-name map when the exact
  lookup fails; if even that finds nothing it returns `-1` (missing-ID
  sentinel) instead of `null`, and every caller is guarded against `-1`.
  The `-1` path yields an empty query result, never an exception.
- **Coverage:** genome-alias resolution happens at the *caller* layer
  (`getCanonicalChrName`, see §4); the file-side fix guarantees the
  file's own names are found regardless of case, and that a genuinely
  unknown name degrades safely.

### 3.2 BAM / alignment files

Path: `org.broad.igv.sam.AlignmentDataManager` and readers
(`SAMReader`, `MergedAlignmentReader`) built on htsjdk.

BAM resolution works differently from bigWig: htsjdk queries the BAM by
exact sequence name, so IGV canonicalizes the **query** through the genome
layer before asking htsjdk, and `SAMAlignment` canonicalizes each record's
reference name on read via `getCanonicalChrName`.

- `AlignmentDataManager.loadInterval` resolves the requested chr:
  exact match in `sequenceNames` → `chrAliasCache` →
  `ChromAliasManager.getAliasName(chr)` (which lowercases the BAM's
  sequence names and matches the genome's alias record) → empty interval.
- `ChromAliasManager` (constructed with the BAM's sequence names) is the
  bridge that lets a genome alias like `1` → `Chr1` find the BAM's actual
  `Chr1` header name.
- Case mismatch is handled because `ChromAliasManager` stores sequence
  names lowercased and matches the (lowercased) alias values.
- **Guard:** if no alias can be found, `loadInterval` returns an empty
  interval — no NPE, no crash. (Upstream 2.19.X already did this; IGV-X
  regression tests lock it in.)

### 3.3 BED / VCF / GFF / PSL / other tribble-based formats

Path: tribble codecs (`IGVBEDCodec`, `VCFWrapperCodec`, `GFFCodec`, …)
through `Genome.getCanonicalChrName`.

- Tribble codecs canonicalize feature chromosome names at decode time via
  `getCanonicalChrName` (upstream 2.19.X behavior, commit 504eb99ed): the
  method checks an alias cache, then the genome's alias table, then — if
the name is not already lowercase — a lowercase lookup of the same name.
- **IGV-X contribution:** regression tests that lock the behavior in for
  BED and VCF (and the general canonicalization path for the rest),
  plus documentation. No code change was needed here; the important
  finding is that this family already degrades gracefully (returns the
  original name if no alias matches) — never NPEs.

### 3.4 Session loading

Session files may reference tracks by chromosome names that differ from
the file's own names. Resolution is delegated to the same
`getCanonicalChrName` layer when tracks are created/queried; sessions
themselves store names verbatim. See `sessions.md`.

## 4. The canonicalization chain (`Genome.getCanonicalChrName`)

Every file type ultimately consults the genome's canonical-name chain for
*query* names. The chain (2.19.X upstream + IGV-X hardening):

```
getCanonicalChrName(str):
  1. chrAliasCache lookup (exact)                → hit? return
  2. chromAliasSource.search(str) (genome alias table, e.g. UCSC/RefSeq)
                                                 → hit? cache + return
  3. if str is not already all-lowercase: search(str.toLowerCase())
                                                 → hit? cache + return
  4. return str unchanged                        → caller must handle
```

The **lowercase fallback (step 3) is upstream 2.19.X behavior** — IGV-X
kept it because it is the pragmatic bridge for case-only mismatches, and
documented that it exists rather than re-architecting it. The
`-1`/empty-result guards at the file and data-manager layers are IGV-X's
contribution; together they make the whole chain crash-free.

## 5. What IGV-X adds, summarized

| Layer | Upstream 2.19.X | IGV-X |
|---|---|---|
| `BBFile.getIdForChr` | exact lookup; `null` on miss → NPE | case-insensitive fallback; `-1` sentinel; guarded callers (23e8e98f8) |
| bigWig/bigBed missing chr | NPE | empty result, no crash |
| BAM query resolution | alias-aware via ChromAliasManager | same (regression-locked, bd7193891) |
| BED/VCF decode | canonicalizes via getCanonicalChrName | same (regression-locked, f2fe0b0a1) |
| Regression suite | none | `ChromosomeResolutionNpeTest` (bigWig/bigBed), BAM tests, BED/VCF tests; see `tests.md` |
| Real-data verification | none | public test-file manifest + `scripts/verify_test_files.py`; see `test-files-manifest.md` |

## 6. Verification strategy

- **Synthetic fixtures** (fast, hermetic, committed): pyBigWig-generated
  bigWigs with `Chr1`..`ChrM` capitalization, RefSeq-accession naming,
  organellar case; BAM with capital `Chr1`; VCF with `chr1` capitalized
  CHROM. These run in CI/Gradle `test` and never require network.
- **Real public files** (slow, network, uncommitted): the manifest in
  `docs/test-files-manifest.md` pins exact URLs, sizes, and expected
  chromosome names; `scripts/verify_test_files.py` checks each file;
  a `tests/` doc describes how to point the suite at them.
- **Naming matrix** (see §1) exercises: digit vs `chr`, capitalization,
  RefSeq accessions, organellar names, word names (`chloroplast`/
  `mitochondria`), spike-in contigs (`lamada`/`pUC19`), and numeric+
  `Mt`/`Pt` Ensembl Plants conventions.

## 7. Known limitations

- Resolution is genome-aware: without a loaded genome (or alias table)
  only the file-side case-insensitive fallback applies, and unknown names
  correctly produce empty results — which may look like "no data" to the
  user. The diagnostics subsystem (see `diagnostics` doc) is the intended
  way to surface this honestly.
- The lowercase fallback (step 3 in §4) is a pragmatic heuristic; it
  cannot distinguish two legitimately different chromosomes that differ
  only by case (no real genome has both, so this is theoretical).
- Word-name ↔ symbol-name aliases (`chloroplast` vs `chrC`) are only
  resolved when the genome's alias table contains them; generic word-
  name guessing is deliberately NOT implemented.
- See `limitations.md` for the full list.
