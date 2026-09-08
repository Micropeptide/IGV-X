# IGV-X — Roadmap ideas (brainstorm, unscoped)

A large, unscoped brainstorm of further IGV-X improvements, requested 2026-09-07.
Nothing here is committed to; it's a pool to pull from when picking the next
piece of work. Items are grouped by area. Where an idea is Arabidopsis/WGBS-
specific (Runtian's actual research use case) it's marked **(WGBS)**.

Related: [limitations.md](limitations.md) (what's honestly not done yet),
[whats-different.md](whats-different.md) (what's already shipped),
[sessions.md](sessions.md) (session-file design).

## A. Sessions, files & portability

1. Save-session dialog: an explicit "Use relative paths" checkbox (currently a
   global Preferences toggle only) so it's visible/overridable per-save.
2. Warn (don't silently relativize-and-fail) when a track's data file is on a
   different filesystem/volume than the session — nothing to make relative in
   that case, so tell the user which paths stayed absolute and why.
3. "Reveal in Finder" on any track's context menu, and on the session file
   itself from a new File > Show Session in Finder.
4. "Move session + all data files into folder..." — a one-click operation
   that copies the session and every referenced local file into a chosen
   folder and rewrites paths, for turning a scattered set of files into a
   truly portable bundle.
5. Detect and offer to fix a session with a mix of absolute and relative
   paths (e.g. after manual XML editing or an old save).
6. Session diff/compare tool: given two `.xml` sessions, show which tracks,
   colors, ranges, or paths differ (useful when a labmate sends back an
   edited copy).
7. Autosave: keep last N autosaves (not just one), with a "restore autosave"
   picker showing timestamps, similar to macOS's own document versions.
8. Session templates: save a session *without* data-file references (just
   panel layout, colors, ranges, genotype rules) as a reusable starting
   point for a new experiment.
9. Warn before overwriting a session file that's changed on disk since IGV-X
   loaded it (external edit / git checkout race).
10. Session file `.gitignore`/git-friendliness: an option to write session
    XML with stable attribute ordering and pretty-printed indentation so
    diffs in git are readable.
11. Bundle a session + its data files into a single `.igvxz` zip archive for
    emailing/Slacking to a collaborator (open transparently, unpack to temp,
    load).
12. "Recent Sessions" panel: show a thumbnail/snapshot of the view alongside
    each entry (small rendered preview cached at save time), not just the
    filename.

## B. macOS integration & packaging

13. Full Developer ID signing + notarization so Gatekeeper doesn't warn on a
    fresh Mac (tracked already in limitations.md; just prioritizing it here).
14. Universal binary / Rosetta-compatible Intel build for lab machines that
    aren't Apple Silicon.
15. Sparkle-style in-app "Install Update" (download + relaunch) instead of
    today's "check + notify + open browser" update flow.
16. Spotlight metadata importer so `mdfind`/Spotlight can search session
    files by genome, track names, or locus.
17. Quick Look plugin for `.xml`/`.igvx` session files — preview panel
    listing genome, track count, and locus without opening the app.
18. Handoff/Universal Clipboard: copy a locus on one Mac, paste-and-jump on
    another IGV-X instance signed into the same Apple ID.
19. Menu bar "mini mode": a compact always-on-top locus/search bar that can
    drive the main window without it being frontmost (fast lookups during a
    presentation).
20. Native macOS Services menu integration: select a gene/locus string
    anywhere in macOS (Notes, Mail, a PDF), right-click > Services > "Open
    in IGV-X".
21. Shortcuts.app (Apple Shortcuts) actions: "Open Session", "Load Track",
    "Take Snapshot" as automatable Shortcuts steps.
22. Homebrew cask formula (`brew install --cask igv-x`) for easier
    install/upgrade than a DMG.

## C. Performance & scalability

23. Lazy track initialization on first view (already flagged in
    limitations.md) — finish separating "track object created" from "track
    data actually fetched/rendered".
24. Progressive/streamed rendering during large-session load: draw tracks as
    they finish loading rather than blocking until the whole batch is in.
25. On-disk render cache for unchanged, off-screen tracks so re-scrolling
    back to a previous view doesn't refetch/redecode.
26. Background pre-fetch of the next likely locus (adjacent gene in a gene
    list, next chromosome) while the user is reading the current view.
27. Memory-pressure-aware track unloading: evict cached data for tracks that
    haven't been visible in N minutes when free memory gets low, with a
    "reload on scroll-back" fallback.
28. Parallelize `.igvx.json` companion + bookmark writes with the main XML
    write (currently sequential, best-effort).
29. A "Performance" pane in Preferences exposing thread-pool size, cache
    limits, and prefetch behavior instead of hardcoded constants **(for
    Runtian's ~900-track WGBS sessions specifically)**.
30. Startup time budget: measure and report (Help > Diagnostics) where
    cold-launch time actually goes (JVM start, plugin scan, genome load,
    session load) so regressions are visible.
31. GPU-accelerated rendering path (Metal via JavaFX/Skija) as an opt-in for
    very tall multi-track WGBS views, if Java2D software rendering becomes
    the bottleneck at scale.
32. Virtualized track list: for 900+ tracks, only construct Swing components
    for the visible + nearby-scroll window, not all of them up front.
33. Incremental bigWig index caching to disk (keyed by file path + mtime) so
    reopening a huge session doesn't re-parse every R-tree from scratch.
34. Background "warm cache" mode: option to pre-load and index all tracks in
    a session immediately after a batch import, during idle time, before the
    user needs them.

## D. Track rendering & visualization

35. Synchronized zoom/scroll across *separate* IGV-X windows (multi-monitor
    side-by-side comparison of two loci or two samples).
36. Per-track "pin to top" so a reference/control track always renders above
    the scrollable stack.
37. Track grouping with collapsible section headers (beyond genotype
    organize) — arbitrary user-defined folders of tracks.
38. Adjustable track-height presets (Compact/Normal/Tall) applied to a whole
    selection at once, not one track at a time.
39. Inline mini-legend per group showing the color key (CG/CHG/CHH, or
    genotype) directly in the track-name panel, not just a separate dialog.
40. Overlay/ghost mode: render a second track semi-transparently on top of
    another for direct visual diff (e.g. two genotypes' CG methylation at
    the same locus).
41. Per-track annotation ruler: user-added colored tick marks on a track
    independent of the shared ROI list (e.g. "peak of interest here").
42. Smooth (animated) pan/zoom transitions, togglable, for screen-recording
    walkthroughs.
43. A minimap/overview strip (like a code editor's) showing the whole
    chromosome with a highlighted viewport box, click-to-jump.
44. Track search-and-highlight: type a track-name substring, matching tracks
    flash/scroll into view (useful with hundreds of same-shaped WGBS tracks).
45. Per-track notes: a free-text sticky note attached to a track, shown on
    hover, persisted in the `.igvx.json` companion.
46. Configurable color-blind-safe palette presets for quantitative tracks
    and genotype/context colors.
47. Live cursor readout: a persistent small HUD showing exact
    position/value under the mouse across all tracks simultaneously, instead
    of only tooltip-on-hover.
48. Track thumbnails in a "track browser" grid view for quickly scanning
    hundreds of tracks' shapes before deciding what to load into the main
    view.
49. Per-track custom render function hook (via a small Groovy/JS snippet)
    for one-off visualizations without writing a Java plugin.
50. Split-track view: view two loci from the *same* track side-by-side in
    one linked panel (e.g. compare two genes' methylation directly).

## E. WGBS / methylation & epigenetics-specific (WGBS)

51. **(WGBS)** Built-in CG/CHG/CHH summary statistics panel: for a selected
    region, show mean methylation per context per genotype in a small table,
    computed live from the loaded bigWigs.
52. **(WGBS)** DMR (differentially methylated region) overlay track: load a
    DMR call file (BED-like) and highlight/shade DMRs directly under the
    relevant methylation tracks.
53. **(WGBS)** One-click "compare genotypes at this locus" — select 2+
    genotype groups (from the existing organize-by-genotype rules) and get a
    side-by-side delta view or a difference track.
54. **(WGBS)** Context-aware track coloring gradient by methylation level
    (not just fixed CG/CHG/CHH colors) — heatmap-style shading proportional
    to value, as an alternative render mode.
55. **(WGBS)** Batch "compute region methylation" export: select an ROI or
    gene list, export a CSV of per-track, per-context mean methylation —
    directly usable in R/pandas without leaving IGV-X.
56. **(WGBS)** RdDM/siRNA locus overlays: bundle small-RNA cluster
    annotations (from existing Arabidopsis gene-list infrastructure) as an
    optional annotation track set.
57. **(WGBS)** Transposable-element (TE) annotation track bundled for
    TAIR10, toggleable, since TEs are frequently the point of a methylation
    analysis.
58. **(WGBS)** "Imprinting view" preset: one menu item that sets up the
    layout Runtian's imprinting/DME gene-list work actually needs (parent-
    of-origin tracks stacked with matched scaling).
59. **(WGBS)** CpG island / density track generator from the loaded genome
    FASTA, for genomes that don't ship one.
60. **(WGBS)** Per-context data-range auto-linking: changing the Y-axis max
    on one CG track offers to apply the same range to all CG tracks (context-
    scoped, not just genotype-scoped) for visual comparability.
61. **(WGBS)** Read-level (single-molecule) methylation view for bisulfite
    BAMs — per-read CpG methylation calls colored per read, like a lollipop/
    haplotype view, for allele-specific methylation inspection.
62. **(WGBS)** Metaplot/aggregate-profile tool: average methylation signal
    across a set of features (e.g. TSS ± 2kb over all genes in a list),
    rendered as a line plot, computed from the currently loaded tracks.
63. **(WGBS)** Bisulfite conversion-rate QC readout in Diagnose Track (using
    a chloroplast/organellar control region as the standard denominator).
64. **(WGBS)** Batch genotype × context × replicate matrix view: auto-lay-out
    a folder of WGBS bigWigs into a grid (rows = genotype, columns =
    context) rather than one flat ordered stack.
65. **(WGBS)** Export a genotype-organized session as a publication figure
    preset (matched track heights/ranges/colors) in one action, building on
    the existing high-quality export.
66. **(WGBS)** Direct methylKit/DSS/ARPEGGIO-style DMR-caller launch (or
    at least a documented "export in the input format X expects") from
    inside IGV-X, closing the loop from viewing back to re-analysis.

## F. Navigation, regions & bookmarks

67. Named "views" (locus + track visibility + ranges as one snapshot),
    switchable from a dropdown, distinct from bookmarks (which are just
    loci).
68. Bookmark folders/tags for organizing dozens of saved regions by
    project/experiment.
69. Keyboard-driven region navigation: next/previous ROI, next/previous
    bookmark, next/previous ordered ROI in current view.
70. History back/forward (like a web browser) across visited loci, separate
    from Undo/Redo which covers track mutations.
71. "Go to gene" with fuzzy/typo-tolerant matching and inline preview of
    candidates as you type.
72. Region-of-interest math: union/intersect/subtract two ROI sets directly
    in the UI (currently needs bedtools outside the app).
73. Locus history export: dump the session's list of visited/bookmarked loci
    as a BED file for use elsewhere.
74. Configurable "jump" increments (page-by-gene, page-by-fixed-kb,
    page-by-visible-width) bound to distinct keys.
75. Multi-locus synchronized bookmarks: bookmark the *current* multi-locus
    gene-list view as one entry, not per-locus.
76. Sticky "compare to bookmark" ghost overlay: keep a bookmarked locus's
    track shapes as faint reference while navigating elsewhere at the same
    scale.
77. Voice/dictation-friendly locus entry (macOS dictation already reaches
    the search box; verify and document it explicitly as supported).
78. "Return to last session state on relaunch" as a real preference (restore
    exact locus/zoom/tracks from the previous quit, not just window bounds).

## G. Comparative & multi-sample analysis

79. Sample sheet import (CSV/TSV of sample metadata) that auto-generates
    genotype/condition organize-by rules instead of regex name matching.
80. Correlation matrix / clustering view across all loaded quantitative
    tracks over the current view or a region set.
81. PCA/embedding plot of samples based on signal over a gene list, opening
    in a side panel (lightweight — not meant to replace R, just triage).
82. Batch "load matched tracks across N sample folders" wizard, pairing by
    filename pattern (e.g. `{sample}_CG.bw`, `{sample}_CHG.bw`).
83. A "differences only" filter: hide tracks whose signal doesn't differ
    beyond a threshold between two selected groups over the current view.
84. Replicate averaging: define replicate groups and render a computed mean
    (± band) track without pre-merging files outside the app.
85. Cross-sample track alignment sanity-check: flag when compared tracks use
    different genome builds/chromosome naming before drawing false
    conclusions.
86. Side-by-side "before/after" mode for two full sessions (e.g. WT vs
    mutant) in a single synchronized split window.
87. Batch screenshot sweep: given a gene list and a set of loaded sessions,
    auto-export one image per gene per session into a folder tree for rapid
    visual QC of many loci.
88. Statistical annotation overlay: mark loci where a paired t-test/Wilcoxon
    over provided per-track values crosses a significance threshold.

## H. Annotation, gene lists & search

89. Gene-list editor UI (add/remove/reorder genes, not just curated
    read-only lists) with save-as-custom-list.
90. Import gene lists from GO term / KEGG pathway ID directly (resolve to
    gene IDs for the loaded genome).
91. Cross-reference panel: click a gene, see external DB links (TAIR,
    UniProt, KEGG) relevant to the loaded genome, opened in the default
    browser.
92. Track-level search: "find tracks whose data has a peak/value above X in
    the current view" across all loaded tracks.
93. Feature search history with recency + frequency ranking (like URL bar
    autocomplete) instead of a flat recent list.
94. Batch annotation lookup: paste a list of gene IDs, get a table of
    coordinates + which loaded tracks have signal there.
95. Custom annotation track builder: turn a set of manually drawn ROIs into
    a named, reusable annotation track.
96. GFF/GTF attribute filter: show only features matching an attribute
    query (e.g. `gene_biotype=transposable_element`) without pre-filtering
    the file externally.
97. Synteny/ortholog jump: given a gene in one genome, jump to its ortholog
    locus in another loaded genome (useful for comparative epigenetics
    across ecotypes/species).
98. Autocomplete search should surface both gene *symbol* and available gene
    *aliases* (already partly genome-driven; make coverage/gaps visible in
    Diagnose Track).

## I. Export & publication

99. Multi-page PDF export: one gene/region per page, auto-titled, for a
    supplementary-figure PDF straight out of IGV-X.
100. Export presets (journal-specific: column width, font, DPI) saved and
     reusable, instead of re-setting export options every time.
101. Copy-as-image directly to clipboard (no intermediate file) for quick
     pasting into Slides/Keynote/Word during a lab meeting.
102. Animated GIF/MP4 export of a pan/zoom sequence (e.g. zooming into a
     locus) for talks and social posts.
103. Batch export driven by a manifest file (locus, output name, per-row
     track visibility overrides) for fully reproducible figure generation.
104. Vector export of read-level/lollipop views (currently likely rasterized
     with the rest) so single-molecule figures stay crisp at any zoom.
105. Export track data (not just the image) as the exact numeric values
     rendered in the current view, for supplementary data tables.
106. "Figure recipe" file: save the exact export parameters + locus + track
     state as a small reproducible file separate from the working session.
107. Embed a small provenance footer (optional) in exported images: IGV-X
     version, genome build, locus, timestamp — for lab-notebook traceability.
108. One-click "export current view to this lab's shared figures folder"
     with a configurable default output directory.

## J. Diagnostics, error recovery & logging

109. Centralized "Session Health" report: one dialog listing every track's
     load status (OK / missing file / index missing / chr-name mismatch) for
     the whole session, not per-track.
110. Crash recovery: on relaunch after an unclean exit, offer to restore the
     last autosaved state (partially exists via autosave; make the recovery
     prompt explicit and visible).
111. Structured (JSON) log output option for easier automated triage/
     tooling, alongside the human-readable log.
112. "Copy diagnostic bundle" (logs + session + prefs, secrets redacted) as a
     single zip for bug reports, one click from Help menu.
113. Network-request diagnostics: for remote/URL tracks, show latency,
     retry count, and last HTTP status per resource.
114. In-app changelog viewer (What's New since your last version) instead of
     only a downloadable release-notes doc.
115. Self-test on startup (headless-safe subset of the regression suite)
     with a visible pass/fail badge in About, so a broken build is obvious
     before the user hits it mid-analysis.
116. Configurable log verbosity per subsystem (session, rendering, network)
     from Preferences instead of editing a properties file.
117. "Explain this error" helper: pattern-match common exceptions (OOM,
     truncated index, wrong genome) to a plain-English explanation + fix
     steps, beyond today's Diagnose Track scope.
118. Watchdog for zombie background threads (beyond the existing wait-cursor
     watchdog) with a "Force Reload Stuck Tracks" action.

## K. Automation, scripting & batch

119. A documented, versioned batch-script language upgrade (today's port
     60151 command set) with conditionals/loops for multi-step automated
     figure generation.
120. Python bindings (via the existing batch port or a small local HTTP API)
     so lab scripts can drive IGV-X from a Jupyter notebook.
121. "Record macro": capture a sequence of UI actions (navigate, toggle
     track, export) and replay it, for repeated weekly report generation.
122. Command-line `--script` flag to run a batch file non-interactively and
     exit (headless figure generation in a pipeline/cron job).
123. Watch-folder mode: auto-load new bigWigs dropped into a designated
     folder into the current session (useful right after a pipeline run
     finishes writing tracks).
124. Scheduled auto-export: nightly regenerate a fixed set of QC figures from
     the latest data, unattended.
125. A REST-ish local API (localhost only) exposing session state as JSON,
     for lightweight external dashboards/tools without full scripting.
126. Plugin/extension mechanism (drop a `.jar` implementing a small
     interface into a plugins folder) for one-off lab-specific features
     without forking IGV-X itself.
127. Template-driven multi-track loading: a YAML/JSON recipe describing
     which files to load into which panels with which colors, versioned
     alongside the analysis it belongs to.
128. CLI genome/session validator: `igvx --validate session.xml` reports
     every problem (missing files, bad chr names) without launching the GUI,
     for CI on shared analysis repos.
129. Headless thumbnail generator: given a session + locus list, produce PNGs
     without ever showing a window, for automated report pipelines.
130. Batch track-format conversion helper (wig→bigWig, etc.) surfaced in the
     UI, wrapping the existing igvtools functionality more discoverably.

## L. Collaboration & sharing

131. "Share this view" — generate a single portable link/file (session +
     zipped small tracks, or a manifest for large ones) a labmate can open
     with one command.
132. Commenting on a bookmark/ROI: short notes visible to whoever opens the
     shared session (stored in `.igvx.json`), for async discussion.
133. Session ownership/history: who last edited this session and when,
     surfaced in the UI when opening a shared file (from git blame or a
     lightweight embedded log).
134. Read-only "presentation mode": lock editing, hide chrome, for showing
     data in a lab meeting without accidental edits.
135. Live co-viewing (screen-share aware): a "follow" mode where a second
     IGV-X instance mirrors navigation from a host instance over the local
     network, for remote pair analysis.
136. Export a locus + track state as a pre-filled igv.js embed snippet for a
     lab website or protocols.io page.
137. Slack/Teams webhook integration: post an exported figure + locus
     directly to a lab channel from the Export dialog.
138. Shared team preset library (genotype rules, color schemes, gene lists)
     synced via a shared folder (Dropbox/iCloud/lab NAS), separate from
     personal preferences.

## M. Data integrity, provenance & reproducibility

139. Per-track checksum recording (in `.igvx.json`) so a session can flag
     "this bigWig changed since the session was saved" — catches silent
     re-processing upstream.
140. Genome-build fingerprinting: warn if a session's declared genome doesn't
     match the actual chromosome sizes/names found in the loaded FASTA/2bit.
141. "Frozen session" export: bundle exact file checksums + tool versions
     (if known) into the companion metadata for a reviewer-facing
     reproducibility record.
142. Immutable snapshot mode: lock a saved session against accidental
     re-save-over, requiring explicit "Save As" once flagged final (e.g. for
     a submitted-manuscript figure's source session).
143. Data lineage note field per track: free-text "how was this file made"
     (pipeline name/version, date) surfaced in Diagnose Track.
144. Session versioning: keep the last N saved versions of a session file
     (like autosave, but explicit named checkpoints) with diff/restore.
145. Built-in file-integrity check against a lab's LIMS/ELN if one exists
     (pluggable — most labs won't have this, but the hook costs little).
146. Warn on save if any loaded track path is inside a location known to be
     ephemeral (e.g. a scratch/tmp dir, a mounted external drive that isn't
     always connected).
147. Export/import of "analysis notebook" pairing a session with a
     Markdown/Jupyter narrative describing what was found at each bookmark.
148. Consistent, documented coordinate-system stamping (0- vs 1-based) in
     every export so downstream tools never guess.

## N. Preferences, customization & workspace

149. Named preference profiles (e.g. "Presentation", "Analysis",
     "Screenshot") swappable from a menu, instead of one global prefs set.
150. Import/export preferences as a single file for moving a whole personal
     setup to a new Mac.
151. Per-genome default preferences (e.g. always load TAIR10 gene lists,
     default locus, default track panel layout) applied automatically when
     that genome is selected.
152. Workspace layouts: named window/panel arrangements (like an IDE), swap
     between "wide monitor" and "laptop" layouts in one click.
153. Custom keyboard shortcut editor (remap any menu action), beyond the
     fixed accelerators shipped today.
154. Toolbar customization: add/remove/reorder buttons on the main toolbar.
155. Font size / density presets for the whole UI (not just tracks) for
     presentation vs normal use.
156. A "reset to defaults" scoped per preferences section (Tracks, Sessions,
     macOS, Diagnostics) instead of one all-or-nothing reset.
157. Configurable default save location per file type (sessions vs exported
     images vs CSV data dumps).
158. Quick-switch color themes for track defaults (a named palette applied
     to newly loaded tracks), separate from the genotype/context color rules.

## O. UI/UX polish & accessibility

159. Full VoiceOver pass beyond what's done (limitations.md already flags
     this as partial) — every dialog, every track control, every custom
     Swing component gets a real accessible name/role.
160. High-contrast mode beyond system dark/light (IGV-X deliberately skips
     dark mode today; a high-contrast *light* theme is a smaller, still
     accessible step).
161. Adjustable UI scale independent of system display scaling, for very
     dense multi-track views on non-Retina external monitors.
162. Onboarding tour for first launch: a short interactive walkthrough of
     Open, genotype organize, and export, instead of a blank window.
163. Command palette (Cmd+Shift+P style): fuzzy-search every menu action by
     name instead of hunting through menus.
164. Better empty-state UI: when no tracks are loaded, show actionable
     buttons (Open File, Open Folder, Recent Sessions) directly in the empty
     canvas, not just a blank gray area.
165. Undo/redo visual feedback: a small toast ("Undid: reorder tracks")
     confirming what just happened, beyond the History dialog.
166. Track context menu reorganization/search — with genotype organize,
     diagnostics, and export all added over time, the menu likely needs a
     submenu pass for discoverability.
167. Drag-to-reorder track panels with live preview (ghost row) — verify
     current UX quality now that regular file drag-and-drop is also
     supported (avoid the two drag modes feeling inconsistent).
168. Inline rename (double-click a track name to rename in place) if not
     already present.
169. Status bar upgraded to show more live context: current genome, track
     count, memory pressure indicator, background task count.
170. A proper "What is this?" hover-help mode (click a `?` cursor onto any
     control to get a one-line explanation), useful for infrequently-used
     power features like organize-by-genotype rules.

## P. Search & data discovery (external DBs)

171. In-app ENCODE/GEO/SRA search-and-preview-load for public tracks
     relevant to the loaded genome (partial ENCODE support exists; extend
     coverage and discoverability).
172. Arabidopsis-specific data portal integration (e.g. AraPort, 1001
     Genomes, ePlant) as a "Browse public tracks" source alongside ENCODE.
173. "Find similar public datasets" for a loaded track (metadata-based
     suggestion, e.g. "other WGBS in Col-0 flower tissue").
174. Literature cross-reference: given a gene under the cursor, one click to
     a PubMed search scoped to that gene + "methylation"/"epigenetics".
175. Local dataset catalog: a searchable index of every file the user has
     ever loaded (path, genome, format, date), so "where's that bigWig from
     three months ago" has an answer.

## Q. Cloud, remote & big-data backends

176. Native S3/GCS URI support for tracks (`s3://...`, `gs://...`) with
     credential profiles, beyond generic HTTPS.
177. Resumable/retrying network reads for flaky lab-network or VPN
     connections to remote data.
178. Optional local disk cache for remote tracks (LRU, size-capped) so
     repeated views of the same remote bigWig don't re-download.
179. Direct SSH/SFTP track loading (`user@host:/path/to/file.bw`) for data
     that lives on a lab compute cluster, not a web server.
180. Track hub authentication support (Bearer/OAuth) for private hosted
     hubs, beyond public track hubs.
181. Bandwidth/usage indicator when working against remote/cloud data, so a
     metered connection doesn't get silently hammered by a 900-track remote
     session.

## R. Security & privacy

182. Prompt before a session/track-hub file is allowed to reach out to a
     non-HTTPS or unfamiliar remote host (phishing-adjacent session files
     are a real vector for a widely-shared app).
183. Sandboxed/hardened runtime entitlements review as part of the
     Developer ID signing work (item 13), least-privilege by default.
184. Redact absolute local paths (usernames, lab folder structure) from
     exported diagnostic bundles by default, with an explicit opt-in to
     include full paths.
185. Verify-before-load option for session files from an untrusted source
     (e.g. downloaded from an email attachment) — show what it references
     before fetching anything.

## S. Testing, CI & dev tooling

186. Real CI pipeline (tracked in release.md as "design, not built yet") —
     GitHub Actions running the full test suite + a release build on every
     push/PR.
187. Visual regression testing: snapshot rendered track images for a fixed
     test session and diff against a baseline to catch rendering
     regressions automatically.
188. Fuzz testing for session-file parsing (malformed/adversarial XML) to
     harden `IGVSessionReader` against crashes on bad input.
189. Synthetic large-session generator as a proper dev tool (not just an ad
     hoc test fixture) for reproducing the ~900-track performance work on
     demand.
190. Automated screenshot-based UI smoke test on every build (open app, load
     a fixture session, screenshot, compare) using the same computer-use-
     style tooling available today.
191. Dependency update automation (Dependabot/Renovate) for the many
     bundled third-party jars, with the existing test suite as the gate.
192. A documented local dev-container/Docker setup for contributors without
     needing to hand-install the exact JDK 21 + toolchain locally.

## T. AI-assisted / speculative

193. "Ask about this locus" panel: given the currently visible tracks and
     genome annotation, a local/offline summary of what's known about the
     gene(s) in view (from bundled annotation only — no network calls
     unless explicitly opted in).
194. Anomaly flagging: highlight tracks/regions whose values are statistical
     outliers relative to the rest of the loaded session, as a triage aid
     for scanning hundreds of tracks.
195. Auto-suggested genotype/context organize rules from track *filenames*
     when the existing regex rules don't match anything (today's rules must
     be hand-authored).
196. Smart default data-range suggestion per track based on its actual value
     distribution, instead of a single global default.
197. Natural-language locus search ("promoter of the gene next to AT1G01010")
     resolved via existing genome annotation, no external LLM call required.
198. Session "sanity summary" on load: a one-paragraph plain-English recap
     (genome, track count, contexts/genotypes detected, any issues) surfaced
     once, so a big session's shape is legible at a glance.
199. Suggested next action based on context (e.g. after organize-by-genotype,
     suggest "Export publication figure" if the view looks finalized) as a
     dismissible hint, never a forced workflow.
200. Optional, fully local (no network) figure-caption drafter: given the
     current export, propose a one-sentence caption describing locus/tracks/
     genotypes shown, for the user to edit — never auto-inserted anywhere.

## How to use this list

Nothing here is prioritized. When picking up a next task, a reasonable filter
is: (a) does it unblock or de-risk Runtian's actual current WGBS workflow
(section E, and the perf/session items in A/C), (b) is it small and
self-contained enough to land as one focused PR, (c) does it need new
third-party dependencies (raises the bar — prefer dependency-free options
first, consistent with the existing PDF/SVG export choice).
