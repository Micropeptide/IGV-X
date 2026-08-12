# IGV-X v2.19.5-igvx.1 — Release Notes

> **IGV-X is a customized version of IGV built specifically for
> [micropeptide](https://github.com/micropeptide)'s research** — Arabidopsis
> epigenetics, whole-genome bisulfite sequencing (WGBS) methylation analysis,
> and large multi-track sessions. It is designed
> around those workflows while remaining fully compatible with standard IGV
> sessions and genomics file formats.

**IGV-X** is a robust, modern fork of the [Integrative Genomics Viewer (IGV)](https://github.com/igvteam/IGV)
(upstream fork base: `2.19.X` stable, commit `e2bcf4200`). It is fully compatible
with standard IGV sessions and genomics file formats, and adds a hardened
chromosome-name resolution layer, large-session performance work, and a
modernized macOS experience.

## New in this release

### In-app updates (GitHub release channel)
- **Help > Check for Updates...** — IGV-X checks `Micropeptide/IGV-X` releases
  on GitHub for a newer version and offers a one-click download of the new
  `.dmg` installer.
- Optional **startup check** (off by default): Preferences > Updates (IGV-X) >
  "Check for updates on startup".
- Version comparison understands the `vX.Y.Z-igvx.N` release scheme.

## Highlights of the IGV-X fork

### Chromosome-name resolution (top priority)
- Fixes the stock IGV 2.19.5 **bigWig NullPointerException** on TAIR10 WGBS
  sessions: `BBFile.getIdForChr()` returning `null` when hundreds of
  methylation bigWigs use `Chr1..ChrM` against a RefSeq-aliased genome.
  Missing mappings **never NPE** — they degrade gracefully and diagnose
  honestly.
- Case-insensitive, alias-aware chromosome resolution across bigWig/bigBed/
  BAM/BED/VCF: `Chr1`/`chr1`/`CHR1`/`1`/RefSeq accessions/organellar names,
  using genome alias information — **never** global lowercase conversion,
  **never** renaming user files.
- **Tools > Diagnose Track/Session...** shows exactly how each file's
  chromosomes compare to the loaded genome (exact / case-only / alias-only /
  missing), plus index and resource checks.

### Large-session performance (~900 bigWigs)
- Bounded loading threads, async track loading with honest progress, viewport
  culling, and caching — no more freezing the UI while a big session loads.

### Sessions & files
- **Relative session paths by default** (relative to the session file).
- Optional `.igvx.json` companion metadata; sessions still load if it is missing.
- Unified smart **Open** (files + sessions auto-detected) and **recent files**.
- **Cancel Session Loading** — escape a stuck session load and open another.
- **Unsaved-session close confirmation.**
- Bookmarks & gene-region highlights persisted **in the session file**.
- **Batch track import**: File > Open Folder of Tracks... scans subfolders,
  filters by type, multi-select.
- **Arabidopsis (tair10) gene lists** — DNA methylation core, RdDM pathway,
  small RNA machinery, histone marks & readers, imprinting/DME, flowering time
  (every AGI verified against TAIR10 GTF).

### Navigation & region selection
- Trackpad two-finger swipe / Magic Mouse pan (configurable sensitivity),
  without breaking vertical scroll.
- ROI drag-select, multi-track selection (Cmd/Shift-click), data-panel
  selection highlight.
- **Configurable default quantitative-track ranges** (e.g. min -5 / max 100)
  in Preferences > Tracks > Default Quantitative Range (IGV-X).

### Organize tracks by genotype
- **Tracks > Organize Tracks by Genotype...** — group tracks by genotype with
  per-genotype background tint, then order CG → CHG → CHH inside each group
  with consistent context colors across genotypes. Editable, remembered
  rules; optional auto-organize after session/batch loads.

### Export
- High-quality **PNG / SVG / PDF** export (publication mode, selected tracks
  only, DPI control).

### Diagnostics & error recovery
- Exception dedup / rate-limiting (per-type 60 s cooldown) so repeated errors
  don't spam.
- Batch command listener preserved (port 60151: new/genome/load).

### macOS app
- Native system menu bar, fullscreen, standard shortcuts, Dock icon,
  window-state memory, accessible dialogs, Retina/HiDPI, information-dense
  dark-friendly UI. App name is **IGV-X**, own bundle ID, prefs/caches/logs
  separated from stock IGV (`~/igvx`). Bundled JDK — no Java install needed.

## Downloads

- `IGV-X-2.19.5-igvx.dmg` — shareable installer (drag to Applications)
- `IGV-X-2.19.5-igvx.zip` — same app, zip form
- `SHA256SUMS` + `version.txt`

## Compatibility

Standard IGV sessions, `.igv_session.xml` / `.xml`, all standard genomics
formats (bigWig, bigBed, BAM, CRAM, VCF, BED, GFF/GTF, WIG, TDF, FASTA,
2bit...). Stock IGV 2.19.5 workflows continue to work; IGV-X adds robustness
and diagnostics on top.

## Install notes

- macOS Apple Silicon (universal build: Apple Silicon; Intel via Rosetta/
  x86_64 build on request).
- Ad-hoc signed: first launch on another Mac may require right-click → Open →
  Open (Gatekeeper). A Developer ID + notarization pass is planned.

## Source

- Repository: https://github.com/Micropeptide/IGV-X
- Branch: `IGV-X` (fork base: upstream `2.19.X`, commit `e2bcf4200`)
- Full diff vs stock IGV: `docs/fork-diff.md` · `docs/changes-vs-igv.md`
- What's different: `docs/whats-different.md` · Build: `docs/build.md`
- Limitations: `docs/limitations.md`
