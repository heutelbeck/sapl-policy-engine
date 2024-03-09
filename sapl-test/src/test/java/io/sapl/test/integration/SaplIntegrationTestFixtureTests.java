/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.integration;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;

class SaplIntegrationTestFixtureTests {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void test() {
        var fixture = new SaplIntegrationTestFixture("policiesIT");
        fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit().verify();
    }

    @Test
    void test_withPDPPolicyCombiningAlgorithm() {
        var fixture = new SaplIntegrationTestFixture("policiesIT");
        fixture.withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY).constructTestCase()
                .when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny().verify();
    }

    @Test
    void test_withPDPVariables() {
        var fixture   = new SaplIntegrationTestFixture("it/variables");
        var variables = new HashMap<String, Val>(1);
        variables.put("test", Val.of(mapper.createObjectNode().numberNode(1)));
        fixture.withPDPVariables(variables).constructTestCase()
                .when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit().verify();
    }

    @Test
    void test_withoutPDPVariables() {
        var fixture = new SaplIntegrationTestFixture("it/variables");
        fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectNotApplicable()
                .verify();
    }

    @Test
    void test_invalidPath1() {
        var fixture = new SaplIntegrationTestFixture("");
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase);
    }

    @Test
    void test_invalidPath2() {
        SaplTestFixture fixture = new SaplIntegrationTestFixture("");
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks);
    }

    @Test
    void test_invalidPath3() {
        SaplTestFixture fixture = new SaplIntegrationTestFixture(null);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase);
    }

    @Test
    void test_invalidPath4() {
        SaplTestFixture fixture = new SaplIntegrationTestFixture(null);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks);
    }

    @Nested
    @DisplayName("PolicyPaths cases")
    class PolicyPathsTests {
        @Test
        void test_nullPDPConfigPath_usesDefaultCombiningAlgorithm() {
            var fixture = new SaplIntegrationTestFixture(null,
                    List.of("policiesIT/policy_A", "policiesIT/policy_B.sapl"));
            fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny()
                    .verify();
        }

        @Test
        void test_invalidPDPConfigPath_usesDefaultCombiningAlgorithm() {
            var fixture = new SaplIntegrationTestFixture("it/empty",
                    List.of("policiesIT/policy_A", "policiesIT/policy_B.sapl"));
            fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny()
                    .verify();
        }

        @Test
        void test_nullPDPConfigPath_usesGivenCombiningAlgorithm() {
            var fixture = new SaplIntegrationTestFixture(null,
                    List.of("policiesIT/policy_A", "policiesIT/policy_B.sapl"));
            fixture.withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES)
                    .constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit()
                    .verify();
        }

        @Test
        void test_invalidPDPConfigPath_usesGivenCombiningAlgorithm() {
            var fixture = new SaplIntegrationTestFixture("it/empty",
                    List.of("policiesIT/policy_A", "policiesIT/policy_B.sapl"));
            fixture.withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES)
                    .constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit()
                    .verify();
        }

        @Test
        void test_invalidPDPConfigPath_givenVariablesAndCombiningAlgorithmOverridesConfig() {
            var       fixture   = new SaplIntegrationTestFixture("it/empty",
                    List.of("it/variables/policy", "policiesIT/policy_A.sapl"));
            final var variables = Map.of("test", Val.of(mapper.createObjectNode().numberNode(1)));
            fixture.withPDPVariables(variables)
                    .withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES)
                    .constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny()
                    .verify();
        }

        @Test
        void test_validConfigPath_usesConfigDefinedCombiningAlgorithm() {
            var fixture = new SaplIntegrationTestFixture("policiesIT",
                    List.of("policiesIT/policy_A", "policiesIT/policy_B.sapl"));
            fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit()
                    .verify();
        }

        @Test
        void test_validConfigPath_givenCombiningAlgorithmOverridesConfig() {
            var fixture = new SaplIntegrationTestFixture("policiesIT",
                    List.of("policiesIT/policy_A", "policiesIT/policy_B.sapl"));
            fixture.withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES).constructTestCase()
                    .when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny().verify();
        }

        @Test
        void test_validConfigPath_givenVariablesOverridesConfig() {
            var       fixture   = new SaplIntegrationTestFixture("policiesIT",
                    List.of("it/variables/policy", "policiesIT/policy_A.sapl"));
            final var variables = Map.of("test", Val.of(mapper.createObjectNode().numberNode(1)));
            fixture.withPDPVariables(variables).constructTestCase()
                    .when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit().verify();
        }

        @Test
        void test_validConfigPath_givenVariablesAndCombiningAlgorithmOverridesConfig() {
            var       fixture   = new SaplIntegrationTestFixture("policiesIT",
                    List.of("it/variables/policy", "policiesIT/policy_A.sapl"));
            final var variables = Map.of("test", Val.of(mapper.createObjectNode().numberNode(1)));
            fixture.withPDPVariables(variables)
                    .withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES)
                    .constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny()
                    .verify();
        }

        @Nested
        @DisplayName("Error cases")
        class ErrorCases {
            @Test
            void test_nullPolicyPaths1() {
                var fixture = new SaplIntegrationTestFixture("path", null);
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase)
                        .withMessage("List of policies paths needs to contain at least 2 values.");
            }

            @Test
            void test_nullPolicyPaths2() {
                var fixture = new SaplIntegrationTestFixture("path", null);
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks)
                        .withMessage("List of policies paths needs to contain at least 2 values.");
            }

            @Test
            void test_emptyPolicyPaths1() {
                var fixture = new SaplIntegrationTestFixture("path", Collections.emptyList());
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase)
                        .withMessage("List of policies paths needs to contain at least 2 values.");
            }

            @Test
            void test_emptyPolicyPaths2() {
                var fixture = new SaplIntegrationTestFixture("path", Collections.emptyList());
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks)
                        .withMessage("List of policies paths needs to contain at least 2 values.");
            }

            @Test
            void test_singleValuePolicyPaths1() {
                var fixture = new SaplIntegrationTestFixture("path", List.of("singleValue"));
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase)
                        .withMessage("List of policies paths needs to contain at least 2 values.");
            }

            @Test
            void test_singleValuePolicyPaths2() {
                var fixture = new SaplIntegrationTestFixture("path", List.of("singleValue"));
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks)
                        .withMessage("List of policies paths needs to contain at least 2 values.");
            }
        }
    }

    @Nested
    @DisplayName("DocumentStrings cases")
    class DocumentStringsTests {

        private static final String policy_A = """
                policy "policy_A"
                deny
                    resource == "foo"
                where
                    "WILLI" == subject;""";

        private static final String policy_B = """
                policy "policy_B"
                permit
                    resource == "foo"
                where
                    "WILLI" == subject;""";

        private static final String policyWithVariables = """
                policy "policy read"
                permit
                	action == "read"
                where
                	test == 1;""";

        private static final String pdpConfig = """
                {
                	"algorithm": "PERMIT_OVERRIDES",
                	"variables": { "test": 1 }
                }""";

        @Test
        void test_usesConfigCombiningAlgorithm() {
            var fixture = new SaplIntegrationTestFixture(List.of(policy_A, policy_B), pdpConfig);
            fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit()
                    .verify();
        }

        @Test
        void test_usesGivenCombiningAlgorithm() {
            var fixture = new SaplIntegrationTestFixture(List.of(policy_A, policy_B), pdpConfig);
            fixture.withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES).constructTestCase()
                    .when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny().verify();
        }

        @Test
        void test_usesGivenCombiningAlgorithmAndConfigVariables() {
            var fixture = new SaplIntegrationTestFixture(List.of(policyWithVariables, policy_A), pdpConfig);
            fixture.withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES)
                    .constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit()
                    .verify();
        }

        @Test
        void test_usesGivenVariablesAndConfigCombiningAlgorithm() {
            var       fixture   = new SaplIntegrationTestFixture(List.of(policyWithVariables, policy_A), pdpConfig);
            final var variables = Map.of("test", Val.of(mapper.createObjectNode().numberNode(2)));
            fixture.withPDPVariables(variables).constructTestCase()
                    .when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny().verify();
        }

        @Test
        void test_givenVariablesAndCombiningAlgorithmOverridesConfig() {
            var       fixture   = new SaplIntegrationTestFixture(List.of(policyWithVariables, policy_A), pdpConfig);
            final var variables = Map.of("test", Val.of(2));
            fixture.withPDPVariables(variables)
                    .withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT)
                    .constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny()
                    .verify();
        }

        @Nested
        @DisplayName("Error cases")
        class ErrorCases {

            private static final String validPolicy = """
                    policy "policy read"
                    permit
                        action == "read\"""";

            @Test
            void test_nullDocumentStrings1() {
                var fixture = new SaplIntegrationTestFixture(null, "abc");
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase)
                        .withMessage("List input documents needs to contain at least 2 values.");
            }

            @Test
            void test_nullDocumentStrings2() {
                var fixture = new SaplIntegrationTestFixture(null, "abc");
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks)
                        .withMessage("List input documents needs to contain at least 2 values.");
            }

            @Test
            void test_emptyDocumentStrings1() {
                var fixture = new SaplIntegrationTestFixture(Collections.emptyList(), "abc");
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase)
                        .withMessage("List input documents needs to contain at least 2 values.");
            }

            @Test
            void test_emptyDocumentStrings2() {
                var fixture = new SaplIntegrationTestFixture(Collections.emptyList(), "abc");
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks)
                        .withMessage("List input documents needs to contain at least 2 values.");
            }

            @Test
            void test_SingleValueDocumentStrings1() {
                var fixture = new SaplIntegrationTestFixture(List.of("a"), "abc");
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase)
                        .withMessage("List input documents needs to contain at least 2 values.");
            }

            @Test
            void test_SingleValueDocumentStrings2() {
                var fixture = new SaplIntegrationTestFixture(List.of("a"), "abc");
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks)
                        .withMessage("List input documents needs to contain at least 2 values.");
            }

            @Test
            void test_nullPDPConfigValueDocumentStrings1() {
                var fixture = new SaplIntegrationTestFixture(List.of(validPolicy, validPolicy), null);
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase)
                        .withMessageContaining("Encountered policy name duplication");
            }

            @Test
            void test_nullPDPConfigDocumentStrings2() {
                var fixture = new SaplIntegrationTestFixture(List.of(validPolicy, validPolicy), null);
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks)
                        .withMessageContaining("Encountered policy name duplication");
            }

            @Test
            void test_emptyPDPConfigValueDocumentStrings1() {
                var fixture = new SaplIntegrationTestFixture(List.of(validPolicy, validPolicy), "");
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase)
                        .withMessageContaining("Encountered policy name duplication");
            }

            @Test
            void test_emptyPDPConfigDocumentStrings2() {
                var fixture = new SaplIntegrationTestFixture(List.of(validPolicy, validPolicy), "");
                assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks)
                        .withMessageContaining("Encountered policy name duplication");
            }
        }
    }
}
