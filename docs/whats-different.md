# What's different in IGV-X?

IGV-X is a customized fork of the [Integrative Genomics Viewer (IGV)](https://github.com/igvteam/IGV)
built for robust genome-browser work in the lab. It stays compatible with standard IGV sessions and
standard genomics file formats, and coexists with an existing IGV install (separate app name, bundle ID,
preferences, caches, and logs).

## Highlights (as they land)

### Chromosome-name resolution (top priority)
- Robust, alias-aware resolution of chromosome names across file types: `Chr1` / `chr1` / `CHR1` / `1` /
  RefSeq accessions (`NC_003070.9`) / organellar names (`ChrC`, `ChrM`), case-insensitive where appropriate,
  driven by genome alias info — never a global lowercase conversion.
- **Fixed**: upstream IGV 2.19.5 crashes with a NullPointerException when a bigWig track's chromosome names
  (e.g. `Chr1..ChrM`) don't exactly match the genome's canonical names (e.g. `NC_003070.9`/`chr1` on tair10).
  IGV-X resolves the mapping centrally and never crashes on a missing mapping.

### Recent-files history + welcome panel
- A **Welcome panel** shows at startup with your recently opened files and sessions — double-click any entry
  to reopen it, or use the buttons to open a file/session or clear history.
- History is now recorded on **every** open path: File menu, Load from URL, drag-and-drop, command line, and
  batch commands (stock IGV only recorded menu-based loads). Entries persist in `~/igvx/prefs.properties`.
- The **Recent Files** menu routes session files to the session loader (never parses a session as data), and
  stale local entries (deleted files) are pruned from the lists automatically.

### Large-session performance
- Sessions with hundreds of tracks (e.g. ~900 methylation bigWigs) load on a **bounded thread pool** instead
  of one thread per file, the status bar updates **asynchronously** with honest `Loading session: N/total`
  progress (no more 1800 blocked UI round-trips), and mouse-hit regions are computed only for **visible**
  tracks. Track-load pool scales with your CPU; bigWig R-tree caches are thread-safe.

### Navigation & region selection
- **Trackpad navigation**: two-finger horizontal swipe pans the view (configurable sensitivity;
  `IGVX.SWIPE_PAN_ENABLED` / `IGVX.SWIPE_PAN_SENSITIVITY`). Vertical scroll is untouched.
- **ROI drag-select**: press-drag-release draws a live region selection; the classic two-click method still works.

### macOS app
- Ships as **IGV-X.app** with its own `~/igvx` preferences/caches (never touches an existing IGV install),
  your custom icon in Finder and the Dock, and a robust CWD-independent launcher.
- Full public test corpus (bigWig/bigBed/BAM/CRAM/BED/VCF/GFF/GTF/FASTA/sessions) verified for compatibility testing.

_(More features documented as they land — relative paths, bookmarks, multi-track selection, export, diagnostics.)_
