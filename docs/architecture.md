# IGV-X — Architecture

High-level architecture of IGV-X and how the IGV-X work fits into the
upstream IGV codebase. Companion to `chromosome-resolution.md` (the
resolution layer), `sessions.md`, `release.md`, and `fork-diff.md` (the
patch list).

## 1. Base and branch model

- Fork of `igvteam/IGV` at `/Users/runtianwu/Rdirectory/IGV-X`.
- Base: upstream stable **2.19.X** (`org.broad.igv.*` packages), the same
  line as Runtian's installed IGV 2.19.5 — this is what the charter's
  reported NPE applies to, and what the app will replace.
- `main` tracks `upstream/main` (3.0-dev) for informational diffs only;
  IGV-X development lives on the `IGV-X` branch (see `upstream-update.md`).

## 2. Toolchain (vendored, reproducible)

| Piece | Location | Why vendored |
|---|---|---|
| JDK 21 (Temurin) | `tools/jdk-21.0.12+8/` | matches modern Gradle/Java requirements without depending on system JDK |
| Gradle 8.10.1 | via wrapper (`./gradlew`) | exact version pinning |
| Gradle user home | `GRADLE_USER_HOME=.gradle-home` (inside repo) | hermetic deps, no ~/.gradle pollution |
| Test data (committed) | `test/data/...` | hermetic regression fixtures (small) |
| Real test data (gitignored) | `test-data/` | large public files, manifest-tracked |

Build verified: `./gradlew compileJava test` green.

## 3. Module layout (as relevant to IGV-X work)

```
src/main/java/org/broad/igv/
  bbfile/          # bigWig/bigBed — IGV-X NPE fix lives here (BBFile)
  sam/             # BAM/alignment — resolution via AlignmentDataManager, ChromAliasManager
  feature/         # BED/VCF/GFF codecs + Genome alias machinery
    genome/        # Genome, GenomeManager, ChromAliasManager, alias sources
  session/         # IGVSessionReader/Writer, SessionManager (session work target)
  ui/              # Swing UI (trackpad nav, multi-select, export targets)
  batch/           # batch command listener (port 60151 style)
  util/            # misc utilities
src/test/java/org/broad/igv/
  bbfile/ChromosomeResolutionNpeTest.java
  sam/AlignmentDataManagerTest.java
  feature/genome/IGVBEDCodecTest.java, VCFWrapperCodecTest.java
```

## 4. Chromosome-resolution architecture

Detailed in `chromosome-resolution.md`; in one paragraph: the genome's
alias table is the authority, `Genome.getCanonicalChrName` is the shared
query chain (exact → alias → lowercase fallback), and each file family
plugs into it differently — tribble codecs at decode time (upstream),
BAM at query/read time via `ChromAliasManager`, and bigWig/bigBed at the
file layer where the IGV-X fix (case-insensitive lookup + `-1` sentinel
instead of null) lives. The invariant: missing mapping never NPEs.

## 5. Testing architecture

See `tests.md`. Layered: hermetic synthetic regression (CI gate) →
real-file verification (`scripts/verify_test_files.py`) → future
end-to-end real-data harness.

## 6. Planned feature areas (mapped to modules)

| Feature | Target module | Status |
|---|---|---|
| bigWig NPE fix + case-insensitive resolution | `bbfile/BBFile` | ✅ 23e8e98f8 + broadened 73416ee69 |
| BAM resolution regression | `sam/`, `feature/genome/` | ✅ bd7193891 |
| BED/VCF canonicalization regression | `feature/genome/` | ✅ f2fe0b0a1 |
| Public test manifest + verifier | `docs/`, `scripts/` | ✅ bb87bc44c, 2a086db34 |
| Large-session performance | `ui/`, `track/`, `data/` | designed (limitations.md) |
| Sessions: relative paths + companion | `session/` | designed (sessions.md) |
| UI: nav/selection/export/ranges | `ui/` | designed |
| Diagnostics + error recovery | `ui/` + new subsystem | designed |
| Packaging | scripts/package + Gradle | contract (release.md) |

## 7. Cross-cutting principles

- Reproduce → root cause → regression test → smallest maintainable fix →
  verify on multiple genomes/files → document.
- Never modify source datasets; never rename user files to fix
  chromosome mismatch.
- No broad exception swallowing; graceful degradation + honest
  diagnostics over crashes and over silent empty results.
- Everything committed and documented; real data referenced by manifest.
