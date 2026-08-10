# IGV-X Large-Session Performance — Summary

Full report: docs/performance-analysis-large-sessions.md (all line numbers verified against the working tree).

## LOAD TIME (900 bigWigs)
- IGVSessionReader.processResources (IGVSessionReader.java:372-462) spawns ONE raw thread per data file (429-431; TODO at 376) → 900 threads, ~900 concurrent file opens, plus a data race on the unsynchronized `errors` ArrayList (374/421).
- Every file load calls IGV.load (IGV.java:1268) which sets/clears the status bar per file (1272/1302) → ApplicationStatusBar.setMessage3 (ApplicationStatusBar.java:136-142) does invokeAndWaitOnEventThread + paintImmediately → 1800 BLOCKING EDT round-trips that serialize the whole "concurrent" load through the EDT. Biggest load-time cost.
- Per bigWig, new BBFile (TrackLoader.java:822) → readHeader (BBFile.java:175-275) eagerly does 3 reads minimum (64-byte common header; zoomHeaders+autosql+totalSummary block; chromTree buffer), 4-5 with extended header, +1 for bigBed. Every read opens a brand-new SeekableStream (IGVSeekableStreamFactory.java:60-78 — no caching). ~2,700 file opens for 900 files.
- BBDataSource ctor (59-64), DataSourceTrack ctor (57-65), DataTrack ctor (78-82), AbstractTrack.init (135-148) are all cheap — no eager data work. DataTrack ctor does subscribe to the event bus (81) × 900.

## PAINT TIME
- DataPanel.paintComponent (DataPanel.java:130-151) calls computeMousableRegions (205-237) which allocates a Rectangle+MouseableRegion for EVERY visible track on EVERY paint — no viewport culling (visibleRect is available at 139). ~900 allocations/paint.
- DataPanelPainter.paintFrame (132-190) DOES cull offscreen (141-143, 163-171) — already good.
- Data loads happen off-EDT via IGV.repaint pipeline (IGV.java:2318-2354) on IGV.threadExecutor = 5 threads (2392): first paint after load queues ~900 track.load futures — ALL visible tracks of ALL panels (visibleTracks 2377-2382, no viewport test) → ~900 file reads on pan/zoom too.
- Per-track data path: DataTrack.load (121-151) → BBDataSource.getPrecomputedSummaryScores (116-158) → zoomLevelForScale (445-461) → getIdForChr (375-409) → getLeafChunks (320-367): RPTree cached in rTreeCache (a plain HashMap, 106/334-338), then one consolidated read + decompress + decodeZoomData.
- Already cached: per-frame LoadedDataInterval (DataTrack 76-102, 184-199), rTreeCache, whole-genome scores (BBDataSource 52/186-223), async coalesced repaint.

## EDT WORK
- Session XML parse + 900 per-file loads + panel.addTrack: OFF EDT (OpenSessionMenuAction.java:82 → LongRunningTask, 5-thread pool at LongRunningTask.java:47; startup via IGV.java:1880). No EDT guards in IGVSessionReader.
- Status-bar updates: BLOCKING EDT round-trips (above). revalidateTrackPanels: EDT (IGV.java:2233-2238).
- Painting (paintComponent/computeMousableRegions/paintFrame/render): EDT. Batch mode loads on EDT (IGV.java:2285-2307).

## PROGRESS
- No ProgressBar/ProgressMonitor for session load (ProgressBar only used by Downloader.java:115 and IGV.closeWindow:1086). Status bar shows only per-file "Loading <path>" churn and MEMORY numbers (ApplicationStatusBar.MemoryUpdateTask 180-199). No files-loaded/total counter.

## RECOMMENDED FIXES (impact/risk order)
1. (a) Bounded ExecutorService (min(8, cores)) replacing new Thread at IGVSessionReader.java:429-441, preserving synchronousLoads batch path; also synchronize `errors`. LOW risk, HIGH impact.
2. (e) Remove per-file setStatusBarMessage3 churn (IGV.java:1272/1302) + make setMessage3 non-blocking (invokeOnEventThread, drop paintImmediately; fixes bug painting messageBox2 at line 140). LOW risk, HIGH impact.
3. (b) Lazy BBFile init: keep only the 64-byte common-header read; defer zoomHeaders/autosql/totalSummary/chromTree/extendedHeader via synchronized lazy accessors (verified: no load-time callers for bigWig; getFeatureDensity needed by bigBed BBFeatureSource ctor). MEDIUM risk.
4. (c) Viewport-cull computeMousableRegions (pass visibleRect, mirror paintFrame culling). LOW-MED risk, removes ~98% of per-paint allocation.
5. (d) Scope data loads to on-screen tracks in IGV.repaint; bump threadExecutor 5→min(8,cores); ConcurrentHashMap for rTreeCache; memoize getIdForChr.
6. (e) AtomicInteger files-loaded/total counter with throttled EDT update; optional determinate ProgressBar dialog.

No source files were modified (read-only analysis).
