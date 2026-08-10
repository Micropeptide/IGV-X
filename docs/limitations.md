# IGV-X — Known Limitations

Honest, current list of what IGV-X does not yet do, or does only
partially. Updated as features land; do not let this file go stale.

## 1. Chromosome resolution

- Without a loaded genome/alias table, only the file-side
  case-insensitive fallback applies; unknown names return empty results
  (no crash, but possibly "no data" to the user). The diagnostics
  subsystem (planned) is the intended honest surface for this.
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

- **Not yet implemented.** Charter priority #3 (lazy init, async loading,
  progressive rendering, EDT reduction, caching, virtualization,
  throttled tooltip/mouse, honest progress) is designed but not built.
  Current behavior is upstream 2.19.X: eager-ish loading, possible EDT
  stalls on very large sessions, memory-number-as-progress in places.
- Milestone: verify with a synthetic ~900-track session once the
  performance work starts; keep the honest-progress requirement
  (never show memory numbers as progress).

## 3. Sessions

- Relative paths, `.igvx.json` companion, bookmarks/highlights
  persistence: **designed, not implemented** (see `sessions.md`).

## 4. UI / interaction

- Trackpad two-finger swipe/Magic Mouse track navigation, configurable
  sensitivity without breaking vertical scroll: planned.
- Multi-track selection (Cmd/Shift-click): planned.
- High-quality PNG/SVG/PDF export (publication mode, selected tracks
  only): planned.
- Configurable quantitative-track default ranges (min/max): planned.
- Dark mode / accessibility polish: not started (inherits upstream's
  partial support).
- HiDPI/Retina: inherited from upstream; not yet verified on all
  displays.

## 5. Diagnostics / error recovery

- Repeated-exception dedup/rate-limit: designed, not implemented.
- Diagnose Track/Session (chromosomes in file vs genome, index exists,
  truncated, 0/1-based, network/file-format/genome/memory/rendering):
  designed, not implemented.
- Batch command listener (port 60151 style): inherited; improvement
  pending.

## 6. Packaging / distribution

- No signed/notarized DMG yet; dev builds are ad-hoc signed or unsigned
  (see `release.md`).
- No 3.0-dev migration; IGV-X targets 2.19.X stable until a written plan.
- No CI pipeline yet (design in `release.md`).

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
