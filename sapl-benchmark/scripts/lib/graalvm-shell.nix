#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# shells/graalvm-shell.nix
# GraalVM native image development shell.
# Usage: nix develop ~/.dotfiles#graalvm
#
# Regular (glibc) native builds work directly with native-image.
# Static musl builds use Docker to avoid NixOS glibc/musl path mixing
# (see nixpkgs#142392). Run `build-musl <module>` for musl builds.
{ pkgs }:
let
  build-musl = pkgs.writeShellScriptBin "build-musl" ''
    set -euo pipefail
    module="''${1:?Usage: build-musl <module>}"
    image="ghcr.io/graalvm/native-image-community:25-muslib"
    maven_version="3.9.12"
    maven_url="https://archive.apache.org/dist/maven/maven-3/$maven_version/binaries/apache-maven-$maven_version-bin.tar.gz"

    echo "Building $module static musl binary via Docker ($image)..."
    exec docker run --rm \
      --entrypoint bash \
      -v "$HOME/.m2:/root/.m2" \
      -v "$(pwd):/project" \
      -w /project \
      "$image" \
      -c "curl -sL $maven_url | tar xz -C /opt && \
          export PATH=/opt/apache-maven-$maven_version/bin:\$PATH && \
          mvn -B install -pl $module -am -Dmaven.test.skip=true -q && \
          mvn -B package -pl $module -Pnative-linux -Dmaven.test.skip=true"
  '';
in
pkgs.mkShell {
  name = "graalvm-native-dev";

  buildInputs = [
    pkgs.graalvmPackages.graalvm-ce
    pkgs.maven
    build-musl
  ];

  shellHook = ''
    echo "GraalVM Native Image Development Shell"
    echo "  native-image: $(native-image --version 2>/dev/null | head -1 || echo 'not found')"
    echo "  maven: $(mvn --version 2>/dev/null | head -1)"
    echo ""
    echo "Usage:"
    echo "  Regular native build:  mvn package -pl <module> -Pnative -DskipTests"
    echo "  Static musl build:     build-musl <module>"
  '';
}
