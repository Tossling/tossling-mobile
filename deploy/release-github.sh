#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

VERSION=$(sed -n 's/^appversion = "\(.*\)"/\1/p' gradle/libs.versions.toml)
CODE=$(cat app/version-code.txt)
TAG="v$VERSION"
APK="app/build/outputs/apk/release/app-release.apk"
OUT="build/github"

if [ -n "$(git status --porcelain)" ]; then
    echo "ERROR: commit or stash your changes first" >&2
    exit 1
fi
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || gh release view "$TAG" >/dev/null 2>&1; then
    echo "ERROR: $TAG already exists; raise appversion in gradle/libs.versions.toml" >&2
    exit 1
fi
if ! command -v gh >/dev/null 2>&1; then
    echo "ERROR: the GitHub CLI is required: brew install gh" >&2
    exit 1
fi

echo "=== Building Tossling $VERSION ($CODE) ==="
./gradlew :app:assembleRelease --console=plain -q

APKSIGNER="$(ls "$HOME"/Library/Android/sdk/build-tools/*/apksigner 2>/dev/null | tail -1)"
if [ -z "$APKSIGNER" ] || ! "$APKSIGNER" verify "$APK" >/dev/null 2>&1; then
    echo "ERROR: the APK is not signed; check tossling.jks and keystore_* in local.properties" >&2
    exit 1
fi
CERT=$("$APKSIGNER" verify --print-certs "$APK" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)

mkdir -p "$OUT"
NAME="Tossling-$VERSION.apk"
cp "$APK" "$OUT/$NAME"
(cd "$OUT" && shasum -a 256 "$NAME" > "$NAME.sha256")

git tag -a "$TAG" -m "Tossling for Android $VERSION ($CODE)"
git push -q origin "$TAG"
gh release create "$TAG" "$OUT/$NAME" "$OUT/$NAME.sha256" --title "Tossling for Android $VERSION" --notes "Build $CODE. Android 13 or newer.

Signing certificate SHA-256: \`$CERT\`"

echo "=== Released $TAG ==="
