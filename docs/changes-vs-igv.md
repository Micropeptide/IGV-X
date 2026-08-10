# IGV-X — All Changes Compared to Stock IGV

This is the complete, detailed record of every deliberate change IGV-X
makes relative to stock [IGV](https://github.com/igvteam/IGV) (upstream
`2.19.X` line). It is the human-readable companion to the machine-readable
patch list in [`fork-diff.md`](fork-diff.md) and the user-facing summary in
[`whats-different.md`](whats-different.md).

Each change notes where it lives in the UI, which preferences control it
(when applicable), and the commit that landed it.

---

## 1. Chromosome-name resolution (the core fix)

**Problem in stock IGV:** chromosome names must match the genome's canonical
names exactly. On a TAIR10 WGBS session with hundreds of methylation
bigWigs whose tracks use `Chr1..ChrM`, while the tair10 genome alias table
uses RefSeq accessions (`NC_003070.9`...) and `chr1`-style names, IGV
2.19.5 throws a `NullPointerException` in `BBFile.getIdForChr()` — a
missing mapping crashes the viewer.

**IGV-X behavior:** a robust, alias-aware resolution layer.

- Resolves `Chr1` / `chr1` / `CHR1` / `1` / RefSeq accessions
  (`NC_003070.9`) / organellar names (`ChrC`, `ChrM`) — case-insensitive
  where appropriate — using the genome's alias table, **never** a global
  lowercase conversion.
- bigWig/bigBed get a **file-side case-insensitive lookup** so the file's
  own naming can be reconciled before asking the genome.
- **A missing mapping never crashes**: unknown names degrade to empty
  results, and the diagnostics subsystem explains why (see §8).
- Regression tests cover synthetic bigWig (case, RefSeq-accession,
  organellar), real RefSeq bigBed, BAM case-mismatch, and BED/VCF
  canonicalization.

Commits: `23e8e98f8` (NPE fix), `73416ee69` / `bd7193891` /
`f2fe0b0a1` (test broadening). Full design: `docs/chromosome-resolution.md`.

## 2. Large-session performance (~900 bigWig tracks)

**Problem in stock IGV:** `IGVSessionReader.processResources` spawns **one
raw OS thread per data file** (with an upstream TODO to bound it). A
~900-track WGBS session means ~900 threads, ~900 concurrent file opens, a
context-switch storm, and an unsynchronized shared `errors` list.

**IGV-X behavior (commit `674efb2fc`):**

- **Bounded thread pool** for session loading (no more 900 OS threads).
- **Async status bar** with honest `Loading session: N/total` progress
  (never memory numbers as progress).
- **Viewport-culled mouse-hit regions**: only visible tracks compute
  mouse regions during load.
- **Thread-safe R-tree caches** for bigWig data.
- Kept the synchronous path for batch mode.

Verified with a synthetic ~900-track session; standing validation target
is a real WGBS session. Full analysis: `docs/performance-analysis-large-sessions.md`.

## 3. Session files

### 3.1 Relative session paths (default ON)

Stock IGV defaults to absolute paths in saved sessions. IGV-X defaults to
**relative** paths (`SESSION.RELATIVE_PATH=true`), so moving a session
folder to another machine keeps every track working as long as the data
moves with it. Toggle: Preferences → General → "Use relative paths in
session files". (Commit `c52d4cc85`.)

### 3.2 `.igvx.json` companion metadata (optional)

Every saved session `session.xml` may be accompanied by `session.igvx.json`
— a small JSON file recording save-time facts (relative-path mode, track
count, save timestamp, session filename). It is **optional**: a session
loads perfectly if missing or corrupt. Written by `SessionMetadata`
(Gson-based, matching the project's JSON stack), read after the XML
parses. (Commit `c52d4cc85`.)

### 3.3 Bookmarks + region highlights (session-persisted)

- Regions → **Add Bookmark**: bookmark the current view with a label;
  **Bookmarks…** opens a manager (list, jump-to, toggle highlight,
  delete).
- Right-click any region bar → **Bookmark this region**.
- Bookmarks persist in the session file (`<Bookmarks>…</Bookmarks>`) and
  come back on reload, highlight included.
- Highlighted bookmarks draw as colored vertical lines.

Commit `1f9d14387`.

### 3.4 Unified smart Open

Stock IGV has separate File → Open (tracks) and File → Open Session,
which errors if you pick the wrong kind. IGV-X has **one** Open that
auto-detects: session files (`.xml`, `.session`, `.session.txt`) load as
sessions; everything else loads as tracks. Works with multi-selection and
records history. (Commit `0f88e2d77`.)

### 3.5 Unsaved-session close confirmation

Closing the window (red button, Cmd+Q, File → Exit) with tracks
loaded/changed since the last save asks for confirmation instead of
silently discarding. (Commit `0f88e2d77`.)

### 3.6 Recent-files history + welcome panel

Every open path records history; the welcome panel offers recent sessions
and files to double-click and reopen. (Commit `84b65b725`.)

## 4. Navigation and interaction

### 4.1 Trackpad navigation

Two-finger **horizontal swipe pans the view** (works on trackpads and
Magic Mouse). Sensitivity is configurable via prefs
`IGVX.SWIPE_PAN_ENABLED` / `IGVX.SWIPE_PAN_SENSITIVITY`;
vertical scroll is untouched. (Commit `e752fc14e`.)

### 4.2 ROI define supports drag-select

Stock ROI is two-click only. IGV-X also supports press-drag-release with
live feedback. (Commit `e752fc14e`.)

### 4.3 Multi-track selection visible in the data panel

Stock Cmd/Ctrl-click toggle and Shift-click range selection exist, but the
selection was only visible in the name panel. IGV-X paints a clear blue
highlight (border + subtle fill) on **selected tracks in the data panel
too** — and the highlight is never exported into images. (Commit
`86da40d31`.)

### 4.4 Configurable default quantitative-track range

Preferences → Tracks → **Default Quantitative Range (IGV-X)**: set e.g.
min −5 / max 100 for new quantitative tracks; blank = autoscale. Applied
when a quantitative track is created and both values are valid.
(Commit `86da40d31`.)

## 5. High-quality export

File → **Save Publication Image…** (commit `7e87a86b6`):

- Formats: **PNG, SVG, PDF**. PDF is new vs stock IGV (which only does
  PNG/SVG) — a dependency-free PDF writer that works in Preview/Acrobat
  and honors DPI so page size is physically correct.
- **DPI scaling**: PNG supports real DPI (default 300 for print;
  stock IGV is 96-dpi only).
- **Selected-tracks-only**: export just the selected tracks.
- **Publication mode**: clean output without UI chrome.

## 6. Diagnostics + error recovery

### 6.1 Diagnose Track/Session

Tools → **Diagnose Track/Session…** (and right-click a track → Diagnose
Track…), commit `30da7ec8f`. One click inspects a track and reports:

- **Resource**: file exists? empty? local vs remote?
- **Index**: BAM/CRAM `.bai`/`.crai`, VCF/BED `.idx`/`.tbi` — present or
  missing?
- **Chromosomes**: which file chromosomes are in the genome exactly,
  which are case-only mismatches, which resolve via aliases (RefSeq
  accessions, organellar), which have no mapping — and the JVM heap
  state.
- Copyable report.

### 6.2 Exception dedup / rate-limit

Repeated exceptions of the same type are suppressed after the first
report (per-type cooldown) and summarized — a burst no longer spams the
log/dialog. (Commit `30da7ec8f`.)

### 6.3 Batch command listener preserved

The port-60151-style batch command listener (`new`/`genome`/`load` over
TCP) is verified intact from upstream.

## 7. macOS app integration

### 7.1 Identity and separation

- App name **IGV-X**, bundle ID `org.igvx.IGVX` — never collides with
  stock IGV (`org.broad.igv`).
- Own data locations: `~/igvx` preferences/caches/logs and `~/.igvx`;
  never touches `~/igv`, `~/.igv`, or stock IGV state.
- Custom icon in Finder and Dock (Runtian's design).

Commits `31b3416d1`, `ec655e300`, `159a41ca2`, `c8996ff63`, `fa69b2c79`
(dock icon root-caused: runtime `-Xdock:icon` PNG and classpath resource
were both fixed, not just the bundle `.icns`).

### 7.2 macOS-native behavior (commit `ea6a25a8e`)

- **Screen menu bar** (`apple.laf.useScreenMenuBar`) — IGV-X lives in the
  macOS menu bar like a native app.
- **Green-button fullscreen** works.
- **App handlers**: Preferences (Cmd+,), Quit (Cmd+Q — routed through the
  unsaved-session confirmation), Open File (drag onto Dock icon → smart
  open).
- **Platform accelerators**: Cmd+O Open, Cmd+U Load from URL, Cmd+N New
  Session, Cmd+S Save Session, Cmd+, Preferences, Cmd+Q Quit.
- All guarded by `Desktop.isSupported` so non-macOS/headless platforms
  are unaffected.

### 7.3 Window state memory (commit `7c91b16ab`)

Exit while maximized → relaunch maximized; the last **normal** bounds are
saved too, so restore never produces a stretched screen-sized rect. Works
on every exit path (Cmd+Q, File → Exit, red button).

### 7.4 Accessibility (VoiceOver / assistive tech)

- Icon-only toolbar buttons carry explicit accessible names/descriptions
  (Home, Back, Forward, Refresh, ROI, Fit-to-window, Details, Ruler, Go,
  Search field). (Commit `ea6a25a8e`.)
- Preferences dialog: resizable (min 700×480), section headers use the
  system font, every field/combo/color-picker is labeled for VoiceOver
  (`setLabelFor`). (Commit `7c91b16ab`.)
- Track-name panel and Diagnose report area have accessible identities.

## 8. Reproducible packaging

`scripts/package/build_release.sh` (commit `32f84d59e`) produces a
complete release from one command:

1. Release gate: full test suite.
2. Fresh app bundle with bundled JDK — cleans stale zips and the
   leftover-app dir (prevents `IGV_user.app`-style blobs from silently
   bloating the artifact).
3. IGV-X launcher + icon enforcement.
4. Codesign: ad-hoc by default; Developer ID with hardened runtime via
   `--sign` (or `IGVX_DEV_ID` env).
5. UDZO DMG + ZIP.
6. `SHA256SUMS` + `version.txt` (version, build date, git head/describe,
   signing identity, JDK path).
7. Post-build verification: codesign verify, plist identity, launcher,
   `hdiutil verify`.

Notarization is the remaining future step (needs Runtian's Developer ID
certificate + Apple credentials; see `docs/release.md`).

## 9. Docs and engineering hygiene

- Full docs tree: architecture, chromosome-resolution, sessions, tests,
  test-data, upstream-update, release, limitations, what's-different,
  fork-diff, build, this file.
- Machine-readable patch list (`fork-diff.md`) with a feature→file map,
  used as the survival checklist before every upstream merge.
- CHANGELOG.md maintained per feature batch.
- Upstream-update workflow documented and verified: backup → fetch →
  diff → deliberate merge → reapply if needed → full regression → update
  report → promote only on green.

---

*Last updated 2026-08-10. The authoritative live list of commits is
`fork-diff.md`; this document is updated alongside it.*
