# IGV-X Changelog

All notable changes to IGV-X are documented here, per feature branch.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project is a fork of [IGV](https://github.com/igvteam/IGV) (MIT license).
Versions follow the upstream version they fork, with an `-X` suffix for IGV-X releases
(e.g. `2.19.8-X1`), unless a divergent versioning scheme is agreed.

## [Unreleased]

Nothing yet.

## [2.19.5-igvx.4] — 2026-09-08

[Release](https://github.com/Micropeptide/IGV-X/releases/tag/v2.19.5-igvx.4) ·
[Full diff vs stock IGV](docs/changes-vs-igv.md)

### Added (IGV-X session portability & workflow, 2026-09-07/08)
- **Move Session + Data Files Into Folder...** (File menu): copies the
  current session and every local file it references (plus each resource's
  index/coverage/mapping companion file) into one chosen folder, and writes
  a copy of the session there with paths relative to that folder —
  filename collisions across source folders are disambiguated, the live
  session's identity is never changed (this writes a copy, not a move; an
  "Open Moved Session" button in the completion dialog switches to it on
  request), and a local (non-bundled) genome file is called out as not
  copied.
- **Reveal in Finder**: track context menu gets "Reveal Data File(s) in
  Finder"; File menu gets "Show Session in Finder" (macOS only).
- **Remove from Recent** (right-click a Welcome-panel Recent Files/Sessions
  row) to drop a single stale entry instead of only "Clear Recent Files".
- **File-changed warnings**: opening a session now warns (non-blocking) if
  any resource file's mtime differs from what was recorded at last save
  (tracked in `.igvx.json`); saving over an existing session file now warns
  if that file changed on disk since it was opened (hand edit, git
  checkout, a cloud-sync conflict copy) before silently overwriting it.
- **Bookmark navigation**: Regions > Next/Previous Bookmark (Shift+Cmd+]/[)
  cycles through bookmarks ordered by chromosome then position, wrapping
  around.
- **Find Track...** (Tracks menu): highlights every track whose name
  contains a given substring (via the existing multi-select highlight),
  useful for locating one track among hundreds of similarly-named ones.
- **Copy Image to Clipboard** (File menu): paste the current view directly
  into Slides/Keynote/Word without saving a file first.
- **Welcome panel redesign**: recent-file rows now show a colored
  file-type badge, filename, and parent directory (not a single long path
  string); a dismissible one-time banner (macOS) links to a guided,
  interactive "enable double-click for .xml sessions" dialog (with a
  "Reveal a Session File in Finder" shortcut) rather than a static Help
  menu wall of text; the real IGV-X app icon replaces a placeholder.
- **~200 further improvement ideas** recorded in `docs/roadmap-ideas.md`,
  unscoped brainstorm grouped by area for future work.

### Fixed (session save/load correctness, 2026-09-07/08)
- **Session save still wrote absolute paths for Track/@id** and, separately,
  for **index/coverage/mapping attributes**: `AbstractTrack.marshalXML`
  unconditionally overwrote a track's relativized id with the raw absolute
  path right after it was set, and index/coverage/mapping were never
  relativized at all on write, nor correctly resolved back to absolute on
  read (a stray `resourceLocator.setCoverage(coverage)` in
  `IGVSessionReader` clobbered the correctly-resolved value with the raw,
  possibly-relative string). A **merged/combined track's member tracks**
  had the same id-clobbering bug independently, since `MergedTracks`
  marshals its members as nested `<Track>` elements outside
  `SessionWriter`'s per-track fix. All four are now fixed via one shared
  `relativizeIfApplicable` check reused everywhere a path-bearing session
  attribute is written, plus matching resolve-on-read fixes.
- **Relative-path computation broke across a symlink** (e.g. an iCloud
  Drive-backed `~/Desktop`/`~/Documents`, or a mapped network share): both
  paths are now canonicalized before comparison.
- **"Open in IGV-X" via Finder** (double-click / right-click > Open With)
  verified fixed end-to-end against a real build — the underlying
  jpackage/PlistBuddy bug (commit d3fcc9067) left `/Applications/IGV-X.app`
  with empty document-type entries until rebuilt.
- **Drag-and-drop only worked on the track data area**: a window-wide
  `DropTarget` now covers the header/name panels and empty window space
  too, routed through the same session-vs-track auto-detection as File >
  Open.
- **A failed Finder "Open With" at cold launch could permanently hide the
  Welcome panel** for the rest of the process (nothing else would ever
  show it again); a session/track load that fails or produces zero tracks
  now explicitly re-shows it.
- **A batch/command-port `saveSession` bypassed the new "file changed on
  disk" mtime tracking**, causing the next interactive save to spuriously
  warn that the file changed when only IGV's own batch command had touched
  it.

### Fixed
- **Welcome panel stayed on top of a session loaded via Finder ("Open With" /
  double-click)** (Runtian bug report, 2026-09-07): the tracks loaded
  correctly (status bar said "Session loaded: N files"), but the "Welcome to
  IGV-X" card stayed visible over them, requiring a manual Dismiss click.
  Root cause was a startup race: a Finder/AppleEvents open-file request is
  handled as an async task on the same thread pool as normal startup, and
  `StartupRunnable` unconditionally re-showed the welcome panel based only on
  empty CLI args — with no awareness that a Finder-delivered session might
  still be loading (or have just finished) on another thread. Fixed two ways:
  `StartupRunnable` now skips showing the panel when a Finder open-file event
  has been seen this launch (`DesktopIntegration.hasSeenOpenFileEvent()`),
  and `IGV.loadSessionFromStream` now explicitly hides the panel once a
  session's tracks are actually on screen (session loading bypasses the
  `addTracks(List<Track>)` path that already did this for ordinary track
  loads, which is why plain File > Open of a *track* file never showed this
  bug).
- **Session save still wrote absolute paths for the Track/@id attribute**
  (Runtian bug report, 2026-09-07): `SessionWriter.writePanels` computed a
  correctly-relativized track id, but `AbstractTrack.marshalXML` — called
  immediately afterward — unconditionally re-set `id` from the track's own
  raw (always-absolute) field, silently clobbering it back to absolute. The
  `<Resource path=...>` element had no such collision and was already
  correct, which is why only half the session file looked relative. Fixed
  by re-applying the relativized id after `marshalXML` runs; also guarded
  against relativizing a non-path id (e.g. the synthetic "Reference
  sequence" track), which the reordering would otherwise have corrupted
  into a garbage `../../../Reference sequence` value.
- **Relative-path computation broke across a symlink** (e.g. an iCloud
  Drive-backed `~/Desktop`/`~/Documents`, or a mapped network share):
  `FileUtils.getRelativePath` compared the session file's and the data
  file's raw absolute paths textually; if either path reached the shared
  directory through a symlink, the two strings shared no common prefix and
  the method silently fell back to an absolute path. Now both paths are
  canonicalized (symlinks resolved) before comparison. Also fixed a missing
  remote-URL guard in `writeResources`/the `.igvx.json` companion writer
  that could mangle an `http(s)://` resource path if relative-path saving
  was ever applied to one.
- **"Open in IGV-X" from Finder (double-click / right-click > Open With)
  was broken on any locally-built app**: `build_app_jpackage.sh`'s
  `PlistBuddy` patch used `Set` to populate `CFBundleDocumentTypes` /
  `UTExportedTypeDeclarations` arrays that jpackage generates empty —
  `Set` cannot create an element inside a freshly-added empty array, so
  every patch silently no-op'd (already partially diagnosed and fixed at
  the script level in commit d3fcc9067, 2026-08-24; this entry documents
  verifying the fix end-to-end and shipping it in the installed
  `/Applications/IGV-X.app`, which still had the pre-fix empty document
  types). Verified end-to-end: `.xml` session files now show IGV-X in
  Finder's Open With menu and load correctly when chosen.
- **No way to drag a file onto the main window**: the only registered
  `DropTarget` was on `DataPanelContainer` (the track data area), so a file
  dropped on the header/track-name panel, or anywhere before any track was
  loaded, did nothing. Added a window-wide `DropTarget` on `IGVContentPane`
  as a fallback covering the rest of the window; drops still route through
  `SmartOpenMenuAction.openFiles` so a dropped session file loads as a
  session and a dropped track file loads as a track, same as File > Open.

### Added
- **Welcome screen redesign** (Runtian feedback, 2026-09-07): the startup
  panel now shows the real IGV-X app icon next to the title, and Recent
  Files/Sessions rows render as filename (bold) + a colored file-type badge
  + the containing directory (dim, smaller) instead of one long raw path
  string — much easier to scan once several entries share a long common
  path prefix. "Open…" is now the visually prominent default button. A
  dismissible one-time banner (macOS only, `Constants.XML_ASSOC_HINT_DISMISSED`)
  offers "Set Up…" for making `.xml` sessions open on double-click.
- **Guided .xml double-click setup** (`FileAssociationHelper`): replaces the
  old plain text-only `JOptionPane` (Help menu) with a small dialog that
  includes a "Reveal a Session File in Finder" button — it runs `open -R`
  on your most recently opened `.xml` session so the required one-time
  Finder "Get Info > Open With > IGV-X > Change All…" step starts with the
  file already found and selected, instead of making you hunt for one.
  IGV-X's own extensions (`.igvx`, `.session`, `.idxsession`) already open
  on double-click with no setup — this is only about the shared `.xml` type,
  which macOS itself requires a manual one-time step for (no app, including
  IGV-X, can silently take over a file type every other XML-reading app
  might also want).
- **Undo/Redo + Track History + save-without-prompt** (commit pending):
  - Edit menu with **Undo (Cmd/Ctrl+Z)** and **Redo (Cmd/Ctrl+Shift+Z)** across
    track-list mutations: add/remove tracks, drag reorder, overlay merge/unmerge,
    rename, recolor, height change, sort, group-by. Labels show the operation
    being undone/redone.
  - **Track History...** dialog (Edit menu) lists the last 100 operations and
    lets you jump to any point in history.
  - Track-history log persisted in the `.igvx.json` companion metadata, so
    history survives save/reload.
  - **Save-without-prompt**: File > Save Session now writes directly to the
    session's path instead of re-asking for a location (Save As... for a new
    path).
  - New `TrackHistoryManager` + `UndoMenuAction`/`RedoMenuAction`;
    regression tests `TrackHistoryManagerTest` (7), `TrackHistoryIntegrationTest`,
    extended `SessionMetadataTest` (history round-trip), new `MockTrack` test
    helper.

### Changed
- **Release pipeline now uses jpackage** (build_release.sh rewritten):
  The release script previously used the old gradle-zip-extract-and-rename
  path with a shell-script launcher — the same launcher that caused the
  Finder session-open bug (JVM registered as the JDK's bundle id, not
  org.igvx.IGVX, so macOS dropped open-file AppleEvents). The release script
  now calls `build_app_jpackage.sh` to produce the app bundle with a native
  Mach-O launcher, ensuring every published release has the Finder-open fix.
  `build_app_jpackage.sh` also gained an optional `[version]` argument.
  Post-build verification now checks for a Mach-O launcher (not a shell
  script). docs/release.md updated to reflect the jpackage flow.
- **Transparent IGV-X Dock icon**: replaced the legacy JPEG artwork stored
  under PNG/ICNS names with real RGBA PNG and alpha-bearing ICNS assets. The
  packaging script now rejects an opaque `IGV_64.png`, and
  `prepare_icon_assets.py` provides a reproducible edge-matte conversion for
  future artwork updates.
- **Update-check controls corrected and exposed**: normal startup checks are
  now silent when IGV-X is current (the dialog appears only for an available
  update), and Help > Update Settings... now edits Daily / Weekly / Never
  scheduling and startup-check behavior after first launch.

### Added
- **macOS Finder association for IGV session files**: IGV-X now registers
  itself with LaunchServices so IGV session files (.igvx, .session,
  .session.txt, .idxsession, .idxsession.txt) open in IGV-X by default when
  double-clicked, and standard IGV sessions (.xml) appear in the Finder
  right-click "Open With" menu. Help > Open IGV Session Files in IGV-X...
  explains the one-time Finder step to make .xml sessions open by default.
  Session-file detection centralized in `SessionMetadata.isSessionFile` and
  shared by the open-file handler, drag-and-drop, recent-files, and the
  command line (closes the gap where .igvx/.idxsession files were treated as
  tracks). New regression tests: `InfoPlistTemplateTest` (3), extended
  `RecentFilesTest`, `SessionMetadataTest`.

### Added (IGV-X organize tracks by genotype)
- **Tracks > Organize Tracks by Genotype...** (commit `6c1d56b2b`): group
  tracks by genotype — each genotype gets a background tint + border; within
  each genotype tracks are ordered CG → CHG → CHH with colors consistent
  across genotypes (defaults: CG blue, CHG orange, CHH green). Editable,
  remembered rules (`IGVX.ORGANIZE.RULES` prefs): genotype rules (name-pattern
  regex + background color) and context rules (regex + color), add/remove/edit
  in the dialog, Apply saves + reorganizes. Genotypes auto-derived from the
  name prefix before the context token; no-context tracks → Ungrouped (never
  guessed). Plain case-insensitive context tokens (word boundaries would fail
  on names like `col0_CG`). New `org.broad.igv.organize` package
  (`OrganizeRules`, `TrackClassifier`, `TrackOrganizer`, `OrganizeTracksDialog`),
  `TrackGroup` background support; 15 regression tests.

### Added (IGV-X wait-cursor watchdog)
- **Stuck wait cursor after Save Bookmark fixed** (commit `55a90d1a5`):
  bookmark add/remove/highlight now use a lightweight overlay repaint (no
  track loads, no wait cursor); the async repaint path got a 60-second
  `orTimeout` watchdog that releases the wait cursor and resets `isLoading`
  when a track load hangs (e.g. OneDrive cloud placeholders).

### Added (IGV-X batch track import)
- **File > Open Folder of Tracks...**: pick a folder and IGV-X recursively scans
  it (subfolders included) for loadable track files, shows a chooser dialog
  with per-file checkboxes, a type filter (bigWig/BAM/VCF/...), Select All /
  Clear, and Load Selected / Load All. Index files (`.bai`/`.tbi`/`.crai`/...)
  and hidden files are excluded automatically. Core scanner is pure
  (`TrackFolderScanner`) and regression-tested by `TrackFolderScannerTest`.

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
