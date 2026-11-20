#!/usr/bin/env bash
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

repository="${1:-$PWD/.github/maven/repository}"
dependency_plugin=org.apache.maven.plugins:maven-dependency-plugin:3.11.0

resolve() {
    local attempt
    for attempt in 1 2 3; do
        if timeout 15m mvn -B -U -ntp "-Dmaven.repo.local=$repository" "$@"; then
            return 0
        fi
        if [[ "$attempt" == 3 ]]; then
            echo "Maven dependency preparation failed after three attempts." >&2
            return 1
        fi
        echo "Retrying Maven dependency preparation after attempt $attempt."
        sleep "$((attempt * 10))"
    done
}

property() {
    local expression="$1"
    mvn -o -B -N -q "-Dmaven.repo.local=$repository" help:evaluate \
        "-Dexpression=$expression" -DforceStdout
}

# Resolve the reactor first, then the additional CI profiles without running a lifecycle.
resolve "$dependency_plugin:go-offline" -DexcludeReactor=true
resolve "$dependency_plugin:go-offline" -DexcludeReactor=true \
    -Pcoverage,it,spotbugs,production,sign,sonatypeDeploy,native,native-linux,native-linux-arm,conformance
resolve -N help:evaluate -Dexpression=project.version -q -DforceStdout
# The standalone code style POM and the library smoke test have no plugin management.
resolve -N -f sapl-code-style/pom.xml dependency:help javadoc:help

# Include the installed library's dependency graph without reactor dependency management.
sapl_version=$(property project.version)
resolve -f .github/maven/pom.xml "$dependency_plugin:tree" \
    -pl :editor-consumer -am "-Dsapl.version=$sapl_version" \
    -DoutputType=json -DoutputFile=target/consumer-dependencies.json
python3 .github/maven/editor-dependencies.py
resolve -f .github/maven/editor-consumer/target/dependencies/pom.xml \
    "$dependency_plugin:go-offline" -DexcludeReactor=true

# Surefire selects these at runtime, outside the plugin's declared dependencies.
surefire_version=$(property maven-surefire-plugin.version)
junit_version=$(property jupiter.version)
resolve -N "$dependency_plugin:get" \
    "-Dartifact=org.apache.maven.surefire:surefire-junit-platform:$surefire_version"
resolve -N "$dependency_plugin:get" \
    "-Dartifact=org.junit.platform:junit-platform-launcher:$junit_version"
resolve -N "$dependency_plugin:get" \
    "-Dartifact=org.junit.vintage:junit-vintage-engine:$junit_version"
spotbugs_contrib_version=$(property sb-contrib.version)
resolve -N "$dependency_plugin:get" \
    "-Dartifact=com.mebigfatguy.sb-contrib:sb-contrib:$spotbugs_contrib_version"

# Reactor Netty declares Intel classifiers. Include the ARM runner equivalents.
for artifact in netty-transport-native-epoll netty-resolver-dns-native-macos; do
    if [[ "$artifact" == netty-transport-native-epoll ]]; then
        classifier=linux-aarch_64
    else
        classifier=osx-aarch_64
    fi
    for pom in "$repository/io/netty/$artifact"/*/*.pom; do
        version=$(basename "$(dirname "$pom")")
        resolve -N "$dependency_plugin:get" -Dtransitive=false \
            "-Dartifact=io.netty:$artifact:$version:jar:$classifier"
    done
done

bash .github/maven/sanitize.sh "$repository"
