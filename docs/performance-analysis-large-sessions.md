# IGV-X Large-Session Performance Analysis (900 bigWig tracks)

## 1. LOAD-TIME BOTTLENECKS (what happens when 900 bigWigs load)

Session load is driven from a non-EDT worker: `OpenSessionMenuAction.actionPerformed` (`src/main/java/org/broad/igv/ui/action/OpenSessionMenuAction.java:82`) calls `LongRunningTask.submit(() -> igv.loadSession(...))`. `LongRunningTask` uses a **fixed 5-thread pool** (`src/main/java/org/broad/igv/util/LongRunningTask.java:47`). Startup uses the same mechanism (`IGV.java:1880`). The XML parse runs on that worker (`IGVSessionReader.loadSession`, `IGVSessionReader.java:135`).

### 1.1 One raw thread per data file (unbounded)
`IGVSessionReader.processResources` (`IGVSessionReader.java:372-462`) spawns **one `new Thread(runnable)` per resource** (lines 429-431) with the explicit TODO `// Load files concurrently -- TODO, put a limit on # of threads?` (line 376). Each runnable calls `igv.load(locator)` (line 391), which is the full `TrackLoader` path. For 900 bigWigs this creates **900 OS threads** (each ~1 MB stack reservation, context-switch storm, ~900 concurrent file opens). The `synchronousLoads` branch (lines 426-427, 443-446) is preserved for batch mode, but interactive sessions take the thread-per-file path.

Secondary data race: `final List<String> errors = new ArrayList<String>();` (line 374) is written from all 900 threads (line 421) and read after join (lines 451-457) with no synchronization (`allTracks` at line 106 and `erroredResources` at line 108 are synchronized, but `errors` is not).

### 1.2 Per-file EDT round-trips for the status bar (serialization bottleneck)
Each `igv.load(locator)` calls `setStatusBarMessage3("Loading " + locator.getPath())` (`IGV.java:1272`) and clears it in `finally` (line 1302) — 2 calls per file, 1800 total. `ApplicationStatusBar.setMessage3` (`src/main/java/org/broad/igv/ui/util/ApplicationStatusBar.java:136-142`) does `UIUtilities.invokeAndWaitOnEventThread(...)` **including `paintImmediately`**. So every one of the 900 "concurrent" load threads **blocks on a synchronous EDT round-trip that forces a status-bar repaint**. This effectively serializes the load through the EDT and defeats most of the thread-per-file concurrency; it is arguably the single biggest load-time cost (an invokeAndWait round-trip + paint per file, twice per file).

### 1.3 Eager multi-read BBFile init per file
`TrackLoader.loadBWFile` (`src/main/java/org/broad/igv/track/TrackLoader.java:815-839`) does `new BBFile(path, genome)` (line 822) → constructor (`src/main/java/org/broad/igv/ucsc/bb/BBFile.java:116-122`) → `init()` (135-137) → `readHeader()` (175-275). Each read goes through `getBytes` (705-717), which **opens a brand-new `SeekableStream`** via `IGVSeekableStreamFactory.getStreamFor` (`IGVSeekableStreamFactory.java:60-78`: new `SeekableFileStream` per local file, new `IGVSeekableHTTPStream` per remote file — no caching) and does seek+readFully.

Per bigWig at load time, `readHeader()` performs:
1. **Read A — 64-byte common header** (line 179): magic → type, version, nZoomLevels, chromTreeOffset, fullDataOffset, fullIndexOffset, autoSqlOffset, totalSummaryOffset, uncompressBuffSize, extensionOffset. (A second 64-byte read at line 188 happens only if little-endian magic fails.) **This read is required** — `TrackLoader.loadBWFile` needs `isBigWigFile()` (line 824) immediately.
2. **Read B — zoom headers + autosql + totalSummary block** (line 218, size computed at 214-216): parses `zoomHeaders[]` (221-231, sorted 231), `autosql` (235-238), `totalSummary` (241-244). **None of these are used at load time for bigWig** (see Section 5b for callers).
3. **Read C — chromosome tree buffer** (line 251, up to 10 KB via `UnsignedByteBufferDynamic.loadBinaryBuffer`, `UnsignedByteBufferDynamic.java:39-72`, again a fresh stream open) → `ChromTree.parseTree` (252) → `chrNames` (253). **Not used at load time for session bigWigs** (callers are data-access paths only).
4. **Read D — bigBed only** (line 258): 4-byte `dataCount` at `fullDataOffset` → `featureDensity` (260) + `BBCodecFactory.getCodec` (262).
5. **Read E — extended header** if `extensionOffset > 0` (268-270 → 277-318): 64-byte read (279), plus a second read of the extra-index list if `extraIndexCount > 0` (287). Search-only.

So the true count is **3 reads minimum per bigWig** (A, B, C), **4-5 with an extended header**, **4-6 per bigBed** (A, B, C, D, +E). For 900 local bigWigs that is ~2,700 file descriptor opens just for header/init, before any data is painted.

### 1.4 Everything else in the load path is cheap
- `BBDataSource` constructor (`BBDataSource.java:59-64`): trivial — `super(genome)`, two map initializations. No eager I/O.
- `DataSourceTrack` constructor (`DataSourceTrack.java:57-65`) → `setDatasource` (67-76): no data access (the whole-genome score fetch is commented out, lines 71-74).
- `DataTrack` constructor (`DataTrack.java:78-82`): subscribes to `FrameManager.ChangeEvent` on the event bus (line 81) — 900 subscriptions, each woken on every frame change (receiveEvent, 87-102).
- `AbstractTrack.init` (`AbstractTrack.java:135-148`): two prefs reads only.
- `IGV.load` post-processing (1278-1296): attribute sets per track (cheap).
- `addUnallocatedTracks` (`IGVSessionReader.java:261-281`): `panel.addTrack(track)` per track; `TrackPanel.addTrack` (`TrackPanel.java:243-264`) is a list insert with no revalidate — cheap.
- Existence checks for missing files (collected into `missingDataFiles`, surfaced at lines 341-364): per-file stat/HEAD, cheap for local files.

### 1.5 Load-time summary per bigWig
1 open of the session XML (shared), then per file: 1 status-bar `invokeAndWait`+paint (set), 3-5 `BBFile` reads (each opening a new stream), 1 `BBDataSource`/`DataSourceTrack`/`DataTrack` object graph, 1 event-bus subscription, 1 synchronized map put, 1 status-bar `invokeAndWait`+paint (clear). Times 900, with 900 simultaneous threads and no I/O throttling.

## 2. PAINT-TIME BOTTLENECKS

### 2.1 Paint path (EDT)
`DataPanel.paintComponent` (`src/main/java/org/broad/igv/ui/panel/DataPanel.java:130-151`) runs on the Swing EDT. Per paint it:
1. Creates a `RenderContext` (line 143).
2. **`computeMousableRegions(groups, trackWidth)` (line 149) — iterates ALL visible tracks in ALL groups of the panel and allocates a `Rectangle` + `MouseableRegion` for every one** (`DataPanel.java:205-237`, allocation at 226-228). There is **no culling against `visibleRect`/`damageRect`** (available at line 139-140). With 900 tracks this allocates ~900 heap objects per paint per panel even though only ~20 are on screen.
3. `painter.paint(...)` → `DataPanelPainter.paintFrame` (`src/main/java/org/broad/igv/ui/panel/DataPanelPainter.java:132-190`) — this one **does** cull offscreen tracks (break at 141-143, continue at 163-171), so `draw()` (234-250) runs only for on-screen tracks. Already good.

`DataTrack.render` (`src/main/java/org/broad/igv/track/DataTrack.java:154-165`) pulls scores from the per-frame `loadedIntervalCache` (76-80; `getInViewScores` 184-199). If the interval is missing or for a different chromosome it returns an empty list (191-193) — i.e. paint does **not** block on data; the async load pipeline (below) fills the cache.

### 2.2 Data access per track (off EDT, first paint / pan / zoom)
`IGV.repaint` (`IGV.java:2280-2354`) is the IGV-X async load pipeline: for every frame and **every visible track of every panel** (`visibleTracks`, `IGV.java:2377-2382` — no viewport culling) it checks `isReadyToPaint` (2322) and queues `CompletableFuture.runAsync(() -> track.load(frame), threadExecutor)` (2323) on `IGV.threadExecutor` — a **fixed 5-thread pool** (`IGV.java:2392`). Then `allOf` → autoscale (2345) → EDT repaint (2346-2349).

`DataTrack.load` (`DataTrack.java:121-151`, `synchronized` per track) → `getSummaryScores` (148) → `DataSourceTrack.getSummaryScores` (`DataSourceTrack.java:100-110`) → `AbstractDataSource.getSummaryScoresForRange` (`AbstractDataSource.java:138-161`) → `BBDataSource.getPrecomputedSummaryScores` (`BBDataSource.java:116-158`):
1. `reader.zoomLevelForScale(scale)` (`BBFile.java:445-461`) — in-memory zoomHeaders scan (cheap).
2. `reader.getIdForChr(chr)` (`BBFile.java:375-409`) — in-memory `chromTree.getIdForName` + optional genome alias lookup (384-397) + lazy case-insensitive index (416-432). Cheap after the tree is parsed.
3. `getLeafChunks(chr, start, chr, end, rTreeOffset)` (`BBFile.java:320-367`): **`RPTree.loadTree(this.path, treeOffset)`** (336) is cached in `rTreeCache` (a plain `HashMap`, line 106, keyed by tree offset, 334-338) — first access per zoom level per file parses the R-tree (one file read); subsequent calls hit the cache. Then one consolidated data read (344-361) + `decodeZoomData` (551-599, allocates a `WigDatum` per score).
4. Uncompression: `new CompressionUtils().decompress(...)` per chunk (554-555).

**First paint after session load**: the session load ends with `revalidateTrackPanels` (`IGV.java:1044`, EDT via 2233-2238) → `repaint(rootPane)` (2236) → the pipeline at 2272-2277 collects **all 900 tracks** → 900 `track.load` futures queued on the 5-thread pool → ~900 file opens (R-tree parse + leaf read each) serialized through 5 threads, then a full autoscale + repaint. Offscreen tracks are loaded too (no viewport culling in `visibleTracks`).

**Pan/zoom**: intervals are cached per frame (`DataTrack.java:76-80`, invalidated/kept on `FrameManager.ChangeEvent` 87-102; `isReadyToPaint` 110-118 checks coverage). When the window moves, all 900 tracks (onscreen and off) re-queue loads. With 5 worker threads and 900 files, every scroll triggers a wave of ~900 file reads.

**Whole-genome view** (`Globals.CHR_ALL`): `BBDataSource.getWholeGenomeScores` (181-230) loads **all** zoom data once per window function and caches in `wholeGenomeScores` (52, 186-223) — 900 full-genome reads on first whole-genome paint (expensive, then cached).

### 2.3 What already exists (do not duplicate)
- Offscreen culling in `paintFrame` (`DataPanelPainter.java:141-171`).
- Per-frame interval cache (`DataTrack.java:76-102, 184-199`) + `isReadyToPaint` short-circuit (110-118).
- Per-file `rTreeCache` (`BBFile.java:106, 334-338`).
- Whole-genome scores cache (`BBDataSource.java:52, 186-223`).
- Async, non-EDT data loads + coalesced repaint (`IGV.java:2318-2354`).
- `AbstractDataSource.summaryTileCache` LRU (10) exists but is not on the BB zoom path (BB returns precomputed zoom data at `AbstractDataSource.java:144-147`).

## 3. EDT WORK (what runs on the event dispatch thread)

There is **no `SwingUtilities.isEventDispatchThread` guard anywhere in `IGVSessionReader`** (grep returned zero hits). The actual threading:

| Work | Thread | Evidence |
|---|---|---|
| Session XML parse (`Utilities.createDOMDocumentFromXmlStream`) | LongRunningTask worker (off EDT) | `OpenSessionMenuAction.java:82` → `LongRunningTask.submit` (`LongRunningTask.java:55-62`, pool of 5, line 47); startup via `StartupRunnable` (`IGV.java:1880`) |
| The 900 per-file loads (`igv.load` → `TrackLoader` → `BBFile.init`/`readHeader`) | 900 raw threads (off EDT) | `IGVSessionReader.java:429-431` |
| Per-file status-bar updates | **Blocking EDT round-trips** (worker threads block on EDT) | `IGV.java:1272,1302` → `ApplicationStatusBar.setMessage3` (`ApplicationStatusBar.java:136-142`) uses `invokeAndWaitOnEventThread` + `paintImmediately` |
| `panel.addTrack(track)` / track-panel model mutation | LongRunningTask worker (off EDT; Swing model mutation off EDT — correctness smell, cheap) | `IGVSessionReader.java:276`; `TrackPanel.java:243-264` |
| `revalidateTrackPanels()` | marshaled to EDT (`invokeOnEventThread`) | `IGV.java:2233-2238`, called from `loadSessionFromStream` (`IGV.java:1044`) |
| Track data loads (`track.load(frame)`) interactive mode | `IGV.threadExecutor` (5 threads, off EDT) | `IGV.java:2323,2392` |
| Track data loads, **batch mode** | **EDT, synchronous** | `IGV.java:2285-2307` (`invokeAndWaitOnEventThread`, loads at 2294) |
| Painting: `paintComponent`, `computeMousableRegions`, `paintFrame`, `draw`, `track.render` | EDT (Swing paint) | `DataPanel.java:130-151`, `DataPanelPainter.java:132-190,234-250` |

Net: heavy I/O is off the EDT, but (1) each of the 900 load threads blocks on the EDT status-bar update twice (`invokeAndWait` + forced paint) — a serialization bottleneck; (2) all paint work, including the 900-object `computeMousableRegions` allocation, is on the EDT; (3) track model mutation happens off-EDT (latent Swing thread-safety issue, not a measured hot path).

## 4. PROGRESS REPORTING (current state)

- **No `ProgressBar`/`ProgressMonitor` is used for session loading.** `ProgressBar.java` (`src/main/java/org/broad/igv/ui/util/ProgressBar.java`) exists and supports `showProgressDialog` (177-178), but its only real consumers are the downloader (`Downloader.java:115`, `monitor.setProgress(percent)`) and `IGV.closeWindow` (`IGV.java:1086`).
- Status bar is the only feedback, and it is churny/blocking: `"Opening session..."` (`IGV.java:970`), per-file `"Loading <path>"` then clear (`IGV.java:1272,1302` — 1800 `invokeAndWait`+paint round-trips), `"Loading ..."` in `loadTracks` (`IGV.java:377`).
- The only numbers shown are **memory**: `ApplicationStatusBar.MemoryUpdateTask` (`ApplicationStatusBar.java:180-199`) displays used/total MB on a 1 s timer. No files-loaded/total counter exists anywhere.
- `LongRunningTask` shows only an hourglass (`LongRunningTask.java:71`) and logs `log.debug("Total load time = " + dt)` (`IGVSessionReader.java:448-449`).

So there is **no honest progress** — the user cannot tell whether 10 or 800 of 900 tracks remain, and the per-file status churn actively slows the load (Section 1.2).

## 5. RECOMMENDED FIXES (ordered by impact/risk)

### (a) Bounded thread pool for session resource loads — HIGH impact, LOW risk
- **File/method:** `src/main/java/org/broad/igv/session/IGVSessionReader.java`, `processResources`, lines 376-441 (TODO at 376, spawn at 429-431, join at 435-441).
- **Current:** one `new Thread(runnable)` per resource; unbounded (900 threads); join loop after.
- **Proposed change** (replaces lines 429-431/435-441, keeps `synchronousLoads` batch path at 426-427/443-446 exactly as-is):
```java
int nThreads = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
ExecutorService executor = Executors.newFixedThreadPool(nThreads);
List<Future<?>> futures = new ArrayList<>(dataFiles.size());
...
if (Globals.isBatch() || !hasTrackElments) {
    synchronousLoads.add(runnable);
} else {
    futures.add(executor.submit(runnable));
}
...
for (Future<?> f : futures) {
    try { f.get(); } catch (Exception e) { log.error("Error waiting for session load", e); }
}
executor.shutdown();
for (Runnable runnable : synchronousLoads) { runnable.run(); }
```
Also make `errors` thread-safe (line 374): `Collections.synchronizedList(new ArrayList<String>())` (or a per-task list merged after join) — `allTracks`/`erroredResources` are already synchronized.
- **Risk:** low. Exceptions are already caught inside the runnable (417-422), so `f.get()` cannot leak a task failure; ordering was already nondeterministic. `Globals.isBatch()` and the no-`<Track>`-elements synchronous path are preserved verbatim.
- **Test:** load the 900-track session; assert same track count/errors as before; verify no thread explosion (`jstack` or thread-count monitor); run batch-mode regression.

### (b) Lazy BBFile init — MEDIUM-HIGH impact, MEDIUM risk
- **File/method:** `src/main/java/org/broad/igv/ucsc/bb/BBFile.java`, `init()`/`readHeader()`, lines 135-275.
- **Current:** `readHeader()` eagerly performs reads A (64-byte common header), B (zoom headers+autosql+totalSummary), C (chromTree), plus D (bigBed dataCount) and E (extended header).
- **Proposed change:** split `readHeader()` into `readCommonHeader()` (keep only read A — the 64 bytes, which set `type`, `byteOrder`, `header` offsets, `uncompressBuffSize`) and lazy accessors for everything else. Safe lazy pattern: `private volatile boolean zoomLoaded; private synchronized void ensureZoomData()` (double-checked), called from `zoomLevelForScale` (445-461) / `getAutoSQL` (167) / `getTotalSummary` (139); `ensureChromTree()` from `getIdForChr` (375-409), `getChrForId` (434-436), `getChromosomeNames` (171-173), `getIdForChrCaseInsensitive` (416-432); extended header from `isSearchable` (463-465)/`getSearchTrees` (686-698); bigBed `dataCount`/`featureDensity`/codec from `getFeatureDensity` (163-165). Keep `header != null` after the common read — `getLeafChunks` (322-324) and `search` (479-481) already guard on `header == null` and would otherwise trigger a full re-read.
- **Who touches what (verified by repo-wide grep):**
  - `zoomHeaders` — only `zoomLevelForScale` (`BBFile.java:445-461`), called from `BBDataSource.getPrecomputedSummaryScores` (`BBDataSource.java:136`) and `getWholeGenomeScores` (198). First use is at first paint. **Lazy-safe.**
  - `autosql` — only `getAutoSQL()` (167); no external callers in the repo; inside `readHeader` it feeds `BBCodecFactory.getCodec` (262) for bigBed. **Lazy-safe for bigWig** (defer codec creation to `decodeFeatures`, 520-549, for bigBed).
  - `totalSummary` — only `getTotalSummary()` (139); no external callers; `computeStats` (735-768) is a manual debug utility. **Fully lazy / never needed for bigWig.**
  - `chromTree`/`chrNames` — `getIdForChr`, `getChrForId`, `getChromosomeNames`; external callers are `BBDataSource.getWholeGenomeScores` (191-193), `BBFeatureSource.getChromosomeNames` (`BBFeatureSource.java:173-174`, bigBed), and genome annotation sources `ChromAliasBB` (63-64)/`CytobandSourceBB` (33-34) — none of which run during session bigWig load. **Lazy-safe.**
  - `featureDensity` — `BBFeatureSource` ctor (`BBFeatureSource.java:68`), bigBed only; compute lazily inside `getFeatureDensity()` so bigBed load behavior is unchanged.
  - Extended header — search only (`isSearchable`/`getSearchTrees`/`search`). **Lazy-safe.**
- **Risk:** medium. Type detection (`isBigWigFile`, needed at `TrackLoader.java:824`) is preserved because the magic read stays eager. All lazy init must be **synchronized** (session threads at load, `threadExecutor` threads + possibly EDT during paint); while touching the file, make `rTreeCache` (106) a `ConcurrentHashMap` (currently a plain `HashMap` mutated from multiple load threads).
- **Test:** unit test — construct `BBFile`, assert `isBigWigFile()` works with no reads beyond the 64-byte header; call `getSummaryScoresForRange` and assert zoom headers/chromTree parse exactly once; bigBed regression (load a `.bb` file, `BBFeatureSource` ctor still gets `featureDensity`); load the 900-track session and compare load time + correctness.

### (c) Skip offscreen tracks in `computeMousableRegions` — MEDIUM impact, LOW-MEDIUM risk
- **File/method:** `src/main/java/org/broad/igv/ui/panel/DataPanel.java`, `computeMousableRegions`, lines 205-237; call site `paintComponent` line 149 (has `visibleRect`/`damageRect` at 139-140).
- **Current:** iterates every visible track of every group and allocates `Rectangle` + `MouseableRegion` for each (226-228), regardless of the viewport. `paintFrame` already culls (`DataPanelPainter.java:141-171`); `computeMousableRegions` does not.
- **Proposed change:** pass `visibleRect` (or `damageRect`) in; inside the loop mirror the `paintFrame` culling: `if (trackY + trackHeight < visibleRect.y) { trackY += trackHeight; continue; } if (trackY > visibleRect.y + visibleRect.height) break;` before allocating (account for group gaps at 216-218 first).
- **Risk:** low-medium. Regions are rebuilt on every paint, and scrolling triggers paint, so newly revealed tracks get regions on the next paint. Mouse hit-testing/tooltips/popup menus operate on on-screen tracks. Edge: drag/tooltip initiated while scrolled should still resolve because the region list is rebuilt at the start of the very paint that shows the new view.
- **Test:** click/popup/tooltip on tracks at the scroll boundary; scroll mid-drag; verify no NPE from missing regions; measure paint time with 900 tracks before/after (expect ~900 → ~visible allocations per paint).

### (d) Reduce per-paint work (what already exists + remaining gaps) — MEDIUM impact, LOW risk
Already in place (do not re-add): offscreen culling in `paintFrame`; per-frame `loadedIntervalCache`; `isReadyToPaint` short-circuit; per-file `rTreeCache`; whole-genome scores cache; the async load/coalesced-repaint pipeline.
Remaining gaps:
1. **Load only on-screen tracks' data.** `IGV.repaint` (`IGV.java:2272-2277`, `visibleTracks` 2377-2382) collects ALL visible tracks of ALL panels with no viewport test, so pan/scroll re-queues ~900 loads. Restrict the track list to tracks whose panel y-range intersects the viewport (the y math is already done in `paintFrame`; expose a `visibleTracks(DataPanelContainer, Rectangle)` or pass the damage rect).
2. **Bump `IGV.threadExecutor` from 5 to `min(8, availableProcessors)`** (`IGV.java:2392`) and cap `LongRunningTask`'s pool similarly (`LongRunningTask.java:47`) so the first-paint burst of 900 loads drains faster without a thread storm.
3. **Thread-safety hygiene:** `rTreeCache` (`BBFile.java:106`) is a plain `HashMap` written from multiple load threads — make it a `ConcurrentHashMap` (also true once fix (b) lazifies init).
4. **Memoize `getIdForChr`** results per (file, chr) — `chrAliasTable` (`BBFile.java:377-396`) already caches alias outcomes; add a small `ConcurrentHashMap<String,Integer>` for resolved ids to avoid repeated `chromTree.getIdForName` + genome alias lookups per paint.
- **Test:** profile `DataPanel.paintComponent` and `IGV.repaint` with 900 tracks; assert repeated paints after settle issue zero file reads; pan and confirm only newly-exposed tracks read.

### (e) Honest progress reporting — LOW impact, LOW risk
- **File/method:** `IGVSessionReader.processResources` (counter) + `IGV.load` (remove per-file status churn, `IGV.java:1272,1302`) + `ApplicationStatusBar.setMessage3` (`ApplicationStatusBar.java:136-142`).
- **Current:** per-file `"Loading <path>"`/clear with `invokeAndWait` + `paintImmediately` per call; no total; only memory numbers are shown (Section 4).
- **Proposed change:**
  1. In `processResources`, maintain `AtomicInteger loaded` / `int total = dataFiles.size()`; after each runnable completes, increment and (throttled, e.g. every 25 files or 250 ms) call `setStatusBarMessage3("Loading session: " + loaded + "/" + total)"`.
  2. In `IGV.load`, drop the per-file `setStatusBarMessage3` calls entirely (they serialize the load through the EDT — Section 1.2).
  3. Change `ApplicationStatusBar.setMessage3` to `UIUtilities.invokeOnEventThread` with `setText` only (no `paintImmediately`; the current code also paints `messageBox2.getBounds()` — a copy-paste bug at line 140). Same for `setMessage`/`setMessage2` if used in hot loops.
  4. Optional: `ProgressBar.showProgressDialog` (files-loaded/total, determinate) wired to the same counter and to `StopEvent` (the stop button already posts `StopEvent`, `ApplicationStatusBar.java:84`).
- **Risk:** low.
- **Test:** load the 900-track session; verify the counter advances monotonically, EDT stays responsive during load, and total message disappears on completion.

### Suggested order of implementation
1. (e-2)/(e-3) remove per-file EDT status churn + (a) bounded pool → biggest, cheapest load-time win (removes 1800 blocking EDT round-trips and 900 threads).
2. (b) lazy `BBFile` init → cuts load-time file reads per bigWig from 3-5 to 1 (64-byte header), keeping paint behavior identical.
3. (c) viewport-cull `computeMousableRegions` → removes ~98% of per-paint allocation with 900 tracks.
4. (d) scope data loads to on-screen tracks + raise pools + `ConcurrentHashMap` hygiene.
5. (e-1)/(e-4) honest files-loaded/total progress.

### Notes / caveats
- All line numbers are from the current working tree (verified by reading the code); they may shift after edits.
- Read counts per bigWig at load are 3 minimum (64-byte common header; zoom/autosql/totalSummary block; chromTree buffer), 4-5 with an extended header; bigBed adds the dataCount read — the original "4-6" estimate is accurate for bigBed and slightly high for plain bigWig.
- Remote bigWigs: every `getBytes` opens a new `IGVSeekableHTTPStream` (`IGVSeekableStreamFactory.java:67-69`) — the lazy-init fix (b) matters even more for HTTP sessions (fewer connection setups), and a per-file stream/connection reuse optimization is a further (out-of-scope) win.

