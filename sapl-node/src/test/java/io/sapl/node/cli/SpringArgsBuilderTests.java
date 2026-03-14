/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.node.cli;

import static io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource.BUNDLES;
import static io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource.DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.node.cli.PolicySourceResolver.ResolvedPolicy;
import lombok.val;

@DisplayName("Spring args builder")
class SpringArgsBuilderTests {

    @Test
    @DisplayName("directory mode produces config-type, config-path, and policies-path")
    void whenDirectoryMode_thenCorrectSpringArgs() {
        val resolved = new ResolvedPolicy(DIRECTORY, "/tmp/policies", null, false);
        val args     = SpringArgsBuilder.build(resolved, false, false, false);
        assertThat(args)
                .contains("--io.sapl.pdp.embedded.pdp-config-type=DIRECTORY",
                        "--io.sapl.pdp.embedded.config-path=/tmp/policies",
                        "--io.sapl.pdp.embedded.policies-path=/tmp/policies")
                .noneMatch(a -> a.contains("bundle-security"));
    }

    @Test
    @DisplayName("bundle with public key includes key path without allow-unsigned")
    void whenBundleWithKey_thenIncludesPublicKeyPath() {
        val resolved = new ResolvedPolicy(BUNDLES, "/tmp", "/tmp/key.pub", false);
        val args     = SpringArgsBuilder.build(resolved, false, false, false);
        assertThat(args)
                .contains("--io.sapl.pdp.embedded.pdp-config-type=BUNDLES",
                        "--io.sapl.pdp.embedded.bundle-security.public-key-path=/tmp/key.pub")
                .noneMatch(a -> a.contains("allow-unsigned"));
    }

    @Test
    @DisplayName("bundle with no-verify includes allow-unsigned without key path")
    void whenBundleNoVerify_thenIncludesAllowUnsigned() {
        val resolved = new ResolvedPolicy(BUNDLES, "/tmp", null, true);
        val args     = SpringArgsBuilder.build(resolved, false, false, false);
        assertThat(args).contains("--io.sapl.pdp.embedded.bundle-security.allow-unsigned=true")
                .noneMatch(a -> a.contains("public-key-path"));
    }

    @ParameterizedTest(name = "when {0} enabled")
    @DisplayName("reporting flag includes property and logging override")
    @MethodSource
    void whenReportingFlagEnabled_thenIncludesPropertyAndLogging(String flagName, boolean trace, boolean jsonReport,
            boolean textReport, String expectedProperty) {
        val resolved = new ResolvedPolicy(DIRECTORY, "/tmp", null, false);
        val args     = SpringArgsBuilder.build(resolved, trace, jsonReport, textReport);
        assertThat(args).contains(expectedProperty, "--logging.level.[io.sapl.pdp.interceptors]=INFO");
    }

    static Stream<Arguments> whenReportingFlagEnabled_thenIncludesPropertyAndLogging() {
        return Stream.of(arguments("trace", true, false, false, "--io.sapl.pdp.embedded.print-trace=true"),
                arguments("json-report", false, true, false, "--io.sapl.pdp.embedded.print-json-report=true"),
                arguments("text-report", false, false, true, "--io.sapl.pdp.embedded.print-text-report=true"));
    }

    @Test
    @DisplayName("no reporting flags omits logging override")
    void whenNoReportingFlags_thenNoLoggingOverride() {
        val resolved = new ResolvedPolicy(DIRECTORY, "/tmp", null, false);
        val args     = SpringArgsBuilder.build(resolved, false, false, false);
        assertThat(args).noneMatch(a -> a.contains("logging.level"));
    }

    @Test
    @DisplayName("multiple reporting flags produce single logging override")
    void whenMultipleReportingFlags_thenSingleLoggingOverride() {
        val resolved     = new ResolvedPolicy(DIRECTORY, "/tmp", null, false);
        val args         = SpringArgsBuilder.build(resolved, true, true, true);
        val loggingCount = Arrays.stream(args).filter(a -> a.contains("logging.level")).count();
        assertThat(loggingCount).isEqualTo(1);
    }

}
