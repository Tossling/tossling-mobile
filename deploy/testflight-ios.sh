#!/bin/bash
set -euo pipefail

# Archive the iOS app and upload it to App Store Connect for TestFlight.
#
# Signing is automatic: Xcode creates the distribution certificate and profiles on the first run.
# The upload uses the Apple ID signed in to Xcode, or an App Store Connect API key when
# ASC_KEY_ID, ASC_ISSUER_ID and ASC_KEY_PATH are set.
#
# ios/Tossling/GoogleService-Info.plist is not in git and has to be there before the build.
# UPLOAD=0 stops after the signed .ipa in build/ios-export, nothing leaves this Mac.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TEAM_ID="AG3425642R"
BUILD_DIR="$ROOT_DIR/build/ios-release"
ARCHIVE="$BUILD_DIR/Tossling.xcarchive"
EXPORT_DIR="$BUILD_DIR/export"
UPLOAD="${UPLOAD:-1}"

if [ ! -f ios/Tossling/GoogleService-Info.plist ]; then
    echo "ERROR: ios/Tossling/GoogleService-Info.plist is missing, download it from the Firebase console" >&2
    exit 1
fi
if ! command -v xcodegen >/dev/null 2>&1; then
    echo "ERROR: XcodeGen is required, brew install xcodegen" >&2
    exit 1
fi

AUTH_ARGS=()
if [ -n "${ASC_KEY_ID:-}" ] && [ -n "${ASC_ISSUER_ID:-}" ] && [ -n "${ASC_KEY_PATH:-}" ]; then
    AUTH_ARGS=(-authenticationKeyPath "$ASC_KEY_PATH" -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID")
fi

# The same counter scheme as Android: the number grows before the build and goes back if the run
# fails, so App Store Connect never sees the same build number twice.
BUILD_FILE="ios/build-number.txt"
CURRENT=$(cat "$BUILD_FILE" 2>/dev/null || echo 0)
NEXT=$((CURRENT + 1))
echo "$NEXT" > "$BUILD_FILE"
restore_build() {
    [ "$?" -eq 0 ] && [ "$UPLOAD" = "1" ] || echo "$CURRENT" > "$BUILD_FILE"
}
trap restore_build EXIT

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
(cd ios && xcodegen generate --quiet)

echo "=== Archiving (build $NEXT) ==="
xcodebuild archive \
    -project ios/Tossling.xcodeproj \
    -scheme Tossling \
    -configuration Release \
    -destination generic/platform=iOS \
    -archivePath "$ARCHIVE" \
    -allowProvisioningUpdates \
    ${AUTH_ARGS[@]+"${AUTH_ARGS[@]}"} \
    -quiet \
    CURRENT_PROJECT_VERSION="$NEXT"

if [ ! -d "$ARCHIVE" ]; then
    echo "ERROR: no archive at $ARCHIVE" >&2
    exit 1
fi

DESTINATION=upload
[ "$UPLOAD" = "1" ] || DESTINATION=export
OPTIONS="$BUILD_DIR/ExportOptions.plist"
cat > "$OPTIONS" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store-connect</string>
    <key>destination</key>
    <string>$DESTINATION</string>
    <key>teamID</key>
    <string>$TEAM_ID</string>
    <key>signingStyle</key>
    <string>automatic</string>
    <key>uploadSymbols</key>
    <true/>
    <key>manageAppVersionAndBuildNumber</key>
    <false/>
</dict>
</plist>
PLIST

echo "=== Exporting ($DESTINATION) ==="
xcodebuild -exportArchive \
    -archivePath "$ARCHIVE" \
    -exportOptionsPlist "$OPTIONS" \
    -exportPath "$EXPORT_DIR" \
    -allowProvisioningUpdates \
    ${AUTH_ARGS[@]+"${AUTH_ARGS[@]}"}

if [ "$UPLOAD" = "1" ]; then
    echo "=== Build $NEXT is uploaded, TestFlight shows it after processing (usually 5 to 30 minutes) ==="
else
    echo "=== Signed build $NEXT: $(ls "$EXPORT_DIR"/*.ipa) ==="
fi
