# IGV-X packaging scripts

Reproducible macOS release packaging. See `docs/release.md` for the full
release contract; this directory holds the executable procedures.

## build_release.sh

Builds a complete IGV-X release from a clean-ish state:

1. **Release gate** — full hermetic test suite (`./gradlew test`) unless
   `--skip-tests`.
2. **Fresh app bundle** — deletes the old `WithJava` zip AND cleans
   `build/IGV-MacApp-dist` (which accumulates stale app bundles from
   different `-Pversion` builds, e.g. `IGV_user.app`, and would bloat the
   release artifact), then runs
   `./gradlew createMacAppWithJavaDistZip -PjdkBundleMac=... -Pversion=...`.
3. **Stage + rename** — unzips, drops any stray `.app` bundles, renames to
   `IGV-X.app`.
4. **Enforce IGV-X launcher + resources** — verifies the bundle carries the
   CWD-independent IGV-X shell launcher (grep for `IGV-X launcher`);
   re-copies from `scripts/mac.app` if a future upstream change ever
   reintroduces the stock CWD-sensitive launcher. Also verifies/copies
   `IGV_64.png` and `igv_icon.icns`.
5. **Codesign** — ad-hoc (`-s -`) by default; `--sign "Developer ID
   Application: ..."` (or `IGVX_DEV_ID` env) for Developer ID builds with
   hardened runtime.
6. **Package** — UDZO DMG + ZIP into `build/release/`.
7. **Checksums + version metadata** — `SHA256SUMS` + `version.txt`
   (version, build date, git head/describe, signing identity, JDK path).
8. **Verification** — codesign verify, bundle ID/name checks, launcher
   checks, `hdiutil verify` (skip with `--no-verify`).

### Usage

```bash
# Local/dev release (ad-hoc signing, tests as release gate)
./scripts/package/build_release.sh -v 2.19.5-igvx.1

# Fast iteration (no tests, no verify)
./scripts/package/build_release.sh -v 2.19.5-igvx.1 --skip-tests --no-verify

# Developer ID build (when a certificate is available)
./scripts/package/build_release.sh -v 2.19.5-igvx.1 --sign "Developer ID Application: Name (TEAMID)"
```

Artifacts land in `build/release/` (gitignored):
`IGV-X.app`, `IGV-X-<version>.dmg`, `IGV-X-<version>.zip`, `SHA256SUMS`,
`version.txt`.

## Notarization (future)

Notarization is intentionally not wired yet — it needs Runtian's Developer
ID certificate + Apple ID credentials stored outside the repo. When
available, add a `notarize.sh` step: `xcrun notarytool submit` on the DMG,
`xcrun stapler staple`, then re-verify. See `docs/release.md` §5.
