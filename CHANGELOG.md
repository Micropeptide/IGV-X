# IGV-X Changelog

All notable changes to IGV-X are documented here, per feature branch.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project is a fork of [IGV](https://github.com/igvteam/IGV) (MIT license).
Versions follow the upstream version they fork, with an `-X` suffix for IGV-X releases
(e.g. `2.19.8-X1`), unless a divergent versioning scheme is agreed.

## [Unreleased] — 2.19.X fork base

### Added
- IGV-X scaffold: fork base on upstream `2.19.X` stable branch (matches IGV 2.19.5 install), `upstream` remote + `IGV-X` dev branch.
- Vendored JDK 21 (Temurin) under `tools/`; project-local Gradle home (`.gradle-home/`).
- `docs/` tree (build, architecture, chromosome-resolution, sessions, tests, upstream-update, release, limitations) + doc index.
- Public test-file download manifest (`docs/test-files-manifest.md`) + verification script (`scripts/verify_test_files.py`).

### Fixed
- **bigWig chromosome-resolution NPE** (upstream 2.19.5): `BBFile.getIdForChr()` returning null on TAIR10 WGBS sessions
  (bigWigs `Chr1..ChrM` vs genome tair10 canonical `NC_003070.9`/`chr1`). Missing mapping must never NPE. (23e8e98f8)
- BAM / BED / VCF chromosome-naming canonicalization regression tests (bd7193891, f2fe0b0a1).

See `docs/fork-diff.md` for the machine-readable patch list and `docs/whats-different.md` for the user-facing summary.
