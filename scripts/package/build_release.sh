#!/bin/bash
#
# build_release.sh — reproducible IGV-X macOS release packaging.
#
# Produces, under build/release/:
#   IGV-X.app                     (native-launcher app bundle via jpackage)
#   IGV-X-<version>.dmg           (disk image, UDZO, for most users)
#   IGV-X-<version>.zip           (alternate distribution)
#   SHA256SUMS                    (checksums for every artifact)
#   version.txt                   (machine-readable release metadata)
#
# Usage:
#   ./scripts/package/build_release.sh [-v <version>] [--skip-tests] [--sign <identity>]
#
#   -v <version>    Release version, e.g. 2.19.5-igvx.3 (default: 2.19.5-igvx)
#   --skip-tests    Skip the full test suite (use with care; release gate = tests green)
#   --sign <id>     Codesign identity, e.g. "Developer ID Application: Name (TEAMID)".
#                   Default: ad-hoc signing (-s -) for local/dev builds.
#   --no-verify     Skip the post-build integrity verification (default: verify everything)
#
# WHY jpackage: the app MUST use a native Mach-O launcher (jpackage) so the JVM
# registers with LaunchServices as org.igvx.IGVX, not the JDK's bundle id.
# With the old shell-script launcher, macOS routed Finder open-file AppleEvents
# to the wrong identity and AWT never received them — double-clicking a session
# file launched IGV-X but silently dropped the file (Runtian bug report,
# 2026-08-14 msg 175/177, fixed 2026-08-15 commits 166efd284 + d9ab77001).
# This script now calls build_app_jpackage.sh for the app bundle; the old
# extract-and-rename path is GONE.
#
# Reproducibility notes:
#   - The upstream gradle Zip task has an input-tracking quirk: if a previous
#     WithJava zip exists and the jar changed, the zip can be reported
#     UP-TO-DATE and stay stale. This script ALWAYS deletes the old zip (and
#     the dist staging dir) before building, so the lib jars are fresh.
#   - Bundled JDK is vendored in-tree (tools/jdk-21.0.12+8/), gradle home is
#     project-local (.gradle-home/), so a clean checkout builds the same bits.
#   - Bundle ID org.igvx.IGVX, name IGV-X, native launcher Contents/MacOS/IGV-X.
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
MACAPP_DIST="$ROOT/build/IGV-MacApp-dist"
rm -rf "$STAGE"
mkdir -p "$STAGE"

# 1. Release gate: full hermetic test suite
if [[ "$SKIP_TESTS" == "0" ]]; then
  echo "=== [1/7] Full test suite (release gate) ==="
  ./gradlew test --console=plain
else
  echo "=== [1/7] SKIPPING test suite (--skip-tests) ==="
fi

# 2. Build the gradle mac-app dist — we need its Java/lib dir (igv.jar + deps)
#    for jpackage.  Force-fresh: delete stale zip and the dist dir (it
#    accumulates app bundles from builds with different -Pversion values).
echo "=== [2/7] Building gradle mac-app dist (for lib jars) ==="
rm -f "$DIST"/IGV_MacApp_*_WithJava.zip
rm -rf "$MACAPP_DIST"
./gradlew createMacAppWithJavaDistZip \
    -PjdkBundleMac="$JDK" -Pversion="$VERSION" \
    --console=plain

# Find the .app directory produced by gradle in the dist dir.
GRADLE_APP="$(find "$MACAPP_DIST" -maxdepth 1 -type d -name '*.app' | head -1)"
if [[ -z "$GRADLE_APP" || ! -d "$GRADLE_APP" ]]; then
  echo "ERROR: no .app found in $MACAPP_DIST" >&2; ls "$MACAPP_DIST" >&2; exit 1
fi
LIB_DIR="$GRADLE_APP/Contents/Java/lib"
if [[ ! -f "$LIB_DIR/igv.jar" ]]; then
  echo "ERROR: $LIB_DIR/igv.jar not found" >&2; exit 1
fi
echo "   lib jars from: $LIB_DIR"

# 3. Build IGV-X.app with jpackage (native Mach-O launcher)
echo "=== [3/7] Building IGV-X.app via jpackage (native launcher) ==="
# Extract the base dotted version (e.g. "2.19.5" from "2.19.5-igvx.3")
BASE_VER="$(echo "$VERSION" | sed 's/-.*//')"
"$ROOT/scripts/package/build_app_jpackage.sh" "$LIB_DIR" "$STAGE" "$BASE_VER"

APP="$STAGE/IGV-X.app"
if [[ ! -d "$APP" ]]; then
  echo "ERROR: IGV-X.app not produced by jpackage" >&2; ls "$STAGE" >&2; exit 1
fi

# Patch the app version to match the release version (build_app_jpackage.sh
# sets the base version; we ensure it matches here).
PLIST="$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $BASE_VER" "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $BASE_VER" "$PLIST" 2>/dev/null || true

# 4. Verify the native launcher is present (not a shell script)
LAUNCHER="$APP/Contents/MacOS/IGV-X"
if [[ ! -x "$LAUNCHER" ]]; then
  echo "ERROR: native launcher IGV-X not found at $LAUNCHER" >&2; exit 1
fi
file "$LAUNCHER" | grep -q 'Mach-O' || {
  echo "ERROR: launcher is not a Mach-O binary — jpackage may have failed" >&2
  file "$LAUNCHER" >&2; exit 1
}
echo "   native launcher: $(file "$LAUNCHER" | cut -d: -f2-)"

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
launcher=native-jpackage
EOF

# Optional post-build verification
if [[ "$VERIFY" == "1" ]]; then
  echo "=== [7/7] Verifying artifacts ==="
  codesign --verify --deep --strict "$APP" || { echo "FAIL: codesign verify" >&2; exit 1; }
  /usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$PLIST" | grep -q 'org.igvx.IGVX' || { echo "FAIL: bundle id" >&2; exit 1; }
  /usr/libexec/PlistBuddy -c 'Print CFBundleName' "$PLIST" | grep -q 'IGV-X' || { echo "FAIL: bundle name" >&2; exit 1; }
  # Native launcher (Mach-O, not shell script)
  file "$LAUNCHER" | grep -q 'Mach-O' || { echo "FAIL: launcher not Mach-O" >&2; exit 1; }
  # Document types
  /usr/libexec/PlistBuddy -c 'Print :CFBundleDocumentTypes:0:LSItemContentTypes:0' "$PLIST" | grep -q 'org.igvx.session' || { echo "FAIL: session document type" >&2; exit 1; }
  /usr/libexec/PlistBuddy -c 'Print :CFBundleDocumentTypes:1:LSItemContentTypes:0' "$PLIST" | grep -q 'public.xml' || { echo "FAIL: xml Open With type" >&2; exit 1; }
  /usr/libexec/PlistBuddy -c 'Print :UTExportedTypeDeclarations:0:UTTypeIdentifier' "$PLIST" | grep -q 'org.igvx.session' || { echo "FAIL: exported UTI" >&2; exit 1; }
  hdiutil verify "$DMG" >/dev/null
  echo "Verification OK."
fi

echo
echo "=== Release artifacts in $STAGE ==="
ls -lh "$STAGE"
echo
echo "DONE: IGV-X $VERSION packaged (native jpackage launcher)."
