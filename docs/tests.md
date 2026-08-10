# IGV-X — Test Suite

How IGV-X tests are organized, how to run them, and what each layer
covers. Companion to `chromosome-resolution.md` (what the layer does) and
`test-files-manifest.md` (real public files).

## 1. Layers

| Layer | What it covers | Where | Network? |
|---|---|---|---|
| Unit regression (committed fixtures) | bigWig/bigBed NPE fix, BAM/BED/VCF canonicalization | `src/test/java/...` | no |
| Full build | everything in `test` task | Gradle | no |
| Real-file verification | downloaded public files vs manifest | `scripts/verify_test_files.py` | download once |
| Real-data compatibility | IGV-X loading real files end-to-end | `tests/` (doc + optional harness) | yes (files) |

## 2. Regression tests (committed, hermetic)

### `ChromosomeResolutionNpeTest` (bb package)

Location: `src/test/java/org/broad/igv/bbfile/ChromosomeResolutionNpeTest.java`

Covers the bigWig/bigBed chromosome-resolution NPE fix (commit 23e8e98f8)
and its broadening (commit 73416ee69). Fixtures are generated with
pyBigWig and committed under `test/data/bb/`:

| Fixture | Naming convention exercised |
|---|---|
| `tair10_chr_case.bigWig` | `Chr1`..`Chr5`, `ChrC`, `ChrM` (capitalized) — query `chr1`..`chrM` (lowercase) must resolve |
| `tair10_refseq.bigWig` | RefSeq accessions (`NC_003070.9` …) — alias-aware query |
| `tair10_organellar_case.bigWig` | `ChrC`/`ChrM` organellar names with mixed case |
| `chr21.refseq.bb` | real RefSeq bigBed (NC_000964.3) |

Tests assert: case-insensitive resolution returns real data; unknown
names return empty results **without throwing**; the full bb package
regression suite still passes.

### BAM tests (sam package)

Location: `src/test/java/org/broad/igv/sam/AlignmentDataManagerTest.java`
Fixture: `test/data/bam/chr_name_cap.{sam,bam,bai}` — BAM header
`Chr1` (capital) with other `chr10..` (lowercase).

- `testBamChrNameCapitalizationResolution` — end-to-end through
  `AlignmentDataManager` + `ChromAliasManager`: query `chr1` returns the
  BAM's `Chr1` reads.
- `testBamChrNameCanonicalizationOnRead` — reader-level: a read on
  `Chr1` canonicalizes to `chr1` under the test genome.

### BED / VCF tests

- `IGVBEDCodecTest.testChrNameCapitalizationCanonicalization` — genome-
  aware decode of a BED line with capital `Chr1` canonicalizes to `chr1`.
- `VCFWrapperCodecTest` — end-to-end via `TribbleFeatureSource` on
  `test/data/vcf/chr1_cap.vcf` (3 records with capital `chr1`): features
  canonicalize to the test genome's naming.

## 3. Running

```bash
cd /Users/runtianwu/Rdirectory/IGV-X
./gradlew test                      # all tests (hermetic)
./gradlew test --tests '*ChromosomeResolutionNpeTest'   # one class
./gradlew test --tests '*AlignmentDataManagerTest'
./gradlew test --tests '*IGVBEDCodecTest' --tests '*VCFWrapperCodecTest'
```

Gradle user home is vendored (`GRADLE_USER_HOME=.gradle-home`), JDK 21
vendored at `tools/jdk-21.0.12+8/` — no system install needed.

## 4. Real-file verification

After running the manifest downloads (`docs/test-files-manifest.md`), run:

```bash
python3 scripts/verify_test_files.py
```

Checks (per file): expected byte size; bigWig open + expected chromosome
names (incl. `chrC`/`chrM` and spike-ins) via pyBigWig; BAM header `@SQ`
lines via `samtools view -H`; gzip member via `gzip.open`; BAI non-empty.
Exit 0 = everything present and valid. Idempotent.

## 5. Real-data compatibility (roadmap)

The manifest's naming matrix (digit vs `chr` vs RefSeq vs organellar vs
word names) is the input to an optional end-to-end harness that loads each
file in a headless IGV session and asserts chromosome queries resolve
without exception and with the expected number of features. The harness
will be added under `tests/` and skipped automatically when the real files
are absent (they are gitignored).

## 6. Discipline

- Reproduce → root cause → regression test → smallest maintainable fix →
  test multiple genomes/files → verify existing behavior → document.
- No broad exception swallowing; a test that only checks "didn't throw"
is a smoke test, not a regression test — assert the data too.
- Fixtures are committed and hermetic; real files are referenced by
  manifest, never committed (size).
