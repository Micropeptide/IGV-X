# IGV-X — Known Limitations

Honest, current list of what IGV-X does not yet do, or does only
partially. Updated as features land; do not let this file go stale.

## 1. Chromosome resolution

- Without a loaded genome/alias table, only the file-side
  case-insensitive fallback applies; unknown names return empty results
  (no crash, but possibly "no data" to the user). The diagnostics
  subsystem is the intended honest surface for this.
- The lowercase fallback in `getCanonicalChrName` cannot distinguish two
  legitimately different chromosomes differing only by case. No real
  genome has both, so this is theoretical.
- Word-name ↔ symbol-name aliases (`chloroplast` vs `chrC`) only resolve
  when the genome's alias table contains them; no generic word-name
  guessing is implemented (deliberate).
- CRAM support: inherited from upstream/htsjdk; not yet exercised against
  real public CRAM in the compatibility suite (manifest has no CRAM file
  yet — add one).

## 2. Large sessions (~900 bigWig tracks)

- **Implemented** (commit 674efb2fc): bounded session-load thread pool,
  async status bar with honest `Loading session: N/total` progress,
  viewport-culled mouse-hit regions, thread-safe R-tree caches. Verified
  with a synthetic ~900-track session; real 900-bigWig WGBS sessions are
  the standing end-to-end validation target.
- Remaining: lazy track *initialization* on first view (tracks are still
  created up front, though loaded asynchronously), and progressive
  rendering during load are not yet fully separated.

## 3. Sessions

- **Implemented**: relative paths default on, `.igvx.json` companion
  (optional; session loads if missing), bookmarks + region highlights
  persist with the session, unified smart Open, unsaved-session close
  confirmation. See `sessions.md`.

## 4. UI / interaction

- **Implemented**: trackpad swipe navigation (configurable sensitivity),
  ROI drag-select, multi-track selection (stock Cmd/Shift-click +
  data-panel highlight), high-quality PNG/SVG/PDF export (publication
  mode, selected tracks only), configurable quantitative-track default
  ranges, window-state memory, Preferences dialog polish + a11y, macOS
  screen menu bar / fullscreen / app handlers / accelerators, organize
  tracks by genotype (CG/CHG/CHH grouping + tint + editable rules,
  commit 6c1d56b2b), wait-cursor watchdog on hung track loads
  (commit 55a90d1a5).
- **Not done**: dark mode (deliberately out of scope per charter),
  remaining stock-IGV dialog/track-header polish, deeper VoiceOver pass
  beyond the command bar / dialogs / track panel.
- HiDPI/Retina: verified native on JDK 21 + Aqua; not yet verified on
  every external display config.

## 5. Diagnostics / error recovery

- **Implemented** (commit 30da7ec8f): Diagnose Track/Session dialog
  (chromosomes in file vs genome, index exists, truncated, 0/1-based,
  network/file-format/genome/memory/rendering), exception dedup/
  rate-limit in the global handler. Batch command listener (port 60151
  style) verified intact; deeper listener improvement pending if Runtian
  wants it.

## 6. Packaging / distribution

- **Implemented**: reproducible release script (`scripts/package/
  build_release.sh`) producing fresh `.app` bundle + UDZO DMG + ZIP +
  `SHA256SUMS` + `version.txt`, ad-hoc or Developer ID codesign, and
  post-build verification.
- **Not done**: Developer ID signing + notarization (needs Runtian's
  certificate + Apple credentials — secrets stay out of the repo), CI
  pipeline (design in `release.md`), x86_64/Rosetta build (Apple Silicon
  primary).
- No 3.0-dev migration; IGV-X targets 2.19.X stable until a written plan.

## 7. Test-data coverage

- Real-file suite currently covers bigWig, bigBed, BAM (real reads),
  VCF, GFF3. Missing: CRAM, BED/PSL real files, multi-sample VCF,
  session files with divergent naming. The manifest can be extended
  without code changes.
- Real files are large and network-bound; the hermetic synthetic suite
  is the CI gate, real files are the periodic deep check.

## 8. Process

This file is updated as part of every milestone report; a limitation
stays listed until a linked doc/commit shows it addressed.
