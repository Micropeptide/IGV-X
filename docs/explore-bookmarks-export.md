# IGV-X Exploration Report: Bookmarks/Highlights & High-Quality Export

**Scope:** `/Users/runtianwu/Rdirectory/IGV-X` (source under `src/main/java/org/broad/igv/`). All line numbers verified against the current tree on 2026-08-10.

---

## Executive summary (conclusion first)

**A) Bookmarks/highlights — IGV-X already has a working, session-persisted region system, but no "bookmark" concept and no region highlighting UI.**

- There is **no `bookmark`/`Bookmark` symbol anywhere** in `src/main/java` (grep: 0 hits). There is **no `FeatureList` UI**. Upstream-style "highlight" is a dead API: `FeatureRenderer.setHighlightFeature(...)` has **no callers**.
- What IGV-X *does* have is **Regions of Interest (ROI)**: `RegionOfInterest` objects stored per-chromosome in `Session.regionsOfInterest` (a `Map<String, Collection<RegionOfInterest>>`), displayed as red bars in the header ruler and as vertical lines over the data panel, editable in the `RegionNavigatorDialog`, and **persisted in the session XML** (`<Regions><Region …/></Regions>`) by `SessionWriter` and reloaded by `IGVSessionReader`. ROI are also the only "highlight"-like thing: `DataPanel.drawAllRegions` draws the currently "selected" region's boundary lines even when the region-bar preference is off.
- Conclusion: **implement bookmarks as an extension of the existing ROI machinery** (a `Bookmark` subtype or a parallel `Session` list with its own XML element) rather than inventing a new subsystem. Region highlighting is best implemented as a per-ROI `highlighted` flag + color stored on `RegionOfInterest`, drawn by `DataPanel.drawRegion` / `RegionOfInterestPanel.drawRegionsOfInterest`.

**B) High-quality export — a complete PNG/SVG pipeline exists; PDF is declared but unimplemented; there are no DPI/scale options and no selected-tracks-only filter.**

- Export entry points: menu items call `IGV.saveImage(...)` → `IGV.createSnapshot(...)` → `IGV.createSnapshotNonInteractive(...)` → `SnapshotUtilities.doComponentSnapshot(...)` → `Paintable.paintOffscreen(Graphics2D, Rectangle, boolean batch)`. `SnapshotUtilities` requires the target to implement `org.broad.igv.ui.panel.Paintable`.
- Formats: **PNG** (BufferedImage + `ImageIO.write`) and **SVG** (Apache Batik `SVGGraphics2D`) work today. `ImageFileTypes.Type` also declares **PDF (`.pdf`)**, but `IGV.createSnapshotNonInteractive` (lines 638–642) and `SnapshotUtilities.doComponentSnapshot` (lines 111–115) explicitly reject it (and EPS/JPEG).
- Options today: none. No DPI, no scale, no resolution choice; only `batch` (affects `getSnapshotHeight(batch)`) and a static `SnapshotUtilities.maxPanelHeight`. HiDPI is handled globally via `Globals.DESIGN_DPI = 96` (`src/main/java/org/broad/igv/Globals.java:41`) and screen-resolution scaling in `Main.java:314` / `PreferencesManager.java:73` — nothing user-facing.
- The **selected-tracks-only filter belongs in `DataPanelPainter.paintFrame`** (`src/main/java/org/broad/igv/ui/panel/DataPanelPainter.java:132–190`, the loop `for (Track track : trackList)` at line 160), plus matching height computation in `TrackPanelScrollPane.getSnapshotHeight` / `MainPanel.paintOffscreen` so the exported image isn't over/under-sized.

---

## A) GENE/REGION BOOKMARKS + HIGHLIGHTS

### A.1 Search results (negative + positive)

| Term | Hits in `src/main/java` | Notes |
|---|---|---|
| `bookmark` / `Bookmark` | **0** | Feature does not exist |
| `RegionNavigatorDialog` | 7 files | UI for ROI table (`ui/panel/RegionNavigatorDialog.java`, `ui/action/NavigateRegionsMenuAction.java`, `ui/action/ExportRegionsMenuAction.java`, `ui/IGV.java:989–991`) |
| `RegionOfInterest` | ~30 files | The de-facto bookmark |
| `highlightRegion` / `feature highlight` | 0 | no such symbols |
| `setHighlightFeature` | only definition | `renderer/FeatureRenderer.java:52` — **no callers** |
| `FeatureList` | only `FeatureCollectionSource.getFeatureList(...)` | data-source helper, not a UI list |

### A.2 How IGV-X lets users mark regions of interest (current behavior)

**Data model** — `src/main/java/org/broad/igv/feature/RegionOfInterest.java` (class at line 38):
- Fields: `String chr` (40), `String description` (41), `int start` 0-based (42), `int end` 0-based (43), static `Color backgroundColor = RED` (44), static `Color foregroundColor = BLACK` (45), `boolean selected` (46).
- API: ctor `RegionOfInterest(String chromosomeName, int start, int end, String description)` (58); `getTooltip()` (66); `getChr()` (70); `setDescription`/`getDescription` (74/78); `setEnd`/`setStart` (83/87); `getEnd` (91); `getDisplayEnd` (99); `getStart` (103); `getCenter` (107); `getLength` (111); `getDisplayStart` (119, = start+1); static `getBackgroundColor`/`getForegroundColor` (123/127); `getLocusString()` (132).
- Note: colors are **static class-wide** — all ROI share the same red. Per-region color/highlight state does not exist yet.

**Storage** — `src/main/java/org/broad/igv/session/Session.java`:
- Field `private Map<String, Collection<RegionOfInterest>> regionsOfInterest;` (line 90, `LinkedHashMap` initialized at 108); observable `regionsOfInterestObservable` (93, 109).
- API: `Collection<RegionOfInterest> getRegionsOfInterest(String chr)` (335); `Collection<RegionOfInterest> getAllRegionsOfInterest()` (344); `boolean removeRegionsOfInterest(Collection<RegionOfInterest> rois)` (360); `void addRegionOfInterestWithNoListeners(RegionOfInterest roi)` (376 — despite the name it *does* call `setChangedAndNotify()` at 386); `void clearRegionsOfInterest()` (389); `ObservableForObject<Map<String, Collection<RegionOfInterest>>> getRegionsOfInterestObservable()` (528).

**Ways a region gets created** (all funnel through `IGV.addRegionOfInterest` or the session map):
1. **Drag tool**: toolbar toggle button in `src/main/java/org/broad/igv/ui/commandbar/IGVCommandBar.java:247` → `IGV.beginROI(JButton)` (`ui/IGV.java:332–346`) installs `RegionOfInterestTool` on every `DataPanel`; drag/click creates ROI (`ui/panel/RegionOfInterestTool.java:48`; `createRegion(String, int, int)` at 79–85; calls `IGV.getInstance().addRegionOfInterest(...)` at 148 and 202).
2. **Keyboard**: `ui/GlobalKeyDispatcher.java:214–220` and 235–241 create ROI and call `igv.addRegionOfInterest`.
3. **Dialog**: `RegionNavigatorDialog.AddRegionAction` (`ui/panel/RegionNavigatorDialog.java:678–709`) adds ROI for the current frame range.
4. **Import**: `ui/action/ImportRegionsMenuAction.java` (`igv.addRegionOfInterest` at 123).
5. **Header context menu**: `ui/panel/HeaderPanel.java:232` builds a transient ROI for the current frame.

**Central add path** — `ui/IGV.java:326–330`:
```java
public void addRegionOfInterest(RegionOfInterest roi) {
    session.addRegionOfInterestWithNoListeners(roi);
    RegionOfInterestPanel.setSelectedRegion(roi);
    repaint();
}
```

**Display (this is the "highlight" today):**
- `ui/panel/RegionOfInterestPanel.java` (lives in `HeaderPanel` at `HeaderPanel.java:215–220`): `drawRegionsOfInterest(Graphics2D, int)` (93–121) fills red rectangles (`g.fillRect(start, 0, regionWidth, height)`, line 118) in the header ruler; mouse adapter (235+) supports ctrl-drag to resize; right-click popup `getPopupMenu(...)` (146–224) offers Zoom (154), Edit description (168), Copy sequence (180), BLAT (205), Delete (216).
- `ui/panel/DataPanel.java`: `drawAllRegions(Graphics g)` (289–313) draws vertical lines per ROI: line 299 reads pref `Constants.SHOW_REGION_BARS` (`prefs/Constants.java:84`), line 304 draws the **selected** region even when bars are off: `if (drawBars || regionOfInterest == RegionOfInterestPanel.getSelectedRegion())`; `drawRegion(Graphics2D, RegionOfInterest)` (315–335) draws the two boundary lines using `regionOfInterest.getForegroundColor()` (331).
- `RegionOfInterestPanel.selectedRegion` static singleton (66; get/set 227/233) = the single "highlighted" ROI.
- `ui/panel/RegionMenu.java` (43–165): per-region popup with "Sort by …" actions and "Scatter Plot …".

**RegionNavigatorDialog** — `src/main/java/org/broad/igv/ui/panel/RegionNavigatorDialog.java` (850 lines):
- Class decl line 64; singleton `getOrCreateInstance(Frame)` / `getInstance()` / `destroyInstance()` (93/105/113); table columns Chr/Start/End/Description (table model at 538–562, column indexes 69–72).
- Sync: `synchRegions()` (144–165) reads `IGV.getInstance().getSession().getAllRegionsOfInterest()` (167–169); subscribes to `ViewChange` events and the ROI observable (191–193); table edits write back via `updateROIFromRegionTable(int)` (322–354, 0-based ↔ 1-based conversion).
- Buttons/actions: **Add** (`AddRegionAction`, 678–709), **Remove** (`RemoveSelectedRegionsAction`, 711–733, calls `session.removeRegionsOfInterest`), **View** (`ViewSelectedAction`, 736–777, builds an on-the-fly `GeneList` and calls `IGV.setGeneList` + `resetFrames`), **Show All Chrs** checkbox (779), **Zoom to Region** checkbox (597), search field + filter (215–271), row popup with Copy Sequence / Copy Details (799–848).
- Opened from menu: `ui/action/NavigateRegionsMenuAction.java:62` → `RegionNavigatorDialog.getOrCreateInstance(mainFrame.getMainFrame())`; menu item "Region Navigator ..." in `ui/IGVMenuBar.java:748`.

**Menu wiring** — `ui/IGVMenuBar.java`, `createRegionsMenu()` (742–784):
- "Region Navigator ..." (748), "Gene Lists..." (753), "Export Regions ..." (`ExportRegionsMenuAction`, 765), "Import Regions ..." (`ImportRegionsMenuAction`, 771), Clear-Regions item commented out (776).

### A.3 Session serialization (where bookmarks would be added)

There is **no `XMLSessionReader`/`XMLSessionWriter` in this fork**. The equivalent classes are:
- **Writer:** `src/main/java/org/broad/igv/session/SessionWriter.java` (single writer for all session types).
- **Readers:** `SessionReader` interface (`session/SessionReader.java:35`) with implementations `IGVSessionReader` (84), `UCSCSessionReader` (56), `IndexAwareSessionReader` (56). `ui/IGV.java:1025–1034` picks the reader by file extension; `IGV.saveSession(File)` at 1055–1056 calls `(new SessionWriter()).saveSession(session, targetFile)`.

**SessionWriter.java** — document assembly in `createXmlFromSession(Session, File)` (107–185): root `<Session version=8 …>` (120–122), then in order: `writeResources` (153), `writePanels` (156), `writePanelLayout` (159), **`writeRegionsOfInterest` (162)**, `writeFilters` (165), `writeGeneList` (168), `writeHiddenAttributes` (173). `CURRENT_VERSION = 8` (68).
- `writeRegionsOfInterest(Element, Document)` (234–251) emits:
```xml
<Regions>
  <Region chromosome="chr1" start="1000" end="5000" description="..."/>
</Regions>
```
  using `SessionElement.REGIONS`/`REGION` and `SessionAttribute.CHROMOSOME`/`START_INDEX`/`END_INDEX`/`DESCRIPTION`.
- Constants: `session/SessionElement.java` — `SESSION` (29), `GLOBAL` (30), `REGION` (31), `REGIONS` (32); `session/SessionAttribute.java` — `CHROMOSOME` (12), `END_INDEX` (13), `START_INDEX` (34), `DESCRIPTION` (58), `VERSION` (36).
- **Where a `<Bookmarks>` element goes:** add a sibling call in `createXmlFromSession` next to `writeRegionsOfInterest` (line 162) and a new `writeBookmarks(...)` method mirroring 234–251.

**IGVSessionReader.java** — element dispatch in `process(Session, Node, String)` (293–330): node-name if/else chain routes `Resources` (302), `Regions` (307), `Region` (309), `GeneList` (311), `Filter` (313), `FilterElement` (315), `Panel` (325), `PanelLayout` (327), `HiddenAttributes` (329). Region handlers: `processRegions` (590–595, clears then recurses) and `processRegion` (597–609, builds `RegionOfInterest` and calls `igv.addRegionOfInterest(region)` at 605). **A new `processBookmarks`/`processBookmark` pair would be added to this dispatcher**, plus the recursive `process(Session, NodeList, String)` at 1047.
- Also uses `SessionWriter`: `session/autosave/SessionAutosaveManager.java` (49, 90), `ui/action/ReloadTracksMenuAction.java:74`, `batch/CommandExecutor.java:1183` — bookmarks automatically ride along.

### A.4 UI components inventory (add/jump/remove)

| Action | Class / line |
|---|---|
| Add region (drag tool) | `ui/commandbar/IGVCommandBar.java:247` → `ui/IGV.java:332 beginROI(JButton)`; `ui/panel/RegionOfInterestTool.java:56,79,146,199` |
| Add region (keyboard) | `ui/GlobalKeyDispatcher.java:214–220, 235–241` |
| Add region (dialog) | `ui/panel/RegionNavigatorDialog.java:678 AddRegionAction` |
| Jump/navigate to region | `RegionNavigatorDialog.ViewSelectedAction` (736) → `IGV.setGeneList`; `RegionOfInterestPanel` popup "Zoom" (154) → `frame.jumpTo(chr, start, end)` |
| Remove region | `RegionNavigatorDialog.RemoveSelectedRegionsAction` (711); `RegionOfInterestPanel` popup "Delete" (216) |
| Edit description / coords | `RegionNavigatorDialog` table edit (322–354); popup "Edit description..." (`RegionOfInterestPanel.java:168`) |
| Export/Import region lists | `ui/action/ExportRegionsMenuAction.java:71 exportRegionsOfInterest()`; `ui/action/ImportRegionsMenuAction.java` |
| Highlight (selected region) | `RegionOfInterestPanel.setSelectedRegion` (231); drawn in `DataPanel.drawAllRegions` (304) |
| Dead feature-highlight API | `renderer/FeatureRenderer.java:46–53` (field `highlightFeature` 46; getter 48; setter 52); consumed only by `renderer/IGVFeatureRenderer.java:288–292` (cyan rect) — no caller sets it |

### A.5 Implementation plan — Bookmarks + Region highlights

**Design decision:** reuse `RegionOfInterest` (already persisted + observable + navigable) rather than a parallel subsystem. Recommended:

1. **New class** `src/main/java/org/broad/igv/feature/Bookmark.java extends RegionOfInterest` (or add a `boolean bookmark` flag + `Color color` + `boolean highlighted` to `RegionOfInterest`). Prefer the subtype to avoid touching every ROI call site; ROI creation sites stay bookmark=false.
2. **Session storage** (`session/Session.java`): add `private Map<String, Collection<Bookmark>> bookmarks;` + `getBookmarks(String chr)`, `getAllBookmarks()`, `addBookmark(Bookmark)`, `removeBookmarks(Collection<Bookmark>)`, `clearBookmarks()`, and a `bookmarksObservable` mirroring lines 90–109 / 335–395 / 528. Alternatively store a flag on ROI and filter — but a separate map keeps ROI semantics untouched.
3. **Persistence:** `SessionWriter.java` — add `writeBookmarks(Element, Document)` (model on 234–251) writing `<Bookmarks><Bookmark chromosome= start= end= description= color= highlighted= label=/></Bookmarks>`, called from `createXmlFromSession` after line 162. Add `SessionElement.BOOKMARK`/`BOOKMARKS` and `SessionAttribute.COLOR` (`SessionAttribute.COLOR` already exists at line 9) constants. `IGVSessionReader.java` — add `processBookmarks`/`processBookmark` to the dispatcher (293–330) and handlers modeled on 590–609. Bump `SessionWriter.CURRENT_VERSION` (68) to 9.
4. **UI — add/jump/remove:** extend `RegionNavigatorDialog` with a "Bookmark" checkbox column or a second table; add menu items in `IGVMenuBar.createRegionsMenu()` (742–784): "Add Bookmark", "Remove Bookmark". Keyboard shortcuts in `GlobalKeyDispatcher` (model on 214–241).
5. **UI — highlight:** add per-ROI highlight toggle (right-click "Highlight" item in `RegionOfInterestPanel.getPopupMenu`, 146–224; also in `RegionNavigatorDialog` row popup, 799–848). Store the highlighted ROI in `RegionOfInterestPanel.selectedRegion` (already exists, 66/231) **plus** a persistent flag on the ROI so it survives reload. Draw: `DataPanel.drawRegion` (315–335) — use per-ROI color instead of static `getForegroundColor()`; `RegionOfInterestPanel.drawRegionsOfInterest` (93–121) — per-ROI `backgroundColor` instead of static RED; the static colors at `RegionOfInterest.java:44–45` should become instance fields with defaults.
6. **Session restore of highlight:** in `IGVSessionReader.processRegion`/`processBookmark` (597–609) re-apply `RegionOfInterestPanel.setSelectedRegion` for the flagged bookmark.

---

## B) HIGH-QUALITY EXPORT (PNG/SVG/PDF, publication mode, selected tracks only)

### B.1 Existing image-export search results

| Symbol | Where | Role |
|---|---|---|
| `IGV.saveImage(Component, String)` / `saveImage(Component, String, String)` | `ui/IGV.java:573–583` | public entry (only `png`/`svg` accepted, line 578) |
| `IGV.createSnapshot(Component, File)` | `ui/IGV.java:585–609` | file chooser + wait cursor + error handling |
| `IGV.createSnapshotNonInteractive(Component, File, boolean batch)` | `ui/IGV.java:621–649` | extension → type; rejects EPS/JPEG (638–642); calls `SnapshotUtilities.doComponentSnapshot` (645) |
| `SnapshotUtilities.doComponentSnapshot(Component, File, ImageFileTypes.Type, boolean batch)` | `ui/util/SnapshotUtilities.java:90–119` | requires `Paintable` (94–96); SVG (103) / PNG (106) dispatch; error otherwise (111–115) |
| `exportScreenshotSVG(...)` (Batik) | `SnapshotUtilities.java:121–150` | Apache Batik `SVGGraphics2D` (132) → `paintImage` → stream (142) |
| `exportScreenShotBufferedImage(...)` | `SnapshotUtilities.java:163–185` | `BufferedImage TYPE_INT_ARGB` (166), white bg (170–173), `ImageIO.write(image, format, file)` (180) |
| `paintImage(Paintable, Graphics2D, int, int, boolean)` | `SnapshotUtilities.java:188–191` | `target.paintOffscreen(g, rect, batch)` — **the single paint contract** |
| `ImageFileTypes.Type` | `ui/util/ImageFileTypes.java:37–60` | `NULL`, `EPS(.eps)`, `PDF(.pdf)`, `SVG(.svg)`, `PNG(.png)`, `JPEG(.jpeg)`; `getImageFileType(String)` (62–79) |
| `Paintable` interface | `ui/panel/Paintable.java:35–37` | `void paintOffscreen(Graphics2D g, Rectangle rect, boolean batch)` |
| `SVGGraphics` (hand-rolled Graphics2D) | `ui/svg/SVGGraphics.java:49`; demo `ui/svg/SVGTest.java:38` (main at 58) | legacy SVG writer (not used by snapshot path; Batik is) |
| `com.itextpdf` / `java.awt.print` | **not found** anywhere in `src/main/java` | no PDF implementation exists |

### B.2 How IGV-X currently saves the view — call chain and options

**Menu entry points** (`ui/IGVMenuBar.java`):
- File menu (lines 370–392): "Save PNG Image ..." (371) → `igv.saveImage(igv.getMainPanel(), "png")` (374); "Save SVG Image ..." (383) → `igv.saveImage(igv.getMainPanel(), "svg")` (386). **`getMainPanel()` = data area only** (headers + track panels, via `MainPanel.paintOffscreen`).
- Tools menu (lines 894–917): "Save PNG Screenshot ..." (896) → `igv.saveImage(igv.getContentPane(), "png")` (900); "Save SVG Screenshot ..." (908) → `(igv.getContentPane(), "svg")` (912). **`getContentPane()` = whole window.**
- Track right-click (`ui/panel/TrackPanelComponent.java:221–230` + `saveImage(String)` at 283–284): `IGV.getInstance().saveImage(getTrackPanel().getScrollPane(), "igv_panel", extension)` → **single track panel only**.
- Tooltips: `ui/UIConstants.java:56–57` (`SAVE_PNG_IMAGE_TOOLTIP`, `SAVE_SVG_IMAGE_TOOLTIP`).

**CLI/batch entry** — `batch/CommandExecutor.java`: `snapshot` command (131–132 → `createSnapshot(String, String)` at 1134+), `snapshotdirectory` (129–130 → 946+), `SnapshotUtilities.setMaxPanelHeight(h)` (603).

**Options that exist today:**
- **None for the user.** No DPI, no scale factor, no "selected tracks only", no publication mode. The only knobs are internal: `batch` flag (used by `getSnapshotHeight(batch)`), static `SnapshotUtilities.maxPanelHeight` (69, default `DEFAULT_MAX_PANEL_HEIGHT = 1000`, line 64), and screen HiDPI scaling applied at startup (`Globals.DESIGN_DPI = 96`, `Globals.java:41`; `Main.java:314`; `prefs/PreferencesManager.java:73`).
- `IGV.selectSnapshotFile(File)` (651+) remembers `PreferencesManager ... getLastSnapshotDirectory()`.

**Whole-window vs data-panel:**
- `MainPanel.paintOffscreen(Graphics2D, Rectangle, boolean)` (`ui/panel/MainPanel.java:507–560`): paints application header (513–516), then iterates `centerSplitPane` components sorted by Y (522–524); skips empty track panels (536); height per panel = `tsp.getSnapshotHeight(batch)` (540); clips and calls `tsp.paintOffscreen` (544–545). `getSnapshotHeight` at 569+.
- `TrackPanelScrollPane.paintOffscreen` (`ui/panel/TrackPanelScrollPane.java:129–138`): translates by viewport scroll Y, delegates to `trackPanel.paintOffscreen` (136). `getSnapshotHeight(boolean)` (140–161): batch mode → `min(maxPanelHeight, max(scrollPaneHeight, preferredPanelHeight))` (147–157); interactive → visible `getHeight()` (159).
- `TrackPanel extends IGVPanel` (`ui/panel/TrackPanel.java:47`); `IGVPanel.paintOffscreen` (`ui/panel/IGVPanel.java:92–110`) iterates children and calls `paintOffscreen` on every `Paintable` child (99–106).
- `DataPanelContainer.paintOffscreen` (`ui/panel/DataPanelContainer.java:132–143`) → per-`DataPanel.paintOffscreen` (143).
- `DataPanel.paintOffscreen` (`ui/panel/DataPanel.java:251–278`): builds `RenderContext` (258), collects `TrackGroup`s from parent (259), `painter.paint(groups, context, background, contentRect)` (267), then `drawAllRegions(g)` (268) — ROI bars painted *after* tracks.

### B.3 Where "selected tracks only" filtering plugs in

**The track iteration that paints everything** is `DataPanelPainter.paintFrame(Collection<TrackGroup>, RenderContext)` (`ui/panel/DataPanelPainter.java:132–190`):
- Line 158: `List<Track> trackList = group.getVisibleTracks();`
- Line 160: `for (Track track : trackList)` — this is the loop to filter.
- Line 175: `if (track.isVisible())` — existing visibility gate (the analogous gate for a selected-only export).
- Line 177: `draw(track, rect, dContext)` — actual track painting.

Related plumbing for a correct export:
- `IGV.getSelectedTracks()` (`ui/IGV.java:1638–1647`, uses `Track.isSelected()`), `IGV.toggleTrackSelections(Iterable<Track>)` (1632–1636), `Track.setSelected(boolean)` / `Track.isSelected()` (`track/Track.java:218/220`).
- **Height must shrink too:** `TrackPanel.getTracks()` (`ui/panel/TrackPanel.java:168–173`), `TrackPanel.getPreferredPanelHeight()` and `TrackPanelScrollPane.getSnapshotHeight` (140–161), `MainPanel.paintOffscreen` (540). If you only filter painting, the exported image will contain empty space where hidden tracks were.
- **Filter must reach the painter:** `SnapshotUtilities.doComponentSnapshot` (90) → `paintOffscreen(g, rect, batch)`. Cleanest option: a static/thread-local export-options holder on `SnapshotUtilities` (pattern already established by `snapshotInProgress` at line 83 and `maxPanelHeight` at 69), or extend `Paintable.paintOffscreen` with an options param (touches ~15 implementors: `MainPanel`, `TrackPanelScrollPane`, `IGVPanel`, `DataPanelContainer`, `DataPanel`, `HeaderPanel`, `HeaderPanelContainer`, `TrackNamePanel`, `NameHeaderPanel`, `AttributePanel`, `AttributeHeaderPanel`). The static-holder route is far less invasive.

### B.4 Implementation plan — high-quality export

1. **New options type** `src/main/java/org/broad/igv/ui/util/SnapshotOptions.java` (new file): fields `boolean selectedTracksOnly`, `int dpi` (default 96 = `Globals.DESIGN_DPI`), `double scale`, `boolean publicationMode`, `ImageFileTypes.Type format`; plus a static holder on `SnapshotUtilities` (`getSnapshotOptions()/setSnapshotOptions(...)`, thread-local or guarded like `maxPanelHeight`).
2. **PNG at higher DPI:** in `SnapshotUtilities.exportScreenShotBufferedImage` (163–185) compute `imageWidth = (int)(width * dpi / 96.0)` (and height) and `g.scale(scaleX, scaleY)` before `paintImage` (175). Currently `doComponentSnapshot` uses `component.getWidth()` and `paintable.getSnapshotHeight(batch)` directly (99–100) — multiply there when options.dpi/scale > 1.
3. **PDF:** add a branch in `SnapshotUtilities.doComponentSnapshot` (mirroring the SVG branch at 103–105) and stop rejecting PDF in `IGV.createSnapshotNonInteractive` (638–642). Two viable routes:
   - (a) **Batik again**: `SVGGraphics2D` output can be wrapped/transcoded; simplest robust route is `org.apache.batik.transcoder.TranscoderInput/PDFTranscoder` (batik-transcoder jar) converting the SVG document to PDF — reuse `exportScreenshotSVG` logic.
   - (b) **java.awt.print**: paint into a `PrinterJob`-style `Printable` (paint `paintOffscreen` into `Graphics2D` obtained from `PrinterJob`/`PDFPrinterJob`), or add `com.itextpdf` dependency. Verify which is already on the classpath (check `pom.xml`/`build.gradle` for `batik`, `itextpdf`) before choosing.
   - Also update the dead code: `ImageFileTypes.PDF` description typo ("FormatFles", `ImageFileTypes.java:41`).
4. **Selected tracks only:**
   - `DataPanelPainter.paintFrame` (132–190): before the `for (Track track : trackList)` loop (160), if `SnapshotUtilities.getSnapshotOptions().selectedTracksOnly` is set, replace `trackList` with `trackList.stream().filter(Track::isSelected)` (and skip the group-gap/border painting at 145–156 for groups with zero selected tracks).
   - Height: in `TrackPanel.getPreferredPanelHeight()`-consuming paths (`TrackPanelScrollPane.getSnapshotHeight` 140–161, `MainPanel.paintOffscreen` 540) sum only selected tracks' heights when the option is on; otherwise the image is too tall. Add a helper e.g. `TrackPanel.getPreferredPanelHeight(boolean selectedOnly)`.
   - Thread the option from `IGV.saveImage`/`createSnapshotNonInteractive` (621) → `doComponentSnapshot` (90) via the static holder; menu action in `IGVMenuBar` File menu (after line 392): "Save Selected Tracks Image ..." toggling `SnapshotOptions.selectedTracksOnly = true`.
5. **Publication mode:** bundle into `SnapshotOptions`: white background already applied (170–173); add `publicationMode` to suppress ROI bars (`DataPanel.drawAllRegions`, 289–313), group-gap grey fill (`DataPanelPainter` 147–149), and light-grey border (`DataPanel.paintOffscreen` 270), and to force `batch=true` (full panel height, `TrackPanelScrollPane.getSnapshotHeight` 147–157).
6. **Menu/UI:** extend `IGVMenuBar` File menu (370–392) and Tools menu (894–917); optionally a `SaveImageDialog` for dpi/scale/scope options (new class under `ui/`, model on `CheckListDialog` used by `IGV.doSelectDisplayableAttribute`, `ui/IGV.java:559–570`).

---

## C) Existing tests to extend / add

| File (under `src/test/java/`) | What to add |
|---|---|
| `org/broad/igv/ui/panel/RegionOfInterestToolTest.java` (extends `AbstractHeadlessTest`) | Bookmark creation/normalization tests (mirror `testCreateRegion_*`, lines 16–51) |
| new `org/broad/igv/session/SessionWriterTest.java` (none exists today) | Round-trip: `SessionWriter.createXmlFromSession` → `IGVSessionReader` with bookmarks; assert `<Bookmarks>` element + attributes; assert ROI still round-trips |
| new `org/broad/igv/ui/util/SnapshotUtilitiesTest.java` (none exists) | `doComponentSnapshot` PNG/SVG to temp file; size == `getSnapshotHeight(batch)`; dpi scaling math; selected-only filter via a fake `Paintable` |
| `org/broad/igv/batch/CommandExecutorTest.java` | `snapshot` command with new options (CLI parity) |
| `org/broad/igv/AbstractHeadlessTest.java` | existing headless harness — extend if `RegionOfInterestPanel.setSelectedRegion` restore needs IGV bootstrapping |

**Key files to modify (Feature A):** `feature/RegionOfInterest.java`, `session/Session.java`, `session/SessionWriter.java`, `session/IGVSessionReader.java`, `session/SessionElement.java`, `session/SessionAttribute.java`, `ui/panel/RegionNavigatorDialog.java`, `ui/panel/RegionOfInterestPanel.java`, `ui/panel/RegionMenu.java`, `ui/panel/DataPanel.java`, `ui/IGVMenuBar.java`, `ui/GlobalKeyDispatcher.java`.
**Key files to create (Feature A):** `feature/Bookmark.java`.

**Key files to modify (Feature B):** `ui/IGV.java`, `ui/IGVMenuBar.java`, `ui/util/SnapshotUtilities.java`, `ui/util/ImageFileTypes.java`, `ui/panel/DataPanelPainter.java`, `ui/panel/DataPanel.java`, `ui/panel/TrackPanel.java`, `ui/panel/TrackPanelScrollPane.java`, `ui/panel/MainPanel.java`, `batch/CommandExecutor.java`, build file (for PDF dependency if needed).
**Key files to create (Feature B):** `ui/util/SnapshotOptions.java`, optional `ui/SaveImageDialog.java`, `session/…` none.

---

## D) Line-reference index (quick map)

- ROI model: `feature/RegionOfInterest.java:38–135`
- ROI storage: `session/Session.java:90,108–109,335–395,528`
- ROI add path: `ui/IGV.java:326–353`
- ROI drag tool: `ui/panel/RegionOfInterestTool.java:48,56,79–85,146–148,199–202`; toolbar `ui/commandbar/IGVCommandBar.java:247`; keys `ui/GlobalKeyDispatcher.java:214–241`
- ROI dialog: `ui/panel/RegionNavigatorDialog.java:64,93–119,144–169,245–271,322–354,414–437,678–777,799–848`; opener `ui/action/NavigateRegionsMenuAction.java:62`; menu `ui/IGVMenuBar.java:742–784`
- ROI drawing: `ui/panel/RegionOfInterestPanel.java:79–121,146–224,227–233`; `ui/panel/DataPanel.java:289–335`; `ui/panel/HeaderPanel.java:215–232`; pref `prefs/Constants.java:84`
- Session write: `session/SessionWriter.java:68,79–185,234–251,358–398`; elements/attrs `session/SessionElement.java:29–32`, `session/SessionAttribute.java:9–13,34,36,58`
- Session read: `session/IGVSessionReader.java:84,134–184,194–226,293–330,590–609,1047`; selection `ui/IGV.java:1025–1034,1055–1068`
- Export chain: `ui/IGV.java:573–649`; `ui/util/SnapshotUtilities.java:64–119,121–191,205–238`; `ui/util/ImageFileTypes.java:37–79`; `ui/panel/Paintable.java:35–37`
- Export menus: `ui/IGVMenuBar.java:370–392,894–917`; `ui/panel/TrackPanelComponent.java:221–230,283–284`; `ui/UIConstants.java:56–57`; CLI `batch/CommandExecutor.java:129–132,603,946–962,1134–1161`
- Paint hierarchy: `ui/panel/MainPanel.java:507–560`; `ui/panel/TrackPanelScrollPane.java:129–161`; `ui/panel/IGVPanel.java:92–110`; `ui/panel/DataPanelContainer.java:132–143`; `ui/panel/DataPanel.java:251–278`; `ui/panel/DataPanelPainter.java:53–114,132–190`
- Track selection: `track/Track.java:218,220`; `ui/IGV.java:1632–1647`
- DPI: `Globals.java:41`; `ui/Main.java:314`; `prefs/PreferencesManager.java:73`
