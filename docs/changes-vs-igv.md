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

### 3.7 Cancel session loading + unified Open on welcome panel

File > **Cancel Session Loading** is enabled while a session loads; it stops
queuing more files, skips the error dialog, and returns cleanly so another
session can open immediately. The welcome panel uses the same unified
`SmartOpenMenuAction` as File > Open (auto-detects files vs sessions).
(Commit `de583ccb7`.)

### 3.8 Session-portability correctness fixes (2026-09-07/08)

Verifying 3.1 (relative paths) end-to-end against a real running app
surfaced — and this batch fixed — four real bugs that had been silently
defeating "relative session paths by default" for part of every saved
session:

- **`Track/@id` was never actually relative.** `SessionWriter` computed a
  correctly relativized id, but `AbstractTrack.marshalXML` (called right
  after) unconditionally overwrote it with the track's raw absolute id.
  `Resource/@path` had no such collision, which is exactly why a saved
  session "looked" relative (the `<Resource path=...>` line was fine) while
  every `<Track id=...>` stayed absolute.
- **A merged/combined track's member tracks had the same bug
  independently** — `MergedTracks.marshalXML` marshals its members as
  nested `<Track>` elements outside `SessionWriter`'s per-track fix, so
  they were never touched at all.
- **`index`/`coverage`/`mapping` attributes were never relativized on
  write**, and on read, a stray `resourceLocator.setCoverage(coverage)` in
  `IGVSessionReader` clobbered the correctly-resolved absolute coverage
  path with the raw (possibly relative) attribute string right after it
  was computed.
- **Relative-path computation broke across a symlinked directory** (e.g. a
  Desktop/Documents folder synced by iCloud Drive, or a mapped network
  share): the old textual comparison found no common path prefix between a
  symlinked and a non-symlinked route to the same real file and silently
  fell back to an absolute path.

All four are fixed via one shared `relativizeIfApplicable` check in
`SessionWriter` (reused by the resource path, track id, nested merged-track
ids, and index/coverage/mapping — previously four separately-maintained,
subtly different implementations) plus symlink canonicalization in
`FileUtils.getRelativePath`. Regression tests: `SessionWriterTrackIdRelativePathTest`,
`IGVSessionReaderResourcePathTest`, the symlink case in `FileUtilsTest`.
Commit `67f33c8d4`.

### 3.9 Move Session + Data Files Into Folder...

File > **Move Session + Data Files Into Folder...**: copies the current
session's file and every local file it references — main data file plus
each resource's index/coverage/mapping companion, if present — into one
chosen folder, and writes a copy of the session there with paths relative
to that folder. Filename collisions across source folders (common with
WGBS naming, e.g. two different sample folders both containing
`col0_CG.bw`) are disambiguated automatically. A local (non-bundled) genome
file is called out in the confirmation dialog as not copied.

This is a **copy**, not a move — the currently open session's identity
(path, window title, Recent Sessions entry) never changes as a side effect;
a completion dialog offers "Open Moved Session" to actually switch to the
new bundle. Stock IGV has no equivalent — moving a session folder to
another machine previously required manually tracking down and copying
every referenced file by hand. Commit `67f33c8d4`.

### 3.10 Reveal in Finder / Show Session in Finder (macOS)

Track context menu gains **Reveal Data File(s) in Finder**; File menu
gains **Show Session in Finder**. Both use `open -R` to select the file(s)
directly in a Finder window — useful before using 3.9, or just to check
where a track's file actually lives on disk. Commit `67f33c8d4`.

### 3.11 File-changed-on-disk warnings

- **On session load**: each resource's last-modified time is recorded in
  the `.igvx.json` companion at save time; opening the session later
  compares it against the file's current mtime and shows a non-blocking
  warning listing any resource that changed (e.g. silently reprocessed or
  regenerated upstream).
- **On session save**: the silent-overwrite save path (3.5's
  save-without-prompt) now checks whether the session file itself changed
  on disk since it was last opened or saved (hand edit, `git checkout`, a
  cloud-sync conflict copy) and asks for confirmation before overwriting
  instead of silently clobbering it. The batch/command-port `saveSession`
  command keeps this tracking in sync too, so a batch-driven save doesn't
  cause the next interactive save to spuriously warn.

Neither check exists in stock IGV. Commit `67f33c8d4`.

### 3.12 Remove from Recent

Right-click a Recent Files/Sessions row in the welcome panel for a **Remove
from Recent** option, instead of only the all-or-nothing "Clear Recent
Files". Commit `67f33c8d4`.

### 3.13 Window-wide drag-and-drop

Stock IGV (and IGV-X until this fix) only accepted a dropped file directly
on the track data panel. A window-wide `DropTarget` on the content pane now
covers the header/name panels and empty window space too, routed through
the same session-vs-track auto-detection as 3.4's unified Open. Commit
`67f33c8d4`.

### 3.14 Welcome-panel startup race fixed + redesign

- **Fix**: opening a session via Finder (double-click / Open With) loaded
  the tracks correctly but the "Welcome to IGV-X" panel stayed on top of
  them — a race between the async Finder-delivered load and the startup
  code's unconditional "show welcome if nothing was loaded" check (which
  only knew about CLI arguments, not an in-flight Finder open-file event).
  A failed or empty Finder-triggered open now explicitly re-shows the
  panel too, so a corrupt/unsupported file at cold launch can't leave a
  permanently blank window.
- **Redesign**: recent-file rows now show a colored file-type badge,
  filename, and parent directory (instead of one long path string); a
  dismissible one-time banner (macOS only) explains the .xml
  double-click-association limitation and links to a guided, interactive
  setup dialog with a "Reveal a Session File in Finder" shortcut, replacing
  a static Help-menu wall of text; the real IGV-X app icon replaces a
  placeholder stock-IGV image.

Commit `67f33c8d4`.

## 4. Navigation and interaction

### 4.0 Arabidopsis (TAIR10) gene lists (commit `d422f0f29`)

View > Gene Lists… now includes a bundled **Arabidopsis (tair10)** group with
6 curated pathway lists: DNA methylation core, RdDM pathway, small RNA
machinery, histone marks & readers, imprinting/DME pathway, flowering time.
Every AGI code was verified against the real TAIR10 GTF before shipping
(no dead symbols). Load any list and use the search box to jump to its genes.
Regression-tested by `Tair10GeneListTest`.

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

### 4.6 Batch track import from folder (commit `6640163af`)

File > **Open Folder of Tracks...** lets you pick a folder; IGV-X scans it and
all subfolders for loadable track files and opens a chooser dialog listing
every file (name, type), with a type filter, Select All / Clear, Load Selected
and Load All. Index files and hidden files are never offered. Backed by a pure
`TrackFolderScanner` (headless-testable) and `TrackFolderScannerTest`.

### 4.7 Organize tracks by genotype (commit `6c1d56b2b`)

Tracks > **Organize Tracks by Genotype...** groups WGBS tracks by genotype:
each genotype gets a background tint + border, and within each genotype
tracks are ordered CG → CHG → CHH with colors consistent across genotypes
(defaults: CG blue `#1f77b4`, CHG orange `#ff7f0e`, CHH green `#2ca02c`).

The rules are editable and remembered (prefs `IGVX.ORGANIZE.RULES`): a
dialog shows two tables — genotype rules (name, name-pattern regex,
background color) and context rules (name, regex, color) — with add/remove
rows and an Apply that saves and reorganizes immediately. Genotypes with no
matching rule are auto-derived from the track-name prefix before the
context token; tracks with no context token go to **Ungrouped** (never
guessed). Context tokens match plain case-insensitive strings, so names
like `col0_CG.bw` work (`\b` word boundaries would not — underscore is a
word char). An **Auto-organize** checkbox (default off) is the planned hook
to auto-run after session/batch loads.

New `org.broad.igv.organize` package: `OrganizeRules` (JSON persistence),
`TrackClassifier` (first-match-wins), `TrackOrganizer` (rebuilds
`TrackGroup`s; per-genotype background via `TrackGroup.setBackground`),
`OrganizeTracksDialog`. 15 regression tests.

### 4.8 Wait-cursor watchdog on bookmarks/highlights (commit `55a90d1a5`)

Saving a bookmark used to trigger a full track-loading repaint; if any load
hung (e.g. OneDrive cloud placeholders), the wait cursor spun forever and
`isLoading` never reset. Bookmark add/remove/highlight now use a lightweight
overlay repaint (no track loads), and the async repaint path has a 60-second
`orTimeout` watchdog that releases the wait cursor and resets `isLoading`
(load continues in background; data appears when it arrives).

### 4.9 Bookmark next/previous navigation (commit `67f33c8d4`)

Regions > **Next Bookmark** / **Previous Bookmark** (Shift+Cmd+] / Shift+Cmd+[
— deliberately not the plain Cmd+]/[ that GlobalKeyDispatcher already uses
for locus back/forward history) cycles through all bookmarks in the current
session, ordered by chromosome (genome order) then start position, wrapping
around at either end. Stock IGV has no bookmark-cycling shortcut at all.

### 4.10 Find Track... (commit `67f33c8d4`)

Tracks > **Find Track...** prompts for a name substring and highlights
every track whose name contains it (case-insensitive), reusing the
existing multi-track-selection highlight rather than introducing a second
visual style. Useful for locating one track among hundreds of
similarly-named ones — the exact WGBS-session scenario this fork targets.
Distinct from the pre-existing **Filter Tracks...** (attribute-value-based,
hides non-matches) — Find Track never hides anything.

### 4.11 Copy Image to Clipboard (commit `67f33c8d4`)

File > **Copy Image to Clipboard** renders the current view directly onto
the system clipboard as an image — paste straight into
Slides/Keynote/Word/Slack without saving a file first. Deliberately
independent of the PNG/SVG/PDF export pipeline (§5): a plain
screen-resolution raster via `Component.printAll`, not the DPI-scaled,
publication-mode-aware export path, since a clipboard paste doesn't need
either.

### 4.12 Undo/Redo + Track History (commits `5a947ee40`, `125fe64d7`)

Edit menu gains **Undo** (Cmd/Ctrl+Z) and **Redo** (Cmd/Ctrl+Shift+Z) across
track-list mutations: add/remove, drag reorder, overlay merge/unmerge,
rename, recolor, height change, sort, group-by, and genome loads. **Track
History...** lists the last 100 operations and jumps to any point in
history; the log persists in the `.igvx.json` companion. Stock IGV has no
undo mechanism for track-list edits at all.

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

### 7.5 Bundled Arabidopsis (TAIR10) genome (commit `d09316511`)

Stock IGV requires downloading a genome before you can view anything.
IGV-X bundles the Arabidopsis TAIR10 genome in the app itself: it's
extracted and registered automatically on first launch, so opening a WGBS
session against TAIR10 works with zero setup. Pairs with the curated
TAIR10 gene lists (§4.0).

### 7.6 Native jpackage launcher + Finder session-file association (commits `d9ab77001`, `74f79c9e1`, `166efd284`, `d3fcc9067`)

Stock IGV's macOS distribution used a shell-script launcher, which meant
the JVM process registered with macOS LaunchServices under the *JDK's*
bundle id, not IGV's own — so Finder "Open With" / double-click AppleEvents
for a session file never reached the running app at all. IGV-X switched
release packaging to `jpackage`, producing a native Mach-O launcher that
registers as `org.igvx.IGVX`, and:

- Declares an owned `org.igvx.session` UTI (extensions `.igvx`, `.session`,
  `.session.txt`, `.idxsession`, `.idxsession.txt`) that opens on
  double-click with zero setup.
- Registers as an **Alternate** handler for the shared `public.xml` UTI, so
  standard `.xml` IGV sessions show up in Finder's right-click "Open With"
  menu (a one-time "Change All..." in Get Info makes double-click work for
  `.xml` too, since macOS never lets an app silently take over a file type
  shared with every other XML-reading app — Help > "Open IGV Session Files
  in IGV-X..." and the Welcome-panel banner (§3.14) walk through it).
- Installs the AppleEvents open-file handler on the EDT with a buffered
  drain, so a cold-launch Finder event isn't lost while the app is still
  starting up.
- The jpackage `Info.plist` patch step is self-verifying: an earlier bug
  had `PlistBuddy Set` silently no-op on a freshly-generated, empty
  document-types array, shipping releases with no Finder association at
  all until this was caught and fixed with fail-fast verification
  (commit `d3fcc9067`) — releases now fail the build rather than ship
  silently broken.

Stock IGV does not integrate with Finder file associations at all on
macOS.

### 7.7 In-app update checking (commits `2c405193a`, `7ed7c33d4`, `cea09e235`)

Help > **Check for Updates** and a startup check against this repository's
GitHub releases feed (never any other source). First run prompts for a
check frequency (Daily / Weekly / Never); checks are silent when already
current and only surface a dialog when a newer version is actually
available. Stock IGV has no update-checking mechanism at all in the
desktop app.

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

*Last updated 2026-09-08 (release v2.19.5-igvx.4). The authoritative live
list of commits is `fork-diff.md`; this document is updated alongside it.*
