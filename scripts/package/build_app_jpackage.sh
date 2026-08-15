#!/bin/bash
#
# build_app_jpackage.sh — build the IGV-X.app bundle with jpackage (native launcher).
#
# WHY jpackage: the previous app used a shell-script launcher (Contents/MacOS/IGV)
# that exec'd the bundled JDK's java binary. With that launcher, the JVM process
# registers with LaunchServices under the JDK's bundle identity
# (net.java.openjdk.jdk / net.java.openjdk.java), NOT org.igvx.IGVX. macOS routes
# Finder open-file AppleEvents (kAEOpenDocuments) to the app by its registered
# bundle id, so the AWT open-file handler never received them — double-clicking a
# session file launched IGV-X but silently dropped the file (Runtian bug report
# 2026-08-14 msg 175/177).
#
# jpackage produces a native Mach-O launcher (Contents/MacOS/IGV-X), so
# NSBundle mainBundle resolves to the app bundle, the JVM registers as
# org.igvx.IGVX, and AppleEvents are delivered to java.awt.Desktop handlers.
# Combined with DesktopIntegration's EDT-installed handler + buffered drain,
# cold-launch session opens now work (verified 2026-08-15 with a real session).
#
# Usage:
#   ./scripts/package/build_app_jpackage.sh <lib-dir> <out-dir>
#     <lib-dir>  the dist Java/lib dir containing igv.jar + dependencies
#                (e.g. build/IGV-MacApp-dist/IGV_<ver>.app/Contents/Java/lib)
#     <out-dir>  destination dir; writes <out-dir>/IGV-X.app
#
# Requires: the vendored JDK (tools/jdk-21.0.12+8) which ships jpackage.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

LIB_DIR="${1:?usage: build_app_jpackage.sh <lib-dir> <out-dir>}"
OUT_DIR="${2:?usage: build_app_jpackage.sh <lib-dir> <out-dir>}"
JDK="$ROOT/tools/jdk-21.0.12+8"
JAVA_HOME="$JDK/Contents/Home"
JPACKAGE="$JAVA_HOME/bin/jpackage"
ICON="$ROOT/scripts/mac.app/Contents/Resources/IGV_64.png"
ICNS="$ROOT/scripts/mac.app/Contents/Resources/igv_icon.icns"

if [[ ! -x "$JPACKAGE" ]]; then
  echo "ERROR: jpackage not found at $JPACKAGE (vendored JDK missing?)" >&2
  exit 1
fi
if [[ ! -f "$LIB_DIR/igv.jar" ]]; then
  echo "ERROR: $LIB_DIR/igv.jar not found; run the gradle dist build first" >&2
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# Build the app image.  --runtime-image uses the full vendored JDK as the
# embedded runtime.  The JVM options mirror the old Java/igv.args file.
"$JPACKAGE" --type app-image \
  --name "IGV-X" \
  --app-version "2.19.5" \
  --module-path "$LIB_DIR" \
  --module org.igv/org.broad.igv.ui.Main \
  --java-options "-Xmx8g" \
  --java-options "-Xdock:name=IGV-X" \
  --java-options "-Dapple.laf.useScreenMenuBar=true" \
  --java-options "--add-exports=java.desktop/com.sun.java.swing.plaf.windows=jide.common" \
  --java-options "--add-exports=java.desktop/javax.swing.plaf.synth=jide.common" \
  --java-options "--add-exports=java.desktop/sun.swing=jide.common" \
  --java-options "--add-exports=java.desktop/sun.awt=jide.common" \
  --java-options "--add-exports=java.desktop/sun.awt.image=jide.common" \
  --java-options "--add-exports=java.desktop/sun.awt.shell=jide.common" \
  --java-options "--add-exports=java.desktop/sun.awt.dnd=jide.common" \
  --java-options "--add-exports=java.desktop/sun.awt.windows=jide.common" \
  --java-options "--add-exports=java.base/sun.security.action=jide.common" \
  --java-options "-Dcom.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize" \
  --runtime-image "$JAVA_HOME" \
  --icon "$ICNS" \
  --dest "$OUT_DIR"

APP="$OUT_DIR/IGV-X.app"
PLIST="$APP/Contents/Info.plist"

# Patch the generated Info.plist with IGV-X identity + Finder associations.
/usr/libexec/PlistBuddy -c 'Set :CFBundleIdentifier org.igvx.IGVX' "$PLIST"
/usr/libexec/PlistBuddy -c 'Set :CFBundleName IGV-X' "$PLIST"
/usr/libexec/PlistBuddy -c 'Set :CFBundleDisplayName IGV-X' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :NSHighResolutionCapable bool true' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :LSApplicationCategoryType string public.app-category.developer-tools' "$PLIST" 2>/dev/null || true

# Document types: org.igvx.session (Owner) + public.xml (Alternate)
/usr/libexec/PlistBuddy -c 'Add :CFBundleDocumentTypes array' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :CFBundleDocumentTypes:0 dict' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Set :CFBundleDocumentTypes:0:CFBundleTypeName "IGV-X Session"' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Set :CFBundleDocumentTypes:0:CFBundleTypeRole Editor' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Set :CFBundleDocumentTypes:0:LSHandlerRank Owner' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :CFBundleDocumentTypes:0:LSItemContentTypes array' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Set :CFBundleDocumentTypes:0:LSItemContentTypes:0 org.igvx.session' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :CFBundleDocumentTypes:1 dict' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Set :CFBundleDocumentTypes:1:CFBundleTypeName "XML Document"' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Set :CFBundleDocumentTypes:1:CFBundleTypeRole Editor' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Set :CFBundleDocumentTypes:1:LSHandlerRank Alternate' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :CFBundleDocumentTypes:1:LSItemContentTypes array' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Set :CFBundleDocumentTypes:1:LSItemContentTypes:0 public.xml' "$PLIST" 2>/dev/null || true

# Export the org.igvx.session UTI (so .igvx/.session/.idxsession files get the
# IGV-X icon in Finder) and declare the extension tags.
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations array' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0 dict' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Set :UTExportedTypeDeclarations:0:UTTypeIdentifier org.igvx.session' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeDescription string "IGV-X Session"' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeConformsTo array' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeConformsTo:0 string public.xml' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeConformsTo:1 string public.data' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeIconFiles array' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeIconFiles:0 string IGV-X.icns' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeTagSpecification dict' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeTagSpecification:public.filename-extension array' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeTagSpecification:public.filename-extension:0 string igvx' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeTagSpecification:public.filename-extension:1 string session' "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c 'Add :UTExportedTypeDeclarations:0:UTTypeTagSpecification:public.filename-extension:2 string idxsession' "$PLIST" 2>/dev/null || true

# Make sure the IGV-X icon file is present in Resources (jpackage generates its
# own IGV-X.icns from --icon; also copy the PNG for the launcher).
cp "$ICON" "$APP/Contents/Resources/IGV_64.png" 2>/dev/null || true

# Re-register with LaunchServices so Finder picks up the document types.
/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f "$APP" 2>/dev/null || true

echo "OK: $APP"
