# What's different in IGV-X?

IGV-X is a customized fork of the [Integrative Genomics Viewer (IGV)](https://github.com/igvteam/IGV)
built for robust genome-browser work in the lab. It stays compatible with standard IGV sessions and
standard genomics file formats, and coexists with an existing IGV install (separate app name, bundle ID,
preferences, caches, and logs).

## Chromosome-name resolution (top priority)

- Robust, alias-aware resolution of chromosome names across file types: `Chr1` / `chr1` / `CHR1` / `1` /
  RefSeq accessions (`NC_003070.9`) / organellar names (`ChrC`, `ChrM`), case-insensitive where appropriate,
  driven by genome alias info — never a global lowercase conversion.
- **Fixed**: upstream IGV 2.19.5 crashes with a NullPointerException when a bigWig track's chromosome names
  (e.g. `Chr1..ChrM`) don't exactly match the genome's canonical names (e.g. `NC_003070.9`/`chr1` on tair10).
  IGV-X resolves the mapping centrally and never crashes on a missing mapping.
- Covered by regression tests for bigWig (synthetic + real RefSeq), bigBed, BAM, BED, and VCF.

## Large-session performance

- Sessions with hundreds of tracks (e.g. ~900 methylation bigWigs) load on a **bounded thread pool** instead
  of one thread per file; the status bar updates **asynchronously** with honest `Loading session: N/total`
  progress (no memory numbers as progress, no 1800 blocked UI round-trips); mouse-hit regions are computed
  only for **visible** tracks; bigWig R-tree caches are thread-safe.

## Sessions & files

- **Relative session paths** default on (paths stored relative to the session file — sessions stay portable
  when moved with their data).
- **`.igvx.json` companion metadata** written next to saved sessions; optional — sessions load fine without it.
- **Unified smart Open**: one File > Open dialog routes files vs sessions automatically.
- **Unsaved-session close confirmation**: closing with unsaved changes asks first (all exit paths).
- **Recent-files history + welcome panel**: every open path records history; double-click to reopen.
- **Persistent bookmarks + region highlights**: saved with the session, per-region colors. Saving a bookmark no longer blocks the UI — it uses a lightweight repaint, and a 60-second watchdog releases the wait cursor if any track load genuinely hangs (e.g. OneDrive cloud placeholders), so the cursor can never spin forever.

## Navigation & region selection

- **Arabidopsis (TAIR10) gene lists built in**: View > Gene Lists… includes a
  bundled Arabidopsis group with 6 curated pathway lists (DNA methylation core,
  RdDM, small RNA, histone marks, imprinting/DME, flowering) — every AGI
  verified against the real TAIR10 GTF.
- **Trackpad navigation**: two-finger horizontal swipe pans the view (configurable sensitivity;
  `IGVX.SWIPE_PAN_ENABLED` / `IGVX.SWIPE_PAN_SENSITIVITY`). Vertical scroll is untouched.
- **ROI drag-select**: press-drag-release draws a live region selection; the classic two-click method still works.
- **Multi-track selection**: Cmd/Ctrl-click toggles, Shift-click ranges (stock behavior) — now also highlighted
  in the data panel, not just the track-name panel.
- **Configurable default quantitative-track range**: set e.g. min -5 / max 100 in Preferences → Tracks;
  blank = autoscale.

## Batch track import

- **File > Open Folder of Tracks...**: recursively scans a chosen folder
  (subfolders included) for track files (bigWig/bigBed/BAM/CRAM/VCF/BED/GFF/
  GTF/WIG/TDF/...), shows a chooser with type filter + Select All/Clear +
  Load Selected/Load All. Index and hidden files excluded.

## Organize tracks by genotype

- **Tracks > Organize Tracks by Genotype...**: one click groups WGBS tracks by
  genotype — each genotype gets a background tint + border, and within each
  genotype tracks are ordered CG → CHG → CHH with colors that stay
  consistent across genotypes (defaults: CG blue, CHG orange, CHH green).
- **Editable, remembered rules**: the dialog has two tables — genotype rules
  (name + name-pattern regex + background color) and context rules (name +
  regex + color). Add/remove rows, edit patterns/colors; Apply saves to
  preferences and reorganizes immediately.
- Tracks matching no genotype rule are auto-grouped from the name prefix
  before the methylation-context token; no-context tracks land in Ungrouped.
  No-context names are never guessed into a genotype.
- Context tokens match plain case-insensitive strings (`CG`, `CHG`, `CHH`),
  so names like `col0_CG.bw` work.
- An **Auto-organize** checkbox is in the dialog (default off); turning it
  on is the planned hook to auto-run the organize step after session/batch
  loads.

## Export

- **High-quality PNG / SVG / PDF export** in publication mode: DPI-scaled PNG, vector SVG/PDF (dependency-free
  PDF writer), selected tracks only.

## Diagnostics & error recovery

- **Diagnose Track/Session** (Tools menu + track popup): what chromosomes are in the file vs the genome,
  whether the index exists, truncated-file detection, 0/1-based hints, and network/file-format/genome/
  memory/rendering checks — with copyable output.
- **Repeated-exception dedup/rate-limit**: a burst of the same exception no longer spams the log/dialog;
  it is suppressed after the first report with a summary.

## macOS app

- Ships as **IGV-X.app** (bundle ID `org.igvx.IGVX`) with its own `~/igvx` preferences/caches — never touches
  an existing IGV install. Custom icon in Finder and Dock.
- **macOS-native integration**: screen menu bar, green-button fullscreen, Cmd+, Preferences / Cmd+Q (with
  unsaved confirm) / Finder-drop Open File, platform accelerators (Cmd+O/U/N/S/,/Q).
- **Window state remembered**: exiting maximized re-launches maximized; normal bounds restored sanely.
- **Accessibility**: VoiceOver names on command-bar buttons, Preferences dialog labels, track panel, and
  diagnose report.
- **Reproducible packaging**: `scripts/package/build_release.sh` produces the app + DMG + ZIP + SHA256SUMS
  (see `release.md`).

## Compatibility

- Reads standard IGV sessions and standard genomics formats (bigWig/bigBed/BAM/CRAM/BED/VCF/GFF/GTF/FASTA).
- Full public test corpus verified for compatibility testing.
