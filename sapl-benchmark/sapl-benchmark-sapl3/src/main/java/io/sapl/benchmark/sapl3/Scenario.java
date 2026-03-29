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
package io.sapl.benchmark.sapl3;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.PolicyDecisionPointFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Self-contained benchmark scenarios for SAPL 3.0. Uses {@code where} keyword
 * syntax and the SAPL 3 {@code PolicyDecisionPointFactory} API.
 */
enum Scenario {

    RBAC(subscription("""
            {"subject":{"username":"bob","role":"test"},"action":"write","resource":{"type":"foo123"}}
            """), AuthorizationDecision.DENY) {

        @Override
        PolicyDecisionPoint buildPdp() throws IOException, io.sapl.interpreter.InitializationException {
            var variables = new HashMap<String, Val>();
            variables.put("permissions", Val.of(MAPPER.readTree("""
                    {
                      "dev":  [{"type":"foo123","action":"write"},{"type":"foo123","action":"read"}],
                      "test": [{"type":"foo123","action":"read"}]
                    }
                    """)));
            return PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(List.of(RBAC_POLICY),
                    PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT, variables);
        }

    };

    static final String AVAILABLE_NAMES = Arrays.stream(values())
            .map(scenario -> scenario.name().toLowerCase().replace('_', '-')).collect(Collectors.joining(", "));

    private static final String ERROR_UNKNOWN_SCENARIO = "Unknown scenario: %s. Available: %s.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String RBAC_POLICY = """
            policy "RBAC"
            permit
            where
                { "type" : resource.type, "action": action } in permissions[(subject.role)];
            """;

    private final AuthorizationSubscription subscription;
    private final AuthorizationDecision     expectedDecision;

    Scenario(AuthorizationSubscription subscription, AuthorizationDecision expectedDecision) {
        this.subscription     = subscription;
        this.expectedDecision = expectedDecision;
    }

    /**
     * Builds a SAPL 3 embedded PDP configured for this scenario.
     *
     * @return the PDP instance (caller should close if AutoCloseable)
     * @throws IOException if PDP initialization fails
     */
    abstract PolicyDecisionPoint buildPdp() throws IOException, io.sapl.interpreter.InitializationException;

    /**
     * @return the authorization subscription for this scenario
     */
    AuthorizationSubscription subscription() {
        return subscription;
    }

    /**
     * @return the expected authorization decision for sanity checking
     */
    AuthorizationDecision expectedDecision() {
        return expectedDecision;
    }

    /**
     * Resolves a CLI name like "rbac" to the matching enum value.
     *
     * @param name the CLI scenario name (case-insensitive, hyphens or
     * underscores)
     * @return the matching scenario
     * @throws IllegalArgumentException if no scenario matches
     */
    static Scenario fromName(String name) {
        try {
            return valueOf(name.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(ERROR_UNKNOWN_SCENARIO.formatted(name, AVAILABLE_NAMES), exception);
        }
    }

    private static AuthorizationSubscription subscription(String json) {
        try {
            var node = new ObjectMapper().readTree(json);
            return AuthorizationSubscription.of(node.get("subject"), node.get("action"), node.get("resource"));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse subscription: " + exception.getMessage(), exception);
        }
    }

}
