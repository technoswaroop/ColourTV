#!/bin/bash
# Builds the release APK, signed with the project's key, and attaches it to the GitHub release of the version in
# app/build.gradle as colourtv.apk: what people install with adb, and what ColourTV settings → Updates installs.
# Uses release.properties when present; otherwise it uses this computer's Android debug key.
#   ./release-apk.sh            the release must already exist (gh release create vX.Y.Z …)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
VERSION="$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle)"
# The key ColourTV checks updates against (SelfUpdate.RELEASE_CERT).
CERT="$(sed -n 's/.*RELEASE_CERT = "\([0-9a-f]*\)".*/\1/p' app/src/main/java/com/techpp/glass/SelfUpdate.java)"
echo "== Building $VERSION"
./gradlew -q clean assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
SIGNER="$(ls -d "$ANDROID_HOME"/build-tools/*/ | tail -1)apksigner"
GOT="$("$SIGNER" verify --print-certs "$APK" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)"
[ "$GOT" = "$CERT" ] || { echo "!! The APK isn't signed with the release key (got $GOT)."; exit 1; }
mkdir -p build
cp "$APK" "$HERE/build/colourtv.apk"
echo "== Attaching colourtv.apk ($(wc -c < build/colourtv.apk | tr -d ' ') bytes) to v$VERSION"
gh release upload "v$VERSION" build/colourtv.apk --clobber
echo "Done: https://github.com/technoswaroop/ColourTV/releases/tag/v$VERSION"
