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
  Count at last update: **64** commits total on the IGV-X branch vs
  upstream `2.19.X`, of which **61** are IGV-X changes (the other 3 are an
  unrelated personal-site `.order` file that happens to live at the repo
  root and isn't part of IGV-X at all — excluded from this list).

## Patch list (newest first)

| ID | Commit | Area | Description | Status |
|---|---|---|---|---|
| P061 | 67f33c8d4 | session | Session portability batch: fixed Track/@id and index/coverage/mapping never being relativized (and clobbered on read/write) across 4 call sites, now one shared `relativizeIfApplicable`; symlink-safe relative-path canonicalization; MergedTracks member-track id fix; Move Session + Data Files Into Folder... (File menu); Reveal Data File(s) in Finder (track menu) + Show Session in Finder (File menu); Remove from Recent (Welcome panel); file-changed-on-disk warnings on load and save; Next/Previous Bookmark navigation; Find Track...; Copy Image to Clipboard; window-wide drag-and-drop; Welcome-panel startup race fix + redesign (file-type badges, guided double-click-setup banner, real app icon); batch-saveSession mtime-tracking fix | Landed |
| P060 | d3fcc9067 | packaging | Fix jpackage Info.plist patch: PlistBuddy `Set` can't create elements inside a freshly-added empty array, so every document-type patch was silently no-op'ing; switched to typed `Add` + fail-fast self-verification | Landed |
| P059 | a6ea50189 | test | Graceful-skip a remote sampleinfo regression test when Broad retires the hosted fixture, instead of failing the suite | Landed |
| P058 | cea09e235 | ui | Quiet, configurable update checks: silent when already current; Help > Update Settings... (Daily/Weekly/Never) | Landed |
| P057 | 2fd646279 | branding | Fix IGV-X Dock icon white matte artifact | Landed |
| P056 | 125fe64d7 | ui | Extend undo/redo to genome loads | Landed |
| P055 | 0416aae91 | test | Session-load cancel regression test (headless-safe) | Landed |
| P054 | 6897fbb72 | packaging | Switch build_release.sh to the jpackage flow (native launcher) | Landed |
| P053 | 5a947ee40 | ui | Undo/Redo + Track History dialog + save-without-prompt (Edit menu, Cmd/Ctrl+Z / Shift+Cmd/Ctrl+Z) | Landed |
| P052 | d9ab77001 | packaging | jpackage app-bundle build script — native launcher so Finder AppleEvents reach the JVM (root cause of P051/P050) | Landed |
| P051 | 166efd284 | macos | Fix macOS Finder session-open: install the Desktop open-file handler on the EDT with a buffered drain for cold-launch events | Landed |
| P050 | 74f79c9e1 | macos | macOS Finder association for IGV session files (double-click + Open With) + canonical session-file detection | Landed |
| P049 | d09316511 | genome | Bundle Arabidopsis TAIR10 genome (extracted + registered on first launch); Help > Request a Feature (mailto) | Landed |
| P048 | 4ed769112 | docs | v2.19.5-igvx.2 release notes | Landed |
| P047 | 7ed7c33d4 | ui | About dialog notes IGV-X customization; first-run update-setup prompt; periodic auto update checks (`IGVX.UPDATE.INTERVAL.HOURS`) | Landed |
| P046 | e20cf8f68 | docs | Use GitHub handle (not real name) in public-facing notes | Landed |
| P045 | 4964e16b9 | docs | Note that IGV-X is customized for Runtian Wu's Arabidopsis epigenetics/WGBS research | Landed |
| P044 | 8aba3e8eb | docs | Public repo README banner + v2.19.5-igvx.1 release notes | Landed |
| P043 | 2c405193a | ui | In-app update checking via GitHub releases: Help > Check for Updates + startup check pref (`IGVX.UPDATE.*`), `UpdateChecker`/`UpdateManager`/`UpdateCheckDialog`, version comparator | Landed |
| P042 | 912f50e67 | ui | Wire auto-organize-by-genotype hook (`IGVX.ORGANIZE.AUTO`): applies saved rules on the EDT after session/batch loads; never throws on failure | Landed |
| P041 | 86b3f08be | docs | Document organize-by-genotype + wait-cursor fix; fork-diff P032–P040 | Landed |
| P040 | 6c1d56b2b | ui | Organize tracks by genotype: editable rules (genotype regex+background, context regex+color), CG/CHG/CHH grouping with consistent colors, auto-derive genotypes, Ungrouped fallback; TrackGroup background tint | Landed |
| P039 | 55a90d1a5 | ui | Wait-cursor fix: bookmark/highlight use lightweight repaint; 60s watchdog releases cursor on hung track loads | Landed |
| P038 | 12dfd0eaa | docs | Document batch track import (CHANGELOG, changes-vs-igv, whats-different) | Landed |
| P037 | 6640163af | ui | Batch track import: File > Open Folder of Tracks... recursive scanner + chooser (type filter, Select All/Clear, Load Selected/All); index/hidden files excluded | Landed |
| P036 | 987ce33cd | docs | Document Arabidopsis tair10 gene lists | Landed |
| P035 | d422f0f29 | ui | Arabidopsis (tair10) gene lists in View > Gene Lists: 6 curated pathway lists, AGIs verified against real TAIR10 GTF | Landed |
| P034 | de583ccb7 | session | Unified Open on welcome panel (SmartOpenMenuAction); File > Cancel Session Loading with clean interruption | Landed |
| P033 | 1f2cbfc89 | docs | docs/changes-vs-igv.md — complete detailed change record vs stock IGV | Landed |
| P032 | cf35bfe55 | docs | Docs finalization: full fork-diff patch list + feature→file map; limitations/whats-different refreshed | Landed |
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
| Gene lists (P035) | `src/main/resources/org/broad/igv/ui/util/tair10.gmt`, `GeneListManager`, `Tair10GeneListTest` |
| Batch import (P037) | `src/main/java/org/broad/igv/ui/util/TrackFolderScanner.java`, menu wiring, `TrackFolderScannerTest` |
| Organize by genotype (P040) | `src/main/java/org/broad/igv/organize/` (OrganizeRules, TrackClassifier, TrackOrganizer, OrganizeTracksDialog), `TrackGroup` background, organize tests |
| Wait-cursor watchdog (P039) | `IGV.java` (repaint paths + orTimeout watchdog), `BookmarkManagerDialog` |
| Launcher (P014) | `scripts/mac.app/Contents/MacOS/IGV` |
| Packaging (P031, P052, P054, P060) | `scripts/package/build_release.sh`, `scripts/package/build_app_jpackage.sh`, `scripts/mac.app/Contents/Info.plist.template` |
| In-app updates (P043, P047, P058) | `src/main/java/org/broad/igv/update/` (UpdateChecker, UpdateManager, UpdateCheckDialog) |
| TAIR10 bundled genome (P049) | `src/main/resources/org/broad/igv/genome/tair10*`, first-run extraction/registration in `GenomeManager` |
| macOS Finder session association (P050–P051, P052, P060) | `src/main/java/org/broad/igv/ui/DesktopIntegration.java`, `src/main/java/org/broad/igv/ui/Main.java`, `scripts/mac.app/Contents/Info.plist.template`, `scripts/package/build_app_jpackage.sh` |
| Undo/Redo + Track History (P053, P056) | `src/main/java/org/broad/igv/history/` (TrackHistoryManager, UndoMenuAction/RedoMenuAction) |
| Session path relativization + portability (P061) | `src/main/java/org/broad/igv/session/SessionWriter.java` (`relativizeIfApplicable`, `relativizeNestedTrackIds`), `IGVSessionReader.java`, `src/main/java/org/broad/igv/util/FileUtils.java` (`canonicalizeForRelativize`), `src/main/java/org/broad/igv/track/MergedTracks.java` |
| Move Session + Data Files Into Folder (P061) | `src/main/java/org/broad/igv/ui/action/MoveSessionToFolderMenuAction.java` |
| Welcome panel redesign + Finder association helper (P061) | `src/main/java/org/broad/igv/ui/panel/WelcomePanel.java`, `src/main/java/org/broad/igv/ui/util/FileAssociationHelper.java` |
| Reveal in Finder / Copy Image to Clipboard / Find Track / Bookmark nav (P061) | `TrackMenuUtils.java`, `IGVMenuBar.java`, `src/main/java/org/broad/igv/ui/util/ClipboardImageUtils.java` |

(Updates appended as features land. This file is the machine-readable
companion to `whats-different.md`.)
