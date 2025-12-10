/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.TraceLevel;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.test.mocking.attribute.MockingAttributeBroker;
import io.sapl.test.mocking.function.MockingFunctionBroker;
import io.sapl.test.mocking.function.models.FunctionParameters;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Simplified test builder for SAPL policy unit and integration tests.
 * <p>
 * All state is contained in this single class. Uses a PDP with
 * ONLY_ONE_APPLICABLE combining algorithm to evaluate single documents while
 * preserving policy SET internal combining algorithms.
 * <p>
 * Example usage:
 *
 * <pre>
 * var test = SaplTest.policy("policy \"test\" permit").mockFunction("time.now", Value.of("2025-01-15"));
 *
 * test.decide(AuthorizationSubscription.of("alice", "read", "doc")).expectNextMatches(AuthorizationDecision::isPermit)
 *         .verifyComplete();
 * </pre>
 *
 * For streaming attributes:
 *
 * <pre>
 * var test = SaplTest.policyResource("policies/streaming.sapl").mockStreamingAttribute("time.now")
 *         .mockFunctionSequence("time.secondOf", Value.of(1), Value.of(15));
 *
 * test.decide(subscription).then(() -> test.emit("time.now", Value.of(1))).expectNextMatches(isPermit())
 *         .then(() -> test.emit("time.now", Value.of(2))).expectNextMatches(isPermit()).thenCancel().verify();
 * </pre>
 */
public class SaplTest {

    private static final String PDP_ID      = "default";
    private static final String CONFIG_ID   = "test-config";
    private static final String POLICIES    = "policies/";
    private static final String SAPL_SUFFIX = ".sapl";

    private final String           policySource;
    private MockingFunctionBroker  functionBroker;
    private MockingAttributeBroker attributeBroker;
    private PolicyDecisionPoint    pdp;

    private SaplTest(String policySource) {
        this.policySource = policySource;
    }

    /**
     * Creates a test for inline policy source code.
     *
     * @param policySource the SAPL policy or policy set source code
     * @return a new SaplTest instance
     */
    public static SaplTest policy(String policySource) {
        return new SaplTest(policySource);
    }

    /**
     * Creates a test loading policy from classpath resource.
     * <p>
     * Searches in order: exact path, policies/ directory, with/without .sapl
     * extension.
     *
     * @param resourcePath path to the policy resource
     * @return a new SaplTest instance
     */
    public static SaplTest policyResource(String resourcePath) {
        return new SaplTest(loadFromClasspath(resourcePath));
    }

    // ========== SETUP: Libraries and PIPs ==========
    // Note: Default function libraries and PIPs are already loaded by the builder.
    // These methods add additional custom ones.

    /**
     * Registers a static function library class.
     */
    public SaplTest withFunctionLibrary(Class<?> libraryClass) {
        // Function libraries are loaded on the base broker which the mocking broker
        // delegates to
        // For now, additional libraries need to be registered before the PDP is built
        throw new UnsupportedOperationException(
                "Additional function libraries must be registered via PolicyDecisionPointBuilder");
    }

    /**
     * Registers an instantiated function library.
     */
    public SaplTest withFunctionLibrary(Object libraryInstance) {
        throw new UnsupportedOperationException(
                "Additional function libraries must be registered via PolicyDecisionPointBuilder");
    }

    /**
     * Registers a Policy Information Point.
     */
    public SaplTest withPIP(Object pip) {
        throw new UnsupportedOperationException("Additional PIPs must be registered via PolicyDecisionPointBuilder");
    }

    /**
     * Registers a Policy Information Point by class (must have no-arg
     * constructor).
     */
    public SaplTest withPIP(Class<?> pipClass) {
        throw new UnsupportedOperationException("Additional PIPs must be registered via PolicyDecisionPointBuilder");
    }

    // ========== MOCKING: Functions ==========

    /**
     * Mocks a function to always return the specified value.
     *
     * @param functionName fully qualified name (e.g., "time.dayOfWeek")
     * @param returnValue the value to return
     */
    public SaplTest mockFunction(String functionName, Value returnValue) {
        ensureBrokersInitialized();
        functionBroker.mockFunctionAlwaysReturns(functionName, returnValue);
        return this;
    }

    /**
     * Mocks a function to return values from a sequence. Each call consumes the
     * next value.
     *
     * @param functionName fully qualified name
     * @param values sequence of values to return
     */
    public SaplTest mockFunctionSequence(String functionName, Value... values) {
        ensureBrokersInitialized();
        functionBroker.mockFunctionReturnsSequence(functionName, values);
        return this;
    }

    /**
     * Mocks a function for specific parameter values.
     *
     * @param functionName fully qualified name
     * @param parameters the parameter matchers
     * @param returnValue value to return when parameters match
     */
    public SaplTest mockFunction(String functionName, FunctionParameters parameters, Value returnValue) {
        ensureBrokersInitialized();
        functionBroker.mockFunctionForParameterMatchers(functionName, parameters, returnValue);
        return this;
    }

    // ========== MOCKING: Attributes ==========

    /**
     * Mocks an attribute to always return the specified value.
     *
     * @param attributeName fully qualified name (e.g., "time.now")
     * @param returnValue the value to emit
     */
    public SaplTest mockAttribute(String attributeName, Value returnValue) {
        ensureBrokersInitialized();
        attributeBroker.mockAttributeAlwaysReturns(attributeName, returnValue);
        return this;
    }

    /**
     * Mocks a streaming attribute that you control via
     * {@link #emit(String, Value)}.
     * <p>
     * Call this for attributes that need to emit multiple values over time during
     * the test.
     *
     * @param attributeName fully qualified name
     */
    public SaplTest mockStreamingAttribute(String attributeName) {
        ensureBrokersInitialized();
        attributeBroker.markAttributeMock(attributeName);
        return this;
    }

    /**
     * Emits a value to a streaming attribute mock.
     * <p>
     * Use this in StepVerifier's {@code .then()} callbacks to control when
     * attribute values arrive during the test.
     *
     * @param attributeName the attribute to emit to
     * @param value the value to emit
     */
    public void emit(String attributeName, Value value) {
        attributeBroker.emitToAttribute(attributeName, value);
    }

    // ========== EVALUATION ==========

    /**
     * Creates a StepVerifier for testing the decision stream.
     * <p>
     * The PDP is created on first call and reused. Use StepVerifier's fluent API
     * to assert on decisions.
     *
     * @param subscription the authorization subscription to evaluate
     * @return StepVerifier.FirstStep for building assertions
     */
    public StepVerifier.FirstStep<AuthorizationDecision> decide(AuthorizationSubscription subscription) {
        return StepVerifier.create(decisions(subscription));
    }

    /**
     * Returns the raw decision flux for custom processing.
     * <p>
     * Most tests should use {@link #decide(AuthorizationSubscription)} instead.
     *
     * @param subscription the authorization subscription
     * @return flux of authorization decisions
     */
    public Flux<AuthorizationDecision> decisions(AuthorizationSubscription subscription) {
        ensurePdpInitialized();
        return pdp.decide(subscription);
    }

    private void ensureBrokersInitialized() {
        if (functionBroker == null) {
            val baseComponents = PolicyDecisionPointBuilder.withDefaults().build();
            functionBroker  = new MockingFunctionBroker(baseComponents.functionBroker());
            attributeBroker = new MockingAttributeBroker(baseComponents.attributeBroker());
        }
    }

    private void ensurePdpInitialized() {
        if (pdp == null) {
            ensureBrokersInitialized();
            val configuration = new PDPConfiguration(PDP_ID, CONFIG_ID, CombiningAlgorithm.ONLY_ONE_APPLICABLE,
                    TraceLevel.STANDARD, List.of(policySource), Map.of());

            // Build PDP with mocking brokers so mocks are actually used
            val pdpComponents = PolicyDecisionPointBuilder.withoutDefaults().withFunctionBroker(functionBroker)
                    .withAttributeBroker(attributeBroker).withConfiguration(configuration).build();
            pdp = pdpComponents.pdp();
        }
    }

    // ========== UTILITIES ==========

    private static String loadFromClasspath(String resourcePath) {
        var paths = List.of(resourcePath, resourcePath + SAPL_SUFFIX, POLICIES + resourcePath,
                POLICIES + resourcePath + SAPL_SUFFIX);

        for (String path : paths) {
            try (InputStream stream = SaplTest.class.getClassLoader().getResourceAsStream(path)) {
                if (stream != null) {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                // Try next path
            }
        }
        throw new SaplTestException("Policy not found on classpath: " + resourcePath);
    }

}
