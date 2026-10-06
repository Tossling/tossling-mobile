#!/bin/bash
set -euo pipefail

# Build a release APK and hand it to testers through Firebase App Distribution.
#
# Via the Firebase CLI rather than their Gradle plugin: the plugin still needs `AppExtension`,
# which AGP 9 removed, and fails as soon as it is applied.
#
# Signing uses the same keystore as every other build (tossy.jks + keystore_* in local.properties).
# Firebase access: either `firebase login` once in this terminal, or a service-account key in
# GOOGLE_APPLICATION_CREDENTIALS.
#
# Firebase IDs: FIREBASE_APP_ID, FIREBASE_PROJECT, FIREBASE_GROUPS (comma-separated aliases) from the
# environment or firebase_app_id, firebase_project, firebase_groups in local.properties.
# NOTES_FILE or NOTES for the release notes.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

APK_PATH="app/build/outputs/apk/release/app-release.apk"
NOTES_FILE="${NOTES_FILE:-}"

local_property() {
    sed -n "s/^$1=//p" local.properties 2>/dev/null | tail -1
}

APP_ID="${FIREBASE_APP_ID:-$(local_property firebase_app_id)}"
# The project is named explicitly: without it the CLI looks the tester group up in the wrong place
# and answers 404, even though the APK itself uploads fine.
PROJECT="${FIREBASE_PROJECT:-$(local_property firebase_project)}"
# Not GROUPS: that is bash's list of the user's groups, assignments to it are ignored, and the
# system gid went out instead of the group alias.
TESTER_GROUPS="${FIREBASE_GROUPS:-$(local_property firebase_groups)}"

if [ -z "$APP_ID" ] || [ -z "$PROJECT" ] || [ -z "$TESTER_GROUPS" ]; then
    echo "ERROR: set FIREBASE_APP_ID, FIREBASE_PROJECT and FIREBASE_GROUPS," >&2
    echo "       or firebase_app_id, firebase_project and firebase_groups in local.properties" >&2
    exit 1
fi

if ! command -v firebase >/dev/null 2>&1; then
    echo "ERROR: Firebase CLI is required — npm i -g firebase-tools, then firebase login" >&2
    exit 1
fi

# The build number grows before every distribution: otherwise Android won't install the new build
# over the old one. It is needed at build time already, so a failed attempt puts it back —
# otherwise failed runs would eat numbers for nothing.
VERSION_FILE="app/version-code.txt"
CURRENT=$(cat "$VERSION_FILE" 2>/dev/null || echo 1)
NEXT=$((CURRENT + 1))
echo "$NEXT" > "$VERSION_FILE"
restore_version() {
    [ "$?" -eq 0 ] || echo "$CURRENT" > "$VERSION_FILE"
}
trap restore_version EXIT

echo "=== Building release (versionCode $NEXT) ==="
./gradlew :app:assembleRelease

if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: no $APK_PATH after the build" >&2
    exit 1
fi

# The signature is checked before upload: an APK signed with another key won't install over the
# one testers already have, and hearing that from a tester is the worst way to find out.
APKSIGNER="$(ls "$HOME"/Library/Android/sdk/build-tools/*/apksigner 2>/dev/null | tail -1)"
if [ -n "$APKSIGNER" ]; then
    if ! "$APKSIGNER" verify "$APK_PATH" >/dev/null 2>&1; then
        echo "ERROR: APK is not signed — check tossy.jks + keystore_* in local.properties" >&2
        exit 1
    fi
    echo "Signature: $("$APKSIGNER" verify --print-certs "$APK_PATH" | grep -m1 "certificate DN")"
fi

NOTES_ARGS=()
if [ -n "$NOTES_FILE" ] && [ -f "$NOTES_FILE" ]; then
    NOTES_ARGS=(--release-notes-file "$NOTES_FILE")
elif [ -n "${NOTES:-}" ]; then
    NOTES_ARGS=(--release-notes "$NOTES")
fi

echo "=== Uploading to Firebase App Distribution ==="
# An empty array under `set -u` counts as unbound in bash 3.2 (the macOS default), hence the
# `${arr[@]+…}` form: it expands to zero arguments instead of an error.
firebase appdistribution:distribute "$APK_PATH" \
    --app "$APP_ID" \
    --project "$PROJECT" \
    --groups "$TESTER_GROUPS" \
    ${NOTES_ARGS[@]+"${NOTES_ARGS[@]}"}

echo ""
echo "=== Done ==="
echo "Testers in «${TESTER_GROUPS}» will get an email with an install link."
