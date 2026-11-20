#!/bin/sh
# Verifies gradle-wrapper.jar against Gradle's published checksum (MASVS K6).
set -eu
cd "$(dirname "$0")/.."
version=$(sed -n 's#.*gradle-\(.*\)-bin\.zip#\1#p' gradle/wrapper/gradle-wrapper.properties)
expected=$(curl -fsSL "https://services.gradle.org/distributions/gradle-$version-wrapper.jar.sha256")
actual=$(sha256sum gradle/wrapper/gradle-wrapper.jar | cut -d' ' -f1)
if [ "$expected" != "$actual" ]; then
    echo "gradle-wrapper.jar checksum mismatch for Gradle $version" >&2
    exit 1
fi
echo "gradle-wrapper.jar OK (Gradle $version)"
