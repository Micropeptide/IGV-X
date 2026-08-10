# IGV-X Changelog

All notable changes to IGV-X are documented here, per feature branch.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project is a fork of [IGV](https://github.com/igvteam/IGV) (MIT license).
Versions follow the upstream version they fork, with an `-X` suffix for IGV-X releases
(e.g. `2.19.8-X1`), unless a divergent versioning scheme is agreed.

## [Unreleased] — 2.19.X fork base

### Added

### Added (IGV-X Arabidopsis gene lists)
- **Bundled Arabidopsis (TAIR10) gene lists** in the gene-list dialog (View >
  Gene Lists…): curated `tair10.gmt` with 6 pathway lists — DNA methylation
  core, RdDM pathway, small RNA machinery, histone marks & readers,
  imprinting/DME pathway, flowering time. Every AGI code was verified against
  the real TAIR10 GTF before shipping. Registered in
  `GeneListManager.DEFAULT_GENE_LISTS`; regression test `Tair10GeneListTest`
  (registration, GMT parse, AGI well-formedness/uniqueness, expected lists).

### Added (IGV-X reproducible release packaging)
- **`scripts/package/build_release.sh`**: one-command reproducible macOS release
  packaging — release gate (full test suite), fresh `WithJava` bundle build
  with stale-app-dir cleanup (prevents leftover `IGV_user.app`-style blobs
  from bloat-carrying into the zip), IGV-X launcher/resource enforcement,
  ad-hoc or Developer ID codesign, UDZO DMG + ZIP, `SHA256SUMS`, `version.txt`
  metadata, and post-build verification (codesign verify, plist identity,
  launcher, `hdiutil verify`). Documented in `scripts/package/README.md` and
  `docs/release.md`.

### Added (IGV-X macOS integration + accessibility)
- **macOS native integration**: screen menu bar (`apple.laf.useScreenMenuBar`) so IGV-X uses the system menu bar; app name property `IGV-X`; green-button fullscreen enabled (`FullScreenUtilities` via reflection); macOS application handlers installed when supported: Preferences (Cmd+, opens Preferences), Quit (Cmd+Q now routes through the unsaved-session confirmation instead of quitting blindly), and Open File (Finder drag-and-drop onto the Dock icon loads files/sessions through the same smart routing as File > Open). All guarded by `Desktop.isSupported` so non-macOS/headless platforms are unaffected.
- **Keyboard accelerators** on the File and View menus using the platform menu shortcut (Cmd on macOS, Ctrl elsewhere): Open (Cmd+O), Load from URL (Cmd+U), New Session (Cmd+N), Save Session (Cmd+S), Preferences (Cmd+,), Exit (Cmd+Q). `MenuAction` gained an accelerator-aware constructor; headless-safe (falls back to Ctrl in unit tests).
- **Accessibility (VoiceOver/assistive tech)**: icon-only toolbar buttons in the command bar now carry explicit accessible names + descriptions (Home, Back, Forward, Refresh, ROI, Fit-to-window, Details, Ruler, Go, Search field) so screen readers describe them.
- Regression tests: `MenuActionTest` (accelerator key code + platform shortcut mask set; no accelerator when disabled).

### Added (IGV-X window state + dialog polish)
- **Window state remembered across launches**: if you exit while the main window is maximized (green zoom / maximize), IGV-X saves that state and re-maximizes on next launch; it also saves the last *non-maximized* bounds so restoring never gives you a stretched full-screen rect. Bounds are persisted on every exit path (Cmd+Q, File > Exit, red close button). New prefs: `IGVX.Frame.Maximized`; `IGVPreferences.setApplicationFrameMaximized` / `isApplicationFrameMaximized`.
- **Preferences dialog modernization**: resizable (minimum 700x480 instead of fixed), section-header font now derived from the system/L&F label font instead of a hard-coded Lucida Grande, dialog carries an accessible name/description, and every labeled control (combo boxes, color swatches, text/password fields) is associated with its label via `setLabelFor` so VoiceOver announces the preference name when focusing the field.
- **Track-name panel accessibility**: the custom-painted track name panel now exposes an accessible name + description for assistive tech.
- **Diagnose dialog accessibility**: read-only report area has an accessible name/description.
- Regression tests: `PreferencesManagerTest` gains window-state round-trips (maximized flag, bounds, zero-size bounds refusal).

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
