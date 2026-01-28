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
package io.sapl.playground.examples;

import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import io.sapl.functions.libraries.ArrayFunctionLibrary;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.functions.libraries.GraphFunctionLibrary;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import io.sapl.api.model.Value;
import io.sapl.test.SaplTestFixture;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.test.Matchers.args;
import static io.sapl.test.Matchers.isDecision;
import static io.sapl.test.Matchers.isNotApplicable;
import static io.sapl.test.Matchers.isPermit;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for the playground examples to verify they produce expected results
 * with the new compiler implementation.
 */
class ExamplesCollectionTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("deterministicExamples")
    @DisplayName("Deterministic examples produce expected decisions")
    void deterministicExampleProducesExpectedDecision(String slug, String displayName, Example example,
            Decision expectedDecision) throws Exception {
        val subscription = parseSubscription(example.subscription());
        val fixture      = SaplTestFixture.createIntegrationTest().withCombiningAlgorithm(example.combiningAlgorithm())
                .withCoverageFileWriteDisabled().withFunctionLibrary(StandardFunctionLibrary.class)
                .withFunctionLibrary(ArrayFunctionLibrary.class).withFunctionLibrary(FilterFunctionLibrary.class)
                .withFunctionLibrary(GraphFunctionLibrary.class).withFunctionLibrary(GeographicFunctionLibrary.class);

        for (var policy : example.policies()) {
            fixture.withPolicy(policy);
        }

        registerVariables(fixture, example.variables());

        fixture.whenDecide(subscription).expectDecisionMatches(isDecision(expectedDecision)).verify();
    }

    private static Stream<Arguments> deterministicExamples() {
        return Stream.of(
                // Documentation examples
                arguments("at-a-glance", "Introduction Policy", ExamplesCollection.DOCUMENTATION_AT_A_GLANCE,
                        Decision.PERMIT),
                arguments("deny-overrides-demo", "Deny Overrides Algorithm",
                        ExamplesCollection.DOCUMENTATION_DENY_OVERRIDES, Decision.DENY),

                // Medical examples
                arguments("emergency-override", "Emergency Access Override",
                        ExamplesCollection.MEDICAL_EMERGENCY_OVERRIDE, Decision.PERMIT),

                // Geographic examples
                arguments("geo-permit-inside-perimeter", "Geo-fence: inside perimeter",
                        ExamplesCollection.GEOGRAPHIC_INSIDE_PERIMETER, Decision.PERMIT),
                arguments("geo-permit-near-facility", "Proximity: geodesic distance",
                        ExamplesCollection.GEOGRAPHIC_NEAR_FACILITY, Decision.PERMIT),
                arguments("geo-deny-intersects-restricted", "Deny intersects restricted",
                        ExamplesCollection.GEOGRAPHIC_DENY_INTERSECTS_RESTRICTED, Decision.DENY),
                arguments("geo-permit-waypoints-subset", "Waypoints subset",
                        ExamplesCollection.GEOGRAPHIC_WAYPOINTS_SUBSET, Decision.PERMIT),
                arguments("geo-permit-buffer-touch", "Buffer touch", ExamplesCollection.GEOGRAPHIC_BUFFER_TOUCH,
                        Decision.PERMIT),
                arguments("geo-permit-wkt-inside-zone", "WKT inside zone",
                        ExamplesCollection.GEOGRAPHIC_WKT_INSIDE_ZONE, Decision.PERMIT),

                // Access Control examples
                arguments("bell-lapadula-basic", "Bell-LaPadula Basic",
                        ExamplesCollection.ACCESS_CONTROL_BELL_LAPADULA_BASIC, Decision.PERMIT),
                arguments("bell-lapadula-compartments", "Bell-LaPadula Compartments",
                        ExamplesCollection.ACCESS_CONTROL_BELL_LAPADULA_COMPARTMENTS, Decision.PERMIT),
                arguments("biba-integrity", "Biba Integrity", ExamplesCollection.ACCESS_CONTROL_BIBA_INTEGRITY,
                        Decision.PERMIT),
                arguments("role-based-access-control", "RBAC", ExamplesCollection.ACCESS_CONTROL_RBAC, Decision.PERMIT),
                arguments("hierarchical-role-based-access-control", "Hierarchical RBAC",
                        ExamplesCollection.ACCESS_CONTROL_HIERARCHICAL_RBAC, Decision.PERMIT),

                // Brewer-Nash (Chinese Wall) examples - use key projection on arrays
                arguments("brewer-nash-financial", "Brewer-Nash Financial",
                        ExamplesCollection.ACCESS_CONTROL_BREWER_NASH_FINANCIAL, Decision.DENY),
                arguments("brewer-nash-consulting", "Brewer-Nash Consulting",
                        ExamplesCollection.ACCESS_CONTROL_BREWER_NASH_CONSULTING, Decision.DENY));
    }

    // ==========================================================================
    // Time-dependent examples (require mocked time attributes)
    // ==========================================================================

    @Test
    @DisplayName("Default example: PERMIT during business hours (mocked time)")
    void defaultExamplePermitsDuringBusinessHours() throws Exception {
        val example      = ExamplesCollection.DEFAULT_SETTINGS;
        val subscription = parseSubscription(example.subscription());
        val fixture      = SaplTestFixture.createSingleTest().withCoverageFileWriteDisabled()
                .withFunctionLibrary(TemporalFunctionLibrary.class)
                .givenEnvironmentAttribute("timeMock", "time.now", args(), Value.of("2025-01-06T10:00:00Z"))
                .withPolicy(example.policies().getFirst());

        registerVariables(fixture, example.variables());

        fixture.whenDecide(subscription).expectDecisionMatches(isPermit()).verify();
    }

    @Test
    @DisplayName("Default example: NOT_APPLICABLE outside business hours (mocked time)")
    void defaultExampleNotApplicableOutsideBusinessHours() throws Exception {
        val example      = ExamplesCollection.DEFAULT_SETTINGS;
        val subscription = parseSubscription(example.subscription());
        val fixture      = SaplTestFixture.createSingleTest().withCoverageFileWriteDisabled()
                .withFunctionLibrary(TemporalFunctionLibrary.class)
                .givenEnvironmentAttribute("timeMock", "time.now", args(), Value.of("2025-01-06T20:00:00Z"))
                .withPolicy(example.policies().getFirst());

        registerVariables(fixture, example.variables());

        fixture.whenDecide(subscription).expectDecisionMatches(isNotApplicable()).verify();
    }

    private AuthorizationSubscription parseSubscription(String subscriptionJson) throws Exception {
        val node = MAPPER.readTree(subscriptionJson);
        return AuthorizationSubscription.of(node.get("subject"), node.get("action"), node.get("resource"),
                node.has("environment") && !node.get("environment").isNull() ? node.get("environment") : null);
    }

    private void registerVariables(SaplTestFixture fixture, String variablesJson) throws Exception {
        val variablesNode = MAPPER.readTree(variablesJson);
        if (variablesNode.isObject()) {
            for (var entry : variablesNode.properties()) {
                fixture.givenVariable(entry.getKey(), ValueJsonMarshaller.fromJsonNode(entry.getValue()));
            }
        }
    }
}
