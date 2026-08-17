# IGV-X — Release & Packaging

Reproducible macOS packaging for IGV-X: bundle, Apple Silicon, signing/
notarization where possible, DMG/ZIP, versioning, checksums.

## 1. Current state (2026-08-17)

The build is verified for development (`./gradlew compileJava test` with
vendored JDK 21 + Gradle 8.10.1). **The app bundle is built and installed**:
`/Applications/IGV-X.app` (v2.19.5-igvx, bundled JDK 21, ad-hoc codesigned,
launch verified). This document is the release contract for moving from
ad-hoc local install to signed/notarized distribution.

## 1.1 Quick local build + install (current flow)

```bash
# 1. Build the gradle mac-app dist (produces lib jars for jpackage):
./gradlew createMacAppWithJavaDistZip \
  -PjdkBundleMac=$PWD/tools/jdk-21.0.12+8 -Pversion=2.19.5-igvx
# 2. Build the app bundle with jpackage (native Mach-O launcher):
./scripts/package/build_app_jpackage.sh \
  build/IGV-MacApp-dist/IGV_2.19.5-igvx.app/Contents/Java/lib build/release
# 3. Ad-hoc codesign + install:
codesign --force --deep -s - build/release/IGV-X.app
cp -R build/release/IGV-X.app /Applications/
# 4. Verify launch (native launcher, Finder session-open works):
open /Applications/IGV-X.app
```

**Why jpackage**: the app uses a native Mach-O launcher
(`Contents/MacOS/IGV-X`) produced by `jpackage`, so the JVM registers with
LaunchServices as `org.igvx.IGVX` (not the JDK's bundle id). This is what
makes Finder double-click session-file opens work — macOS routes
open-file AppleEvents to the app by its registered bundle id, and the
native launcher ensures the identity matches. The old shell-script
launcher registered as `net.java.openjdk.jdk` and silently dropped files
(fixed 2026-08-15, commits 166efd284 + d9ab77001).

For a full release (tests + DMG + ZIP + checksums), use:
```bash
./scripts/package/build_release.sh -v 2.19.5-igvx.3
```


## 2. App identity (must not collide with stock IGV)

- App name: **IGV-X** (never "IGV").
- Bundle ID: `org.igvx.IGVX` (distinct from stock IGV's `org.broad.igv`).
- Data locations (charter): `~/igvx` or macOS Application Support under
  the IGV-X bundle ID; **separate** prefs, caches, logs, and session
  storage. Never touch `~/igv`, `~/.igv`, or stock IGV's state.
- `Preferences`, `IGVPreferences`, and session file dialogs must default
  to the IGV-X locations; verify no stock-IGV paths are read/written.

## 3. Packaging targets

| Target | Purpose |
|---|---|
| `.app` bundle | Development builds, direct run (`open IGV-X.app`) |
| `.dmg` | Distribution for most users |
| `.zip` | Alternate distribution (works with Gatekeeper quarantine + signing) |
| source tarball | For anyone building from source |

All built on **Apple Silicon (arm64)** primarily; x86_64 rosetta build
optional later if needed. The build must be reproducible from a clean
checkout with the vendored toolchain (`tools/jdk-21.0.12+8/`,
`GRADLE_USER_HOME=.gradle-home`).

## 4. Versioning

- IGV-X version = upstream base + IGV-X patch level, e.g.
  `2.19.5-igvx.1`, `2.19.5-igvx.2`. Keep upstream version visible so
  users know the base.
- Tags: `v2.19.5-igvx.1` etc. Release notes in `CHANGELOG.md`.
- Each release records: upstream commit merged, IGV-X commits since last
  release, build environment (macOS version, JDK, Gradle), checksums.

## 5. Signing & notarization (where possible)

- Ad-hoc signing (`codesign -s -`) for local/dev builds.
- Developer ID signing + notarization when Runtian provides a Developer
  ID certificate and Apple credentials; until then the release artifact
  is ad-hoc signed with clear documentation (users must right-click-open
  or `xattr -dr com.apple.quarantine`).
- Hardened runtime: `--options runtime` for notarized builds.
- **Implemented**: `scripts/package/build_release.sh` codifies the full
  pipeline — fresh gradle dist build (for lib jars), jpackage app-image
  build (native Mach-O launcher, `build_app_jpackage.sh`), Info.plist
  patching (bundle ID, document types, UTIs), ad-hoc or Developer ID
  codesign, UDZO DMG + ZIP, `SHA256SUMS`, and `version.txt` metadata,
  plus post-build verification (codesign verify, plist identity, native
  launcher Mach-O check, document types, `hdiutil verify`). See
  `scripts/package/README.md`. Notarization (`notarytool` + stapler)
  remains future work — keep secrets out of the repo (env vars / Keychain).

## 6. Checksums & release notes

Each release publishes SHA-256 checksums for every artifact
(`SHA256SUMS`), plus a short `RELEASE_NOTES` pointing at `CHANGELOG.md`
and `docs/whats-different.md`.

## 7. Release checklist (gate)

1. `./gradlew clean test` green (full hermetic suite).
2. Real-data verification run (`scripts/verify_test_files.py`) if
   applicable and files present.
3. Manual smoke: app launches, loads a known session, chromosome
   resolution works on the TAIR10 fixtures (chrC/chrM), no stock-IGV
   paths touched (verify via `lsof`/prefs inspection).
4. Bundle, sign, package, checksum, tag, write release notes.
5. Document in `docs/release-<version>.md`.

## 8. CI (future)

A GitHub Actions workflow (macos-14 arm64) can automate: checkout →
vendored JDK → `./gradlew test` → package → checksums → attach artifacts.
Configured once signing secrets are available; not blocking development.
