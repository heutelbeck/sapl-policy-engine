#!/usr/bin/env bash
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

version=3.10.0
checksum=908b1501bfb420bf7c8affb855534a9c407fd6099367bfb9f2f2dcb8e9799102bffb84518cde74c679bd76870247c6528683abdd620581bffa90f95d92d175aa
directory="${RUNNER_TEMP:?Runner temporary directory is required.}"
if [[ "${RUNNER_OS:-}" == Windows ]]; then
    directory=$(cygpath -u "$directory")
fi
archive="$directory/apache-maven-$version-bin.tar.gz"
curl --proto '=https' -fsSL --retry 3 --retry-all-errors --retry-max-time 180 \
    --connect-timeout 30 --max-time 180 \
    "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.tar.gz" \
    -o "$archive"
if command -v sha512sum >/dev/null; then
    echo "$checksum  $archive" | sha512sum -c -
else
    echo "$checksum  $archive" | shasum -a 512 -c -
fi
tar -xzf "$archive" -C "$directory"
bin="$directory/apache-maven-$version/bin"
if [[ "${RUNNER_OS:-}" == Windows ]]; then
    bin=$(cygpath -w "$bin")
fi
echo "$bin" >> "${GITHUB_PATH:?GitHub path file is required.}"
