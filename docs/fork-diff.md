# IGV-X fork diff (technical)

Machine-readable record of every deliberate IGV-X change relative to
upstream IGV. This is the checklist that MUST survive every upstream merge
(see `upstream-update.md` §4): after merging `upstream/2.19.X`, verify each
entry still applies (`git log --oneline -- <file>` per area).

- **Base**: upstream `2.19.X` branch, commit `e2bcf4200` (2026-06-05).
- **Branch model**: `IGV-X` (dev) off `main` (tracks `upstream/main`,
  currently 3.0-dev — kept for future update analysis only).
- **Tagging**: every IGV-X release is tagged on the `IGV-X` branch.
- **Regenerate**: `git log upstream/2.19.X..IGV-X --oneline --no-merges`.
  Count at last update: **31** IGV-X commits.

## Patch list (newest first)

| ID | Commit | Area | Description | Status |
|---|---|---|---|---|
| P031 | 32f84d59e | packaging | Reproducible release build script (scripts/package/build_release.sh): fresh bundle, stale-app cleanup, codesign, DMG+ZIP, SHA256SUMS, version.txt, verification | Landed |
| P030 | 7c91b16ab | ui | Window state memory (maximized flag + last normal bounds), Preferences dialog resizable + a11y + system font, accessible track panel + diagnose report | Landed |
| P029 | ea6a25a8e | ui | macOS integration: screen menu bar, fullscreen, app handlers, platform accelerators; VoiceOver names on command bar | Landed |
| P028 | 30da7ec8f | diagnostic | Diagnose Track/Session dialog; exception dedup/rate-limit; getChromosomeNames API | Landed |
| P027 | c52d4cc85 | session | Relative session paths default on; optional .igvx.json companion metadata | Landed |
| P026 | 86da40d31 | ui | Configurable default quantitative track range; data-panel selection highlight | Landed |
| P025 | 7e87a86b6 | export | High-quality export: PDF (dependency-free), PNG DPI, selected-tracks-only, publication mode | Landed |
| P024 | 1f9d14387 | session | Persistent bookmarks + region highlights (session-persisted, per-region colors) | Landed |
| P023 | 0f88e2d77 | ui | Unified smart Open (files+sessions) + unsaved-session close confirmation | Landed |
| P022 | fa69b2c79 | branding | Runtime Dock icon from classpath resource → Runtian icon (256px) + window icon (16px) | Landed |
| P021 | c8996ff63 | branding | Fix -Xdock:icon runtime PNG to Runtian icon | Landed |
| P020 | 84b65b725 | ui | Recent-files history + welcome panel; record on all open paths | Landed |
| P019 | 159a41ca2 | branding | Dock runtime icon via -Xdock:icon (IGV_64.png) | Landed |
| P018 | 674efb2fc | perf | Large-session performance: bounded executor, async status, viewport culling, honest progress, concurrent rTree cache | Landed |
| P017 | ec655e300 | branding | Runtian app icon (igvx_icon.icns) in bundle resources | Landed |
| P016 | e752fc14e | ui | Trackpad horizontal swipe pan; ROI drag-select with live feedback | Landed |
| P015 | 5a8086cfa | docs | App bundle build+install procedure; chunked download script | Landed |
| P014 | 689c2dbe1 | launcher | Robust CWD-independent shell launcher, bundled JDK, ~/igvx args | Landed |
| P013 | 31b3416d1 | branding | ~/igvx data dir, ~/.igvx dot-dir, org.igvx.IGVX bundle id | Landed |
| P012 | 709ac974d | testdata | Manifest: alternate BAM mirror + never-resume-dirty-partial warning | Landed |
| P011 | 33c06dc26 | testdata | Verifier: python BGZF fallback for BAM header check | Landed |
| P010 | 0999bfe5c | docs | CHANGELOG for docs/manifest/verifier/regression fixes | Landed |
| P009 | 1810c6a7c | docs | Full docs tree scaffold + index | Landed |
| P008 | 2a086db34 | testdata | Test-file verification script (scripts/verify_test_files.py) | Landed |
| P007 | bb87bc44c | testdata | Public test-file download manifest | Landed |
| P006 | f2fe0b0a1 | chrom | BED/VCF chromosome-naming canonicalization regression tests | Landed |
| P005 | bd7193891 | chrom | BAM chromosome-naming regression tests (chr_name_cap fixture) | Landed |
| P004 | 73416ee69 | chrom | Broaden chrom-resolution tests: RefSeq-accession bigWig, organellar case, real RefSeq bigBed | Landed |
| P003 | 23e8e98f8 | chrom | **bigWig chromosome-resolution NPE fix** (TAIR10 WGBS): case-insensitive fallback + null-safe unboxing | Landed |
| P002 | d391461bb | docs | CHANGELOG, fork-diff, whats-different, build, test-datasets | Landed |
| P001 | 4e97aa4a6 | build | Fork scaffold on 2.19.X stable; gitignore project-local data/tools | Landed |

## Feature→file map (for merge verification)

| Feature | Key files |
|---|---|
| Chromosome resolution (P003–P006) | `src/main/java/org/broad/igv/bbfile/BBFile.java` (+ idForChr/getChromosomeNames), `src/test/java/org/broad/igv/bbfile/` fixtures |
| Session metadata (P027) | `src/main/java/org/broad/igv/session/` (SessionMetadata, SessionWriter, IGVSessionReader) |
| Bookmarks/export (P024–P025) | `src/main/java/org/broad/igv/ui/BookmarkManager*.java`, export panel classes |
| Diagnostics (P028) | `src/main/java/org/broad/igv/diagnostic/`, `ExceptionRateLimiter` |
| macOS UI (P029–P030) | `IGVMenuBar`, `IGV`, `MenuAction`, `CommandBarAccessibility`, `PreferencesEditor`, `TrackNamePanel` |
| Launcher (P014) | `scripts/mac.app/Contents/MacOS/IGV` |
| Packaging (P031) | `scripts/package/build_release.sh`, `scripts/package/README.md`, `docs/release.md` |

(Updates appended as features land. This file is the machine-readable
companion to `whats-different.md`.)
