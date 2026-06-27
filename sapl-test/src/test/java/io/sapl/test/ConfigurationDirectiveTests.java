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
package io.sapl.test;

import io.sapl.test.plain.PlainTestAdapter;
import io.sapl.test.plain.PlainTestResults;
import io.sapl.test.plain.SaplDocument;
import io.sapl.test.plain.SaplTestDocument;
import io.sapl.test.plain.TestConfiguration;
import io.sapl.test.plain.TestStatus;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Configuration directive tests")
class ConfigurationDirectiveTests {

    private static final PlainTestAdapter ADAPTER                = new PlainTestAdapter();
    private static final String           POLICY_PERMIT          = "policy \"policy_permit\" permit";
    private static final String           POLICY_DENY            = "policy \"policy_deny\" deny";
    private static final String           PDP_JSON_PRIORITY_DENY = """
            {
                "algorithm": {
                    "votingMode": "PRIORITY_DENY",
                    "defaultDecision": "DENY",
                    "errorHandling": "ABSTAIN"
                },
                "variables": {}
            }
            """;

    @Nested
    @DisplayName("configuration directive")
    class ConfigurationDirective {

        @Test
        @DisplayName("loads folder and applies pdp.json PRIORITY_DENY algorithm")
        void whenConfigurationLoadsFolderThenPdpJsonDenyAlgorithmApplies() {
            val testCode = """
                    requirement "configuration folder loading" {
                        scenario "pdp.json PRIORITY_DENY algorithm makes deny win"
                            given
                                - configuration "configuration-tests/test-config"
                            when "WILLI" attempts "read" on "foo"
                            expect deny;
                    }
                    """;
            val results  = executeWithoutPolicies(testCode);

            assertSinglePassedTest(results);
        }

        @Test
        @DisplayName("inline algorithm overrides pdp.json algorithm")
        void whenInlineAlgorithmSpecifiedThenOverridesPdpJson() {
            val testCode = """
                    requirement "algorithm override" {
                        scenario "inline permit-overrides overrides pdp.json deny-overrides"
                            given
                                - configuration "configuration-tests/test-config"
                                - priority permit or permit errors propagate
                            when "WILLI" attempts "read" on "foo"
                            expect permit;
                    }
                    """;
            val results  = executeWithoutPolicies(testCode);

            assertSinglePassedTest(results);
        }

        @Test
        @DisplayName("rejects combination with documents directive")
        void whenCombinedWithDocumentsThenValidationError() {
            val testCode = """
                    requirement "invalid combination" {
                        scenario "configuration and documents cannot coexist"
                            given
                                - configuration "configuration-tests/test-config"
                                - documents "policy_permit"
                            when "WILLI" attempts "read" on "foo"
                            expect deny;
                    }
                    """;
            val results  = executeWithPolicies(testCode);

            assertSingleErrorWithMessage(results, "Cannot use 'configuration' together with 'document' or 'documents'");
        }

        @Test
        @DisplayName("rejects combination with pdp-configuration directive")
        void whenCombinedWithPdpConfigurationThenValidationError() {
            val testCode = """
                    requirement "invalid combination" {
                        scenario "configuration and pdp-configuration cannot coexist"
                            given
                                - configuration "configuration-tests/test-config"
                                - pdp-configuration "configuration-tests/pdp-config-only/pdp.json"
                            when "WILLI" attempts "read" on "foo"
                            expect deny;
                    }
                    """;
            val results  = executeWithoutPolicies(testCode);

            assertSingleErrorWithMessage(results, "Cannot use 'configuration' together with 'pdp-configuration'");
        }

        @Test
        @DisplayName("filesystem configuration paths with dot segments stay inside the base path")
        void whenConfigurationPathContainsDotSegmentsThenPathIsContained(@TempDir Path tempDir) throws Exception {
            val basePath      = Files.createDirectory(tempDir.resolve("base"));
            val outsideConfig = Files.createDirectory(tempDir.resolve("outside-config"));
            Files.writeString(outsideConfig.resolve("pdp.json"), PDP_JSON_PRIORITY_DENY);
            Files.writeString(outsideConfig.resolve("policy_deny.sapl"), POLICY_DENY);
            Files.writeString(outsideConfig.resolve("policy_permit.sapl"), POLICY_PERMIT);
            val testCode = """
                    requirement "configuration path containment" {
                        scenario "escaped configuration path is rejected"
                            given
                                - configuration "../outside-config"
                            when "WILLI" attempts "read" on "foo"
                            expect deny;
                    }
                    """;

            val results = executeWithBasePath(testCode, basePath, List.of());

            assertSingleErrorWithMessage(results, "base path");
        }
    }

    @Nested
    @DisplayName("pdp-configuration directive")
    class PdpConfigurationDirective {

        @Test
        @DisplayName("loads pdp.json algorithm and applies to explicit documents")
        void whenPdpConfigurationWithDocumentsThenPdpJsonAlgorithmApplies() {
            val testCode = """
                    requirement "pdp-configuration loading" {
                        scenario "pdp.json PRIORITY_DENY algorithm applies to listed documents"
                            given
                                - pdp-configuration "configuration-tests/pdp-config-only/pdp.json"
                                - documents "policy_permit", "policy_deny"
                            when "WILLI" attempts "read" on "foo"
                            expect deny;
                    }
                    """;
            val results  = executeWithPolicies(testCode);

            assertSinglePassedTest(results);
        }

        @Test
        @DisplayName("inline algorithm overrides pdp.json algorithm")
        void whenInlineAlgorithmSpecifiedThenOverridesPdpJson() {
            val testCode = """
                    requirement "algorithm override" {
                        scenario "inline permit-overrides overrides pdp.json deny-overrides"
                            given
                                - pdp-configuration "configuration-tests/pdp-config-only/pdp.json"
                                - documents "policy_permit", "policy_deny"
                                - priority permit or permit errors propagate
                            when "WILLI" attempts "read" on "foo"
                            expect permit;
                    }
                    """;
            val results  = executeWithPolicies(testCode);

            assertSinglePassedTest(results);
        }

        @Test
        @DisplayName("filesystem PDP configuration paths with dot segments stay inside the base path")
        void whenPdpConfigurationPathContainsDotSegmentsThenPathIsContained(@TempDir Path tempDir) throws Exception {
            val basePath   = Files.createDirectory(tempDir.resolve("base"));
            val outsidePdp = tempDir.resolve("outside-pdp.json");
            Files.writeString(outsidePdp, PDP_JSON_PRIORITY_DENY);
            val testCode = """
                    requirement "pdp configuration path containment" {
                        scenario "escaped pdp configuration path is rejected"
                            given
                                - pdp-configuration "../outside-pdp.json"
                                - documents "policy_permit", "policy_deny"
                            when "WILLI" attempts "read" on "foo"
                            expect deny;
                    }
                    """;
            val policies = List.of(SaplDocument.of("policy_permit", POLICY_PERMIT),
                    SaplDocument.of("policy_deny", POLICY_DENY));

            val results = executeWithBasePath(testCode, basePath, policies);

            assertSingleErrorWithMessage(results, "base path");
        }
    }

    private PlainTestResults executeWithoutPolicies(String testCode) {
        val testDoc = SaplTestDocument.of("test", testCode);
        val config  = TestConfiguration.builder().withSaplTestDocument(testDoc).build();
        return ADAPTER.execute(config);
    }

    private PlainTestResults executeWithPolicies(String testCode) {
        val testDoc = SaplTestDocument.of("test", testCode);
        val config  = TestConfiguration.builder().withSaplDocuments(
                List.of(SaplDocument.of("policy_permit", POLICY_PERMIT), SaplDocument.of("policy_deny", POLICY_DENY)))
                .withSaplTestDocument(testDoc).build();
        return ADAPTER.execute(config);
    }

    private PlainTestResults executeWithBasePath(String testCode, Path basePath, List<SaplDocument> policies) {
        val testDoc = SaplTestDocument.of("test", testCode);
        val config  = TestConfiguration.builder().withSaplDocuments(policies).withSaplTestDocument(testDoc)
                .withBasePath(basePath).build();
        return ADAPTER.execute(config);
    }

    private static void assertSinglePassedTest(PlainTestResults results) {
        assertThat(results.total()).isOne();
        assertThat(results.passed()).isOne();
        assertThat(results.allPassed()).isTrue();
    }

    private static void assertSingleErrorWithMessage(PlainTestResults results, String expectedMessage) {
        assertThat(results.total()).isOne();
        assertThat(results.errors()).isOne();
        assertThat(results.scenarioResults().getFirst()).satisfies(result -> {
            assertThat(result.status()).isEqualTo(TestStatus.ERROR);
            assertThat(result.failureMessage()).contains(expectedMessage);
        });
    }
}
