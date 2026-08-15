#!/bin/bash
#
# build_release.sh — reproducible IGV-X macOS release packaging.
#
# Produces, under build/release/:
#   IGV-X.app                     (the renamed, re-launchered, signed app bundle)
#   IGV-X-<version>.dmg           (disk image, UDZO, for most users)
#   IGV-X-<version>.zip           (alternate distribution)
#   SHA256SUMS                    (checksums for every artifact)
#   version.txt                   (machine-readable release metadata)
#
# Usage:
#   ./scripts/package/build_release.sh [-v <version>] [--skip-tests] [--sign <identity>]
#
#   -v <version>    Release version, e.g. 2.19.5-igvx.1 (default: 2.19.5-igvx)
#   --skip-tests    Skip the full test suite (use with care; release gate = tests green)
#   --sign <id>     Codesign identity, e.g. "Developer ID Application: Name (TEAMID)".
#                   Default: ad-hoc signing (-s -) for local/dev builds.
#   --no-verify     Skip the post-build integrity verification (default: verify everything)
#
# Reproducibility notes:
#   - The upstream gradle Zip task has an input-tracking quirk: if a previous
#     WithJava zip exists and the jar changed, the zip can be reported
#     UP-TO-DATE and stay stale. This script ALWAYS deletes the old zip (and
#     the release staging dir) before building, so the artifact is fresh.
#   - Bundled JDK is vendored in-tree (tools/jdk-21.0.12+8/), gradle home is
#     project-local (.gradle-home/), so a clean checkout builds the same bits.
#   - The app bundle gets the IGV-X shell launcher (CWD-independent) and
#     IGV-X resources (icon). Bundle ID org.igvx.IGVX, name IGV-X.
#
# Environment (optional, for Developer ID + notarization later):
#   IGVX_DEV_ID  — signing identity (overrides --sign)
#   (Notarization is intentionally NOT wired yet; see docs/release.md.)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

VERSION="2.19.5-igvx"
SKIP_TESTS=0
SIGN_ID="-"            # ad-hoc by default
VERIFY=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    -v) VERSION="$2"; shift 2 ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --sign) SIGN_ID="$2"; shift 2 ;;
    --no-verify) VERIFY=0; shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

if [[ -n "${IGVX_DEV_ID:-}" ]]; then
  SIGN_ID="$IGVX_DEV_ID"
fi

JDK="$ROOT/tools/jdk-21.0.12+8"
JAVA_HOME="$JDK/Contents/Home"
export JAVA_HOME

echo "=== IGV-X release build: version=$VERSION sign='$SIGN_ID' skip_tests=$SKIP_TESTS ==="

# 0. Working dirs
STAGE="$ROOT/build/release"
DIST="$ROOT/build/distributions"
rm -rf "$STAGE"
mkdir -p "$STAGE"

# 1. Release gate: full hermetic test suite
if [[ "$SKIP_TESTS" == "0" ]]; then
  echo "=== [1/7] Full test suite (release gate) ==="
  ./gradlew test --console=plain
else
  echo "=== [1/7] SKIPPING test suite (--skip-tests) ==="
fi

# 2. Force-fresh app bundle zip (delete stale zip to defeat UP-TO-DATE quirk)
#    Also clean build/IGV-MacApp-dist: it accumulates app bundles from builds
#    with different -Pversion values (e.g. IGV_user.app) and the zip task
#    packs EVERYTHING in that dir, silently bloating the release artifact.
echo "=== [2/7] Building mac app bundle with bundled JDK ==="
rm -f "$DIST"/IGV_MacApp_"$VERSION"_WithJava.zip
rm -rf "$ROOT/build/IGV-MacApp-dist"
./gradlew createMacAppWithJavaDistZip \
    -PjdkBundleMac="$JDK" -Pversion="$VERSION" \
    --console=plain

ZIP="$DIST/IGV_MacApp_${VERSION}_WithJava.zip"
if [[ ! -f "$ZIP" ]]; then
  echo "ERROR: expected zip not produced: $ZIP" >&2; exit 1
fi

# 3. Extract and rename to IGV-X.app
echo "=== [3/7] Staging IGV-X.app ==="
STAGE_ZIP="$STAGE/from-gradle.zip"
cp "$ZIP" "$STAGE_ZIP"
(cd "$STAGE" && unzip -q from-gradle.zip && rm -f from-gradle.zip)

APP_SRC="$STAGE/IGV_${VERSION}.app"
APP="$STAGE/IGV-X.app"
if [[ ! -d "$APP_SRC" ]]; then
  echo "ERROR: app bundle not found in zip: $APP_SRC" >&2; ls "$STAGE" >&2; exit 1
fi
# Defensive: drop any other app bundles the zip may carry (stale blobs).
find "$STAGE" -maxdepth 1 -type d -name '*.app' ! -path "$APP_SRC" -exec rm -rf {} +
mv "$APP_SRC" "$APP"

# 4. Enforce IGV-X launcher + resources (defensive: the gradle dist copies our
#    launcher from scripts/mac.app, but verify and re-copy so a future upstream
#    change cannot silently reintroduce the CWD-sensitive stock launcher).
LAUNCHER="$APP/Contents/MacOS/IGV"
if ! grep -q 'IGV-X launcher' "$LAUNCHER" 2>/dev/null; then
  echo "INFO: replacing launcher with IGV-X shell launcher"
  cp scripts/mac.app/Contents/MacOS/IGV "$LAUNCHER"
  chmod 775 "$LAUNCHER"
fi
for RES in IGV_64.png igv_icon.icns; do
  if [[ ! -f "$APP/Contents/Resources/$RES" ]]; then
    cp "scripts/mac.app/Contents/Resources/$RES" "$APP/Contents/Resources/"
  fi
done

# 5. Codesign
#    --force --deep so nested jars/executables get signatures too.
#    Hardened runtime is only for notarized Developer ID builds; ad-hoc keeps
#    the dev-build path simple.
if [[ "$SIGN_ID" == "-" ]]; then
  echo "=== [5/7] Ad-hoc codesign ==="
  codesign --force --deep --sign - "$APP"
else
  echo "=== [5/7] Developer ID codesign: $SIGN_ID ==="
  codesign --force --deep --options runtime --sign "$SIGN_ID" "$APP"
fi

# 6. Package DMG + ZIP
DMG="$STAGE/IGV-X-${VERSION}.dmg"
ZOUT="$STAGE/IGV-X-${VERSION}.zip"
echo "=== [6/7] Creating DMG + ZIP ==="
hdiutil create -volname "IGV-X $VERSION" -srcfolder "$APP" -ov -format UDZO "$DMG" >/dev/null
(cd "$STAGE" && zip -qry "$(basename "$ZOUT")" IGV-X.app)

# 7. Checksums + version metadata
(cd "$STAGE" && shasum -a 256 "$(basename "$DMG")" "$(basename "$ZOUT")" > SHA256SUMS)
cat > "$STAGE/version.txt" <<EOF
version=$VERSION
build_date=$(date -u +%Y-%m-%dT%H:%M:%SZ)
git_head=$(git rev-parse --short HEAD)
git_describe=$(git describe --tags --always 2>/dev/null || echo none)
signing=$SIGN_ID
jdk=$(cd "$JDK" && pwd)
EOF

# Optional post-build verification
if [[ "$VERIFY" == "1" ]]; then
  echo "=== [7/7] Verifying artifacts ==="
  codesign --verify --deep --strict "$APP" || { echo "FAIL: codesign verify" >&2; exit 1; }
  /usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$APP/Contents/Info.plist" | grep -q 'org.igvx.IGVX' || { echo "FAIL: bundle id" >&2; exit 1; }
  /usr/libexec/PlistBuddy -c 'Print CFBundleName' "$APP/Contents/Info.plist" | grep -q 'IGV-X' || { echo "FAIL: bundle name" >&2; exit 1; }
  test -x "$APP/Contents/MacOS/IGV" || { echo "FAIL: launcher not executable" >&2; exit 1; }
  grep -q 'IGV-X launcher' "$APP/Contents/MacOS/IGV" || { echo "FAIL: wrong launcher" >&2; exit 1; }
  /usr/libexec/PlistBuddy -c 'Print :CFBundleDocumentTypes:0:LSItemContentTypes:0' "$APP/Contents/Info.plist" | grep -q 'org.igvx.session' || { echo "FAIL: session document type" >&2; exit 1; }
  /usr/libexec/PlistBuddy -c 'Print :CFBundleDocumentTypes:1:LSItemContentTypes:0' "$APP/Contents/Info.plist" | grep -q 'public.xml' || { echo "FAIL: xml Open With type" >&2; exit 1; }
  /usr/libexec/PlistBuddy -c 'Print :UTExportedTypeDeclarations:0:UTTypeIdentifier' "$APP/Contents/Info.plist" | grep -q 'org.igvx.session' || { echo "FAIL: exported UTI" >&2; exit 1; }
  hdiutil verify "$DMG" >/dev/null
  echo "Verification OK."
fi

echo
echo "=== Release artifacts in $STAGE ==="
ls -lh "$STAGE"
echo
echo "DONE: IGV-X $VERSION packaged."
