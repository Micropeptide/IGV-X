# IGV-X — Sessions

How IGV-X handles session files (`.xml` IGV sessions and the planned
`.igvx.json` companion), relative paths, and backward compatibility.

## 1. Current state (updated 2026-09-07)

IGV-X is built on upstream IGV 2.19.X and inherits its session format and
loading machinery. The charter's session work (relative paths by default,
`.igvx.json` companion) landed in commits c52d4cc85 / 74f79c9e1 / 166efd284
(2026-08-10 through 2026-08-15) and is **implemented** — sections 3-4 below
describe the shipped design, not a plan.

Verified end-to-end 2026-09-07 (real app build, real bigWig track, real
Finder interaction — not just a code read):
- A saved session's `<Resource path=...>` **and** `<Track id=...>` are both
  relative when the data file lives under the session's directory. (A
  latent bug had `Track/@id` silently staying absolute even though
  `Resource/@path` was correct — see CHANGELOG.md "Fixed", 2026-09-07.)
- Relative-path computation is robust to a symlinked directory in the path
  (e.g. an iCloud Drive-backed `~/Desktop`/`~/Documents`) on either side.
- Double-click and Finder right-click > Open With > IGV-X on a `.xml`
  session file both load it correctly in a locally-built app.
- Dropping a file anywhere on the main window (not just the track data
  area) loads it.

## 2. Design goals (charter)

1. **Relative session paths by default.** A session saved next to its data
   files should reference them by paths relative to the session file, so
   the whole folder is portable (copy/move/zip without breaking tracks).
2. **`.igvx.json` companion metadata, optional.** If the companion file is
   missing, the session still loads with standard IGV behavior. The
   companion may hold IGV-X-only state (bookmarks, highlights,
   multi-track selection, export settings, per-track UI state) that stock
   IGV sessions cannot represent, plus diagnostics metadata.
3. **Backward compatible with standard IGV sessions.** Any session a
   stock IGV can open, IGV-X can open; and IGV-X-written sessions remain
   openable by stock IGV when they contain only stock features.

## 3. Compatibility contract

- **Read:** accept all upstream 2.19.X session XML (absolute paths, URL
  paths, relative paths if base dir resolvable). Do not refuse a session
  because it references a missing file — surface a diagnostic (see
  diagnostics doc) and continue.
- **Write (planned):** by default emit relative paths (relative to the
  session file's directory); a preference may restore absolute-path
  emission for users who want it. Write the same XML structure stock IGV
  writes; never emit IGV-X-only elements inside the XML that stock IGV
  would choke on — put them in the `.igvx.json` companion instead.
- **Companion:** same basename as the session with `.igvx.json` suffix
  (e.g. `session.xml` ↔ `session.xml.igvx.json`), or `session.igvx.json`
  — final choice documented here at implementation time. Load is
  best-effort: malformed/missing companion ⇒ log + continue.

## 4. What the companion will hold (planned)

- Bookmarks and gene/region highlights (charter feature; persist across
  sessions).
- Track-level UI state: selected tracks, collapsed state, quantitative
  track ranges (min/max overrides), color overrides.
- Session-level preferences that are IGV-X-specific.
- Versioned schema (`"schemaVersion": 1`) for forward migration.

## 5. Current upstream session machinery (reference)

- `org.broad.igv.session.IGVSessionReader` / `IGVSessionWriter` — XML
  read/write of tracks, genome, views.
- `org.broad.igv.ui.util.SessionManager` — load/save entry points.
- `ResourceLocator` — how track URLs/paths are represented; relative
  resolution happens here today only in limited forms.
- The batch command listener (port 60151 style) accepts `load`/`new`/
  `genome` commands — preserved and improved as part of the diagnostics/
  automation work (see the diagnostics doc).

## 6. Implementation notes (for the implementer)

- Make path relativization a single utility (`SessionPathUtils`)
  used by both reader and writer, with an explicit "base dir = session
  file's parent".
- Keep absolute URLs (http/https) untouched; only filesystem paths are
  candidates for relativization.
- Loading a session must not require the companion; missing companion is
  the default for sessions written by stock IGV.
- Regression tests: save→load round-trip with relative paths moved to a
  new directory; load of a stock-IGV-authored session fixture; load with
  deliberately missing companion; session with a missing data file must
  not throw (diagnostic instead).
