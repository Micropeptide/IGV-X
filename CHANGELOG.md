# IGV-X Changelog

All notable changes to IGV-X are documented here, per feature branch.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project is a fork of [IGV](https://github.com/igvteam/IGV) (MIT license).
Versions follow the upstream version they fork, with an `-X` suffix for IGV-X releases
(e.g. `2.19.8-X1`), unless a divergent versioning scheme is agreed.

## [Unreleased] — 2.19.X fork base

### Added

### Added (IGV-X diagnostics + error recovery)
- **Diagnose Track/Session (Tools menu + track popup)**: new `org.broad.igv.diagnostic` package — `TrackDiagnostics` inspects a track and reports resource presence (local/remote, empty file), index existence (BAM/CRAM `.bai`/`.crai`, Tribble `.idx`/`.tbi`, self-indexed bigWig/bigBed/TDF), the chromosome-resolution comparison (exact / case-insensitive / alias-aware vs the current genome, with unmatched names listed), and JVM heap pressure. `DiagnoseDialog` shows a copyable report; safe to run on any track — never renames user files, never mutates state.
- Public chromosome-name accessors for diagnostics: `TribbleFeatureSource.getChromosomeNames()` and `TDFDataSource.getChromosomeNames()` (read-only, sorted/immutable snapshot).
- **Exception dedup/rate-limit**: `ExceptionRateLimiter` — repeated identical exceptions (same root-cause class + message) are logged once per 60s cooldown window with a suppressed-count summary; wired into `DefaultExceptionHandler` so an exception storm (e.g. missing chromosome per paint during a large session) no longer floods the log.
- Regression tests: `ExceptionRateLimiterTest` (first-log, dedup, per-signature independence, window expiry summary, root-cause signature), `TrackDiagnosticsTest` (exact/case/alias/missing chromosome comparison on a TAIR10-like genome).
- IGV-X scaffold: fork base on upstream `2.19.X` stable branch (matches IGV 2.19.5 install), `upstream` remote + `IGV-X` dev branch.
- Vendored JDK 21 (Temurin) under `tools/`; project-local Gradle home (`.gradle-home/`).
- `docs/` tree (build, architecture, chromosome-resolution, sessions, tests, upstream-update, release, limitations) + doc index.
- Public test-file download manifest (`docs/test-files-manifest.md`) + verification script (`scripts/verify_test_files.py`); full 11-file corpus (incl. 2.1 GB EBI BAM) downloaded + MD5-verified.
- macOS app branding: `IGV-X.app` installable (separate `~/igvx` prefs/caches, `org.igvx.IGVX` bundle ID), Runtian's icon as bundle icon + runtime Dock icon, CWD-independent shell launcher.
- **Recent-files history + welcome panel**: startup panel lists recent files and recent sessions (double-click to reopen); history now recorded on every open path (menu, URL, drag-and-drop, command line, batch commands); Recent Files menu routes sessions safely; stale local entries pruned from lists.
- Trackpad navigation: horizontal two-finger swipe pans the view (shift+wheel), configurable via `IGVX.SWIPE_PAN_ENABLED` / `IGVX.SWIPE_PAN_SENSITIVITY`; vertical scroll untouched.
- ROI define drag-select: press-drag-release draws a live selection; two-click still works.
- Large-session performance (roadmap item 1): bounded session-load thread pool, async status-bar updates with honest `N/total` progress, viewport-culled mouse-region computation, scaled track-load pool, thread-safe bigWig R-tree cache.

### Fixed
- **bigWig chromosome-resolution NPE** (upstream 2.19.5): `BBFile.getIdForChr()` returning null on TAIR10 WGBS sessions
  (bigWigs `Chr1..ChrM` vs genome tair10 canonical `NC_003070.9`/`chr1`). Missing mapping must never NPE. (23e8e98f8)
- BAM / BED / VCF chromosome-naming canonicalization regression tests (bd7193891, f2fe0b0a1).
- Upstream `MacOS/IGV` launcher CWD-sensitivity (exit 255 outside `Contents`) replaced with robust shell launcher.
- `ApplicationStatusBar.setMessage3` blocked the EDT with per-file `invokeAndWait`+`paintImmediately`; now async + `repaint`, and a bug painting `messageBox2` bounds was fixed.
- Dock icon showed the stock IGV icon at runtime: the launcher's `-Xdock:icon` PNG was separate from the bundle icns — both now use Runtian's icon.

See `docs/fork-diff.md` for the machine-readable patch list and `docs/whats-different.md` for the user-facing summary.
