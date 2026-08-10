# IGV-X "Recent Files History" — Codebase Exploration Report

**Date:** 2026-08-10  ·  **Scope:** `src/main/java/org/broad/igv/`

## Headline conclusion (read this first)

IGV-X **already ships a working recent-files implementation**. There is a **"Recent Files" menu** (data files + URLs, backed by `RecentUrlsSet`) and a **"recent sessions" submenu** at the bottom of the File menu (backed by `RecentFileSet`). Both are persisted in `~/igvx/prefs.properties` under keys `IGV.Session.recent.urls` and `IGV.Session.recent.sessions` (10 entries max), saved on exit via `IGV.saveStateForExit()`.

A new "recent files history" feature should **extend/reuse** the existing machinery (`StackSet`, `RecentFileSet`, `RecentUrlsSet`, `RecentUrlsMenu`, `IGV.addToRecentUrls`) rather than re-implement it. Concrete gaps a new feature could fill:

1. **No startup/welcome panel exists** — there is no `showInitialDialog`/welcome dialog anywhere; the main frame is shown directly by `IGV.StartupRunnable`. A clickable recent-files list at startup is greenfield UI.
2. **Not every local-file load path records history.** Only menu-based loads (`LoadFilesMenuAction`, `LoadFromURLMenuAction`) call `IGV.addToRecentUrls(...)`. Drag-and-drop (`DataPanelContainer.FileDropTargetListener`), command-line (`StartupRunnable`), and batch (`CommandExecutor`) loads **do not** record recent files.
3. **Sessions are kept in a separate list** (`RecentFileSet`) and the Recent Files menu only holds `ResourceLocator`s; `RecentUrlsMenu` would load a session as tracks if one were ever placed in it (sessions are currently filtered out by `LoadFilesMenuAction`).
4. **No per-entry genome association** is stored (entries are bare path or path+index strings).

All paths below are relative to `/Users/runtianwu/Rdirectory/IGV-X/src/main/java/`. Line numbers were verified against the current tree.

---

## 1. File-menu construction and every action that opens a LOCAL file

### 1.1 Menu bar construction

- `org/broad/igv/ui/IGVMenuBar.java`
  - `private JMenuItem recentFilesMenu;` — field, **line 118**.
  - `static IGVMenuBar createInstance(IGV igv)` — **line 121** (called from the `IGV` constructor, see below).
  - `private IGVMenuBar(IGV igv)` — **line 136**; iterates `createMenus()` (line 141).
  - `private List<AbstractButton> createMenus()` — **line 171**; `menus.add(createFileMenu());` at **line 175**.
  - `JMenu createFileMenu()` — **line 276** (this is the File menu).
  - `public void showRecentFilesMenu()` — **line 1204** (`this.recentFilesMenu.setVisible(true);`).
  - `public void enableReloadSession()` / `disableReloadSession()` — lines 1200 / 1208.
  - `final public void doExitApplication()` — **line 1162**; calls `igv.saveStateForExit()` (line 1165) then exits (line 1174).

- `org/broad/igv/ui/IGV.java` — the menu bar is created in the constructor:
  - `menuBar = IGVMenuBar.createInstance(this);` — **line 230**; installed on the root pane at line 233.

### 1.2 File menu item layout (`IGVMenuBar.createFileMenu`, lines 276–424)

| Menu item | Construction line | Action class / behavior |
|---|---|---|
| Load from File… | 287 | `new LoadFilesMenuAction("Load from File...", KeyEvent.VK_L, igv)` |
| Load from URL… | 291 | `new LoadFromURLMenuAction(LoadFromURLMenuAction.LOAD_FROM_URL, KeyEvent.VK_U, igv)` |
| Load From Server… | 295 | `LoadFromServerAction` |
| **Recent Files** | 300 | `recentFilesMenu = new RecentUrlsMenu();` (field, line 118) |
| Load from Database… | 303–306 | `LoadFromDatabaseAction` (conditional on `DB_ENABLED`) |
| ENCODE items | 309–334 | `BrowseEncodeAction` |
| Reload Tracks | 337–339 | `ReloadTracksMenuAction` |
| New Session… | 344–346 | `NewSessionMenuAction` |
| Open Session… | 348–350 | `OpenSessionMenuAction` |
| Save Session… | 352–355 | `SaveSessionMenuAction` |
| Reload Session | 357–361 | `ReloadSessionMenuAction` (disabled until a session is loaded) |
| Autosave menu | 363–364 | `AutosaveMenu` (`org/broad/igv/ui/util/AutosaveMenu.java`) |
| Save PNG / SVG, Exit | 370–406 | inline `MenuAction`s |
| Recent-sessions submenu | 407–421 | `JSeparator recentSessionsSep` (line 407, initially invisible) + `DynamicMenuItemsAdjustmentListener` (line 416) that injects one `OpenSessionMenuAction(session, igv)` item per recent session (line 420) |

### 1.3 Action classes / handlers that open a LOCAL file from disk

1. **Load from File — `org/broad/igv/ui/action/LoadFilesMenuAction.java`**
   - `actionPerformed(ActionEvent)` — **line 61** → `loadFiles(chooseTrackFiles())` (line 62).
   - `chooseTrackFiles()` — **line 65**: uses `PreferencesManager.getPreferences().getLastTrackDirectory()` (line 67) and `FileDialogUtils.chooseMultiple(...)` (line 73); remembers last directory (line 79).
   - `loadFiles(File[])` — **line 86**: rejects session files (`SessionReader.isSessionFile(path)` → message, lines 98–107); for valid files builds locators via `ResourceLocator.getLocators(validFiles)` (line 122) then **`igv.addToRecentUrls(locators); igv.loadTracks(locators);`** at **lines 123–124**. ← the current “record recent file” hook.

2. **Open Session — `org/broad/igv/ui/action/OpenSessionMenuAction.java`**
   - `actionPerformed(ActionEvent)` — **line 76**; `LongRunningTask.submit(() -> this.igv.loadSession(sessionFile, null));` at **line 82**.
   - `pickSessionFile()` — **line 86** (file chooser, remembers directory at line 95).
   - Constructor `OpenSessionMenuAction(String sessionFile, IGV igv)` — **line 63** — is the “load recent session” variant (`autoload = true`, line 67); this is how the recent-sessions submenu items are wired.

3. **Load from URL (also accepts local paths) — `org/broad/igv/ui/action/LoadFromURLMenuAction.java`**
   - `actionPerformed` — **line 71**; `loadUrls(...)` at line 83.
   - `loadUrls(List<String>, List<String>, boolean)` — **line 97**: single hub URL → `GenomeManager.loadGenome` (line 102); single session URL → `igv.loadSession(url, null)` (line 114); otherwise builds locators and calls **`igv.addToRecentUrls(locators); igv.loadTracks(locators);`** at **lines 124–125**.

4. **Drag-and-drop of files onto the data panel — `org/broad/igv/ui/panel/DataPanelContainer.java`**
   - Inner class `FileDropTargetListener implements DropTargetListener` — **line 175**; registered via `setDropTarget(target)` at line 68.
   - `drop(DropTargetDropEvent)` — **line 205**: accepts `DataFlavor.javaFileListFlavor` (lines 218–229), builds `ResourceLocator.getLocators(files)` (line 221) and calls **`IGV.getInstance().load(locator, panel)`** at **line 224**. ⚠️ Does **not** call `addToRecentUrls` — a gap for history recording.
   - (The `org/broad/igv/ui/panel/DragAndDropTransferHandler.java` class is only for **rearranging track headers** — `MOVE` action, line 77 — not file drops.)

5. **Command-line / startup — `org/broad/igv/ui/IGV.java`, inner class `StartupRunnable`** (see §2.2).

6. **Batch/command port — `org/broad/igv/batch/CommandExecutor.java`**
   - session file: `igv.loadSession(f, locus);` — **line 778**; data files: `igv.loadTracks(fileLocators);` — **line 816**. Neither records recent files.

7. **Reload actions (operate on the current session path, not user-selected files):**
   - `org/broad/igv/ui/action/ReloadTracksMenuAction.java` — `actionPerformed` **line 69**: serializes current session and calls `igv.loadSessionFromStream(currentSessionFilePath, inputStream)` (line 81).
   - `org/broad/igv/ui/action/ReloadSessionMenuAction.java` — `actionPerformed` **line 64**: `LongRunningTask.submit(() -> this.igv.loadSession(currentSessionFilePath, null));` at **line 67**.

8. **Other loaders (NOT local-file openers, listed for completeness):** `LoadFromServerAction` (line 94 → `mainFrame.loadTracks(locators)`), `LoadFromDatabaseAction` (line 110), `BrowseEncodeAction` (line 143), `SelectGenomeAnnotationTracksAction` (line 111), `S3LoadDialog` (`org/broad/igv/aws/S3LoadDialog.java` line 126), `UCSCGenArkAction`. These all funnel into `IGV.loadTracks`.

---

## 2. Main load entry points (signatures + callers)

All in `org/broad/igv/ui/IGV.java` unless noted.

| Method | Line | Signature | Notes / callers |
|---|---|---|---|
| `loadTracks` | 372 | `public Future loadTracks(final Collection<ResourceLocator> locators)` | Submits a `NamedRunnable` (“Load Tracks”, line 390) that calls `loadResources(locators)` (line 383). Callers: `LoadFilesMenuAction` 124, `LoadFromURLMenuAction` 125, `RecentUrlsMenu` 56, `BrowseEncodeAction` 143, `LoadFromServerAction` 94, `LoadFromDatabaseAction` 110, `SelectGenomeAnnotationTracksAction` 111, `S3LoadDialog` 126, `CommandExecutor` 816, `StartupRunnable` 2094. **There is no `loadTracks(File, String)` overload — wrap the path in `new ResourceLocator(path)`.** |
| `loadResources` | 1185 | `public void loadResources(Collection<ResourceLocator> locators)` | Event-thread; existence check for local files (1192–1198), `load(locator)` (1202), `addTracks(tracks)` (1203), error messages (1205–1208). |
| `load` | 1268 | `public List<Track> load(ResourceLocator locator) throws DataLoadException` | Uses `new TrackLoader()` + `GenomeManager.getInstance().getCurrentGenome()` (1275–1277); sets track attributes (1288–1290). |
| `load` | 1311 | `public void load(final ResourceLocator locator, final TrackPanel panel) throws DataLoadException` | **DnD entry**: if `SessionReader.isSessionFile(locator.getPath())` → `loadSession(locator.getPath(), null)` (1314–1315); else load into panel (1318–1323). |
| `addTracks` | 1239 | `public void addTracks(List<Track> tracks)` | Chooses panel via `getPanelFor(representativeTrack)` (1247); special VCF panel handling (1248–1255). |
| `loadSession` | 966 | `public boolean loadSession(String sessionPath, String locus)` | Opens stream (972), delegates to `loadSessionFromStream` (973); on success: `session.setPath` (977), `goToLocus` (981), adds to recent sessions `getRecentSessionList().add(sessionPath)` (**986**), `menuBar.enableReloadSession()` (987); on failure removes from recent list (999). Callers: `OpenSessionMenuAction` 82, `LoadFromURLMenuAction` 114, `ReloadSessionMenuAction` 67, `CommandExecutor` 778, `StartupRunnable` 2011/2015/2100, `load(ResourceLocator, TrackPanel)` 1315. |
| `loadSessionFromStream` | 1025 | `public boolean loadSessionFromStream(String sessionPath, InputStream inputStream) throws IOException` | Picks reader by extension: `.session`/`.session.txt` → `UCSCSessionReader` (1028–1029), `.idxsession*` → `IndexAwareSessionReader` (1030–1031), else `IGVSessionReader` (1033). |
| `saveSession` | 1055 | `public void saveSession(File targetFile) throws IOException` | `SessionWriter.saveSession` (1056); adds to recent sessions (1062); `enableReloadSession()` (1063); `setLastTrackDirectory` (1066). |
| `resetSession` | 930 | `public void resetSession(String sessionPath)` | Clears tracks/attributes; `menuBar.resetSessionActions()` (935). |
| `startUp` | 1874 | `public Future startUp(Main.IGVArgs igvArgs)` | Submits `StartupRunnable` (1880). |

### 2.2 Launch sequence (no welcome panel)

- `org/broad/igv/ui/Main.java` — `public static void open(Frame frame, Main.IGVArgs igvArgs)` — **line 254**:
  - `IGV igv = IGV.createInstance(frame, igvArgs);` — **line 296** (constructor builds content pane + menu bar; `IGV.java` line 229–233).
  - `igv.startUp(igvArgs);` — **line 298**.
- `IGV.java` — `public class StartupRunnable implements Runnable` — **line 1916**; `run()` — **line 1925**: starts command server (1931), **shows the main frame** (1933–1939: `mainFrame.setVisible(true)`), loads default genome (1978–1991), then handles CLI `--session` / data-file args (1993–2116): session via `loadSession` (2011 remote / 2015 local), data files via `loadTracks(locators)` (**line 2094**), autosave via `loadSession(autosavePath, null)` (2100).

---

## 3. Preferences persistence (PrefsManager)

### 3.1 Classes and file path

- `org/broad/igv/prefs/PreferencesManager.java` (note: the class is **`PreferencesManager`**, and the preferences object is **`IGVPreferences`** — there is no `PrefsManager`):
  - `public static IGVPreferences getPreferences()` — **line 137** (NULL category; `getPreferences(String category)` at line 49).
  - `private static String prefFile;` — **line 32**; `public static void setPrefsFile(String prefsFile)` — **line 145**; `loadUserPreferences()` — **line 149** (sets `prefFile = DirectoryManager.getPreferencesFile().getAbsolutePath()` at line 153); `load(String prefFileName)` — **line 189** (parses `key=value`, `##category` sections, lines 197–211).
  - `private synchronized void storePreferences()` — **line 355** — writes `prefFile` (FileWriter line 359). Triggered by `receiveEvent(PreferencesChangeEvent)` — **line 387**.
- `org/broad/igv/DirectoryManager.java`:
  - `public static synchronized File getIgvDirectory()` — **line 92**; **IGV-X change already applied**: falls back to `new File(rootDir, "igvx")` — **line 111** (never `~/igv`).
  - `public static synchronized File getPreferencesFile()` — **line 289** → `new File(igvDirectoy, "prefs.properties")` — **line 292**. ⇒ **prefs file = `~/igvx/prefs.properties`** (legacy file copied in if present, lines 294–305; created if missing, 307–313). So yes — the `~/.igvx` change is already in effect for preferences.
  - `getAutosaveDirectory()` — line 184 (used by session autosave).

### 3.2 `IGVPreferences` API (`org/broad/igv/prefs/IGVPreferences.java`)

| Method | Line | Notes |
|---|---|---|
| `public String get(String key)` | 93 | falls back through parent + defaults |
| `public String get(String key, String defaultValue)` | 118 | user map only |
| `public void put(String key, String value)` | 273 | **ignored in batch mode** (line 275); posts `PreferencesChangeEvent` (line 287) ⇒ auto-persists via `PreferencesManager.storePreferences()`; blank value removes key |
| `public void put(String key, boolean b)` | 291 | |
| `public void putAll(Map<String,String>)` | 296 | posts event at line 312 |
| `public void remove(String key)` | 424 | |
| `getAsBoolean/getAsInt/getAsFloat/...` | typed getters elsewhere in file (`getAsBoolean` used e.g. IGV.java 533) | |
| `setRecentSessions(String)` | 614 | `remove(RECENT_SESSIONS); put(RECENT_SESSIONS, recentSessions);` |
| `getRecentSessions()` | 620 | `RecentFileSet.fromString(get(RECENT_SESSIONS, null), UIConstants.NUMBER_OF_RECENT_SESSIONS_TO_LIST)` |
| `setRecentUrls(String)` | 625 | symmetric to sessions |
| `getRecentUrls()` | 631 | `RecentUrlsSet.fromString(..., UIConstants.NUMBER_OF_RECENT_SESSIONS_TO_LIST)` |
| `setLastTrackDirectory(File)` / `getLastTrackDirectory()` | 687 / 692 | last-open directory used by choosers |

- Keys in `org/broad/igv/prefs/Constants.java`: `RECENT_SESSIONS = "IGV.Session.recent.sessions"` — **line 44**; `RECENT_URLS = "IGV.Session.recent.urls"` — **line 45**. (`Constants` also has IGV-X keys, e.g. `SWIPE_PAN_ENABLED` line 87.)
- Max list size: `org/broad/igv/ui/UIConstants.java` — `NUMBER_OF_RECENT_SESSIONS_TO_LIST = 10` — **line 97** (shared by both lists).

### 3.3 Existing string-list helpers (reuse these — do not duplicate)

- `org/broad/igv/ui/StackSet.java` — `public class StackSet<T> extends AbstractCollection<T> implements Set<T>, SequencedCollection<T>` — **line 15**. LRU stack semantics: `add` moves to front / evicts oldest (lines 63–71), `addAll` (81–86), `remove` (92), `clear` (100), `getMaxSize()` (123).
- `org/broad/igv/ui/RecentFileSet.java` — `extends StackSet<String>` — **line 13**; serialization `asString()` joins with `";"` (**line 25**) and `static RecentFileSet fromString(String, int maxSize)` (**line 29**) filters blanks/`"null"` and strips.
- `org/broad/igv/ui/RecentUrlsSet.java` — `extends StackSet<ResourceLocator>` — **line 20**; `asString()` (**line 32**) joins `path [index: indexPath]` with `"|"`; `fromString(String, int)` (**line 61**) parses back incl. index path.

---

## 4. Startup / welcome panel

- **There is no startup panel, welcome dialog, or `showInitialDialog`** — a repository-wide grep for `showInitialDialog|Welcome|InitialDialog` returns nothing, and `IGVMainFrame` is only a legacy shim (`org/broad/igv/ui/IGVMainFrame.java` line 34, forwards to `Main.main`).
- The only dialog class that looks startup-related, `org/broad/igv/ui/VersionUpdateDialog.java` (`public VersionUpdateDialog(String versionString)` — line 52), is **never instantiated** anywhere in `src/` (dead code) — there is currently no hook dialog at launch.
- Launch flow (see §2.2): `Main.open` (line 254) → `IGV.createInstance` (line 296; builds `IGVContentPane` + `IGVMenuBar`) → `IGV.startUp` (line 1874) → `StartupRunnable.run` (line 1925) → `mainFrame.setVisible(true)` (line 1938) → genome/session/data load (1941–2116).
- Main window layout — `org/broad/igv/ui/IGVContentPane.java`, constructor **line 57**: `BorderLayout` with `commandBarPanel` NORTH (**line 71**, contains `IGVCommandBar` line 73), `MainPanel` CENTER (**line 80**), `ApplicationStatusBar` SOUTH (**line 83**).
  - **Where a recent-files clickable list fits:** inside `MainPanel` (`org/broad/igv/ui/panel/MainPanel.java`) as an empty-state overlay in the CENTER region when no tracks are loaded, or as a modal `IGVDialog` shown from `StartupRunnable.run` after line 1938 (before/after genome load). `IGV.getGenomeManager()` does **not** exist in this codebase — the genome manager is the singleton `org.broad.igv.feature.genome.GenomeManager.getInstance()` (used e.g. IGV.java lines 948–953, 1276).

---

## 5. Existing recent-files/sessions support (so we don't duplicate)

| Piece | File / class | Lines | Behavior |
|---|---|---|---|
| “Recent Files” menu | `org/broad/igv/ui/util/RecentUrlsMenu.java` — `public class RecentUrlsMenu extends JMenu` | class line 14; menu listener 23–47; item builder `createMenuItem` 50–69 | On File-menu open: reads `IGV.getInstance().getRecentUrls()`; hides itself when empty (line 26), otherwise rebuilds items (30–35), adds **“Clear Recent Files”** action (38–44, calls `getRecentUrls().clear()`). Clicking an item (56–57): `igv.loadTracks(resource); igv.addToRecentUrls(resource);`. **Note: does not special-case session files — a session in this list would be loaded as tracks.** |
| Recent-sessions submenu | `org/broad/igv/ui/IGVMenuBar.java` createFileMenu | 407–421 | `DynamicMenuItemsAdjustmentListener` (class: `org/broad/igv/ui/DynamicMenuItemsAdjustmentListener.java` line 22; ctor line 40: `(JMenu, JSeparator insertionPoint, Collection<T> values, Function<T, JMenuItem> itemConstructor)`) injects `OpenSessionMenuAction(session, igv)` items after the separator; `MenuSelectedListener extends MenuListener` (`org/broad/igv/ui/MenuSelectedListener.java` line 9). |
| In-memory lists + accessors | `org/broad/igv/ui/IGV.java` | fields 139–140; `getRecentSessionList()` **1122** (prunes non-existent files, 1126); `getRecentUrls()` **1131**; `addToRecentUrls(Collection<ResourceLocator>)` **1143** (also reveals the menu: `menuBar.showRecentFilesMenu()`, 1147) | |
| Persistence | `IGVPreferences` 614–634; `Constants` 44–45; `UIConstants` 97 | | String-serialized via `RecentFileSet`/`RecentUrlsSet`; written on `PreferencesChangeEvent` and explicitly in `saveStateForExit` |
| Save-on-exit | `IGV.java saveStateForExit()` | 515–527 | Persists both lists if non-empty (520, 526); also stops autosave timer and conditionally autosaves session (530–539). Called from `IGVMenuBar.doExitApplication` (1165) and shutdown paths. |
| Recording on open/save | `IGV.loadSession` 986 / on failure 999; `IGV.saveSession` 1062 | | Sessions recorded automatically; failed session loads are removed from history. |

**Gaps (candidate scope for the new feature):**
1. No startup surface (no welcome panel) — §4.
2. DnD (`DataPanelContainer` line 224), CLI (`StartupRunnable` line 2094), batch (`CommandExecutor` lines 778/816) do not record recent files.
3. Recent Files menu only handles track files; sessions live in a separate list and are excluded by `LoadFilesMenuAction` (lines 98–107).
4. No genome recorded per entry; no submenu-capacity differentiation (both lists share `NUMBER_OF_RECENT_SESSIONS_TO_LIST = 10`).
5. Persistence happens on change-event/exit; a crash mid-session can lose additions made since the last `put` — fine for a menu, worth noting for a startup panel.

---

## 6. Opening a file from a path string (exact calls)

**Data file (track file):**
```java
IGV igv = IGV.getInstance();
List<ResourceLocator> locators = List.of(new ResourceLocator(path)); // optional: .setIndexPath(...)
igv.addToRecentUrls(locators);   // record history (existing hook)
igv.loadTracks(locators);        // IGV.java:372; wraps loadResources -> load(ResourceLocator)
```
- `ResourceLocator(String path)` — `org/broad/igv/util/ResourceLocator.java`; `public static List<ResourceLocator> getLocators(Collection<File> files)` — **line 150** (used by `LoadFilesMenuAction` 122 and DnD 221).
- There is **no** `loadTracks(File, String)` / `loadTracks(File, Genome)` overload in `IGV` — always wrap in `ResourceLocator`. The current genome is taken implicitly from `GenomeManager.getInstance().getCurrentGenome()` inside `IGV.load` (line 1276).

**Session file (.xml / .session / .idxsession):**
```java
IGV.getInstance().loadSession(path, null);   // IGV.java:966 (background thread required)
// or loadSessionFromStream(path, inputStream)  IGV.java:1025
```
- Distinguish via `SessionReader.isSessionFile(String)` — `org/broad/igv/session/SessionReader.java` **line 37**: `f.endsWith(".xml") || f.endsWith(".php") || f.endsWith(".php3") || f.endsWith(".session")`. (Note: `.session.txt` variants are recognized by `loadSessionFromStream` at 1028–1031 but not by `isSessionFile`.)
- Existing routing precedents: `LoadFilesMenuAction` rejects sessions with a message (98–107); `LoadFromURLMenuAction` routes a single session URL to `loadSession` (109–117); `IGV.load(locator, panel)` routes sessions to `loadSession` (1313–1315); `StartupRunnable` routes CLI session arg to `loadSession` (2004–2029).

---

## 7. Top 3 integration points for implementing the feature

1. **`org/broad/igv/ui/IGV.java` — `addToRecentUrls(Collection<ResourceLocator>)` (line 1143) + `getRecentUrls()` (line 1131) + `getRecentSessionList()` (line 1122)** — the single central API for recording/reading history. Add recording calls at the un-covered load paths: `DataPanelContainer.FileDropTargetListener.drop` (line 224), `StartupRunnable.run` (line 2094), `CommandExecutor` (lines 778/816).
2. **`org/broad/igv/ui/util/RecentUrlsMenu.java` (whole file, class line 14)** — the existing “Recent Files” menu; extend `createMenuItem` (line 50) to dispatch sessions to `loadSession` vs tracks to `loadTracks`, and optionally show genome info; mirror its `JMenu` + `MenuSelectedListener` pattern for any new startup panel list.
3. **`org/broad/igv/prefs/IGVPreferences.java` (lines 614–634) + `org/broad/igv/prefs/Constants.java` (lines 44–45) + `org/broad/igv/ui/UIConstants.java` (line 97)** — reuse `StackSet`/`RecentFileSet`/`RecentUrlsSet` serialization and the `put()` → `PreferencesChangeEvent` → `storePreferences()` persistence flow (`PreferencesManager.java` 355/387) for any new history format (e.g. path+genome entries); file is `~/igvx/prefs.properties` (`DirectoryManager.getPreferencesFile()`, line 289/292).

---

## Appendix: key file map

| Concern | File |
|---|---|
| File menu | `org/broad/igv/ui/IGVMenuBar.java` (createFileMenu 276) |
| Main window | `org/broad/igv/ui/IGV.java`, `org/broad/igv/ui/IGVContentPane.java` (57) |
| Load actions | `org/broad/igv/ui/action/LoadFilesMenuAction.java`, `LoadFromURLMenuAction.java`, `OpenSessionMenuAction.java`, `ReloadTracksMenuAction.java`, `ReloadSessionMenuAction.java` |
| DnD | `org/broad/igv/ui/panel/DataPanelContainer.java` (FileDropTargetListener 175, drop 205) |
| Recent lists | `org/broad/igv/ui/StackSet.java`, `RecentFileSet.java`, `RecentUrlsSet.java`, `org/broad/igv/ui/util/RecentUrlsMenu.java`, `org/broad/igv/ui/DynamicMenuItemsAdjustmentListener.java` |
| Prefs | `org/broad/igv/prefs/PreferencesManager.java`, `IGVPreferences.java`, `Constants.java`, `org/broad/igv/DirectoryManager.java` (289) |
| Startup | `org/broad/igv/ui/Main.java` (254), `IGV.java` (startUp 1874, StartupRunnable 1916) |
| Session I/O | `org/broad/igv/session/SessionReader.java` (37), `SessionWriter`, `org/broad/igv/session/autosave/SessionAutosaveManager.java` |
