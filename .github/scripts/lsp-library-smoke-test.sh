#!/usr/bin/env bash
#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Verify the installed editor as a library consumer outside the Maven reactor.
# Usage: bash .github/scripts/lsp-library-smoke-test.sh <version> [Maven options...]
set -euo pipefail

version="${1:?Supply the installed SAPL version}"
shift
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cat >"$work/pom.xml" <<'XML'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.sapl.smoke</groupId>
  <artifactId>editor-consumer</artifactId>
  <version>1.0.0</version>
  <dependencies>
    <dependency>
      <groupId>io.sapl</groupId>
      <artifactId>sapl-vaadin-editor</artifactId>
      <version>${sapl.version}</version>
    </dependency>
  </dependencies>
</project>
XML

mvn -B "$@" -f "$work/pom.xml" -Dsapl.version="$version" \
    dependency:build-classpath -Dmdep.outputFile="$work/classpath"

cat >"$work/LspConsumerSmoke.java" <<'JAVA'
import io.sapl.lsp.server.SAPLLanguageServer;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class LspConsumerSmoke {
    public static void main(String[] args) throws Exception {
        var initialized = CompletableFuture.supplyAsync(() -> {
            var parameters = new InitializeParams();
            parameters.setCapabilities(new ClientCapabilities());
            return new SAPLLanguageServer().initialize(parameters).join();
        }).get(10, TimeUnit.SECONDS);
        if (initialized.getCapabilities() == null || initialized.getServerInfo() == null) {
            throw new IllegalStateException("Language server did not initialize");
        }
        System.out.println("PASS: installed editor initializes the embedded language server");
    }
}
JAVA

java --class-path "$(cat "$work/classpath")" "$work/LspConsumerSmoke.java"
