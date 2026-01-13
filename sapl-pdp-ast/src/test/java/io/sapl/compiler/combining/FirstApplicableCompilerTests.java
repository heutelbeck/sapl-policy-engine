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
package io.sapl.compiler.combining;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.pdp.CompiledPolicySet;
import io.sapl.compiler.pdp.PDPCompiler;
import io.sapl.compiler.policyset.PolicySetBody;
import io.sapl.compiler.policyset.PolicySetDecision;
import io.sapl.compiler.policyset.PurePolicySetBody;
import io.sapl.compiler.policyset.StreamPolicySetBody;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FirstApplicableCompiler covering short-circuit optimization,
 * pure evaluation, and streaming evaluation paths.
 */
@DisplayName("FirstApplicableCompiler")
class FirstApplicableCompilerTests {

    record PureTestCase(
            String description,
            String policySet,
            String subscription,
            Decision expectedDecision,
            Class<? extends PolicySetBody> expectedStratum) {

        @Override
        public @NonNull String toString() {
            return description;
        }
    }

    record StreamTestCase(
            String description,
            String policySet,
            String subscription,
            Map<String, Value[]> attributes,
            Decision expectedDecision) {

        @Override
        public @NonNull String toString() {
            return description;
        }
    }

    static Stream<PureTestCase> pureTestCases() {
        return Stream.of(new PureTestCase("short-circuit: first policy permits", """
                set "guild-access"
                first-applicable

                policy "always-permit"
                permit

                policy "fallback-deny"
                deny
                """, """
                {
                    "subject": "alice",
                    "action": "read",
                    "resource": "data"
                }
                """, Decision.PERMIT, PolicySetDecision.class),

                new PureTestCase("short-circuit: first policy body NOT_APPLICABLE, continues to next", """
                        set "test"
                        first-applicable

                        policy "body-not-applicable"
                        permit
                        where
                          false;

                        policy "fallback-deny"
                        deny
                        """, """
                        {
                            "subject": "alice",
                            "action": "read",
                            "resource": "data"
                        }
                        """, Decision.DENY, PolicySetDecision.class),

                new PureTestCase("first policy target false, second applies", """
                        set "guild-access"
                        first-applicable

                        policy "never-matches"
                        permit false

                        policy "always-deny"
                        deny
                        """, """
                        {
                            "subject": "alice",
                            "action": "read",
                            "resource": "data"
                        }
                        """, Decision.DENY, PurePolicySetBody.class),

                new PureTestCase("runtime target: first policy matches", """
                        set "watch-duties"
                        first-applicable

                        policy "captain-only"
                        permit subject == "Vimes"

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Vimes",
                            "action": "patrol",
                            "resource": "city"
                        }
                        """, Decision.PERMIT, PurePolicySetBody.class),

                new PureTestCase("runtime target: second policy matches", """
                        set "watch-duties"
                        first-applicable

                        policy "captain-only"
                        permit subject == "Vimes"

                        policy "sergeant-fallback"
                        permit subject == "Colon"

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Colon",
                            "action": "patrol",
                            "resource": "city"
                        }
                        """, Decision.PERMIT, PurePolicySetBody.class),

                new PureTestCase("runtime target: no policy matches, falls through to default", """
                        set "watch-duties"
                        first-applicable

                        policy "captain-only"
                        permit subject == "Vimes"

                        policy "sergeant-fallback"
                        permit subject == "Colon"

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Nobby",
                            "action": "patrol",
                            "resource": "city"
                        }
                        """, Decision.DENY, PurePolicySetBody.class),

                new PureTestCase("body condition matches", """
                        set "library-access"
                        first-applicable

                        policy "wizards-reading"
                        permit subject == "Rincewind"
                        where
                          action == "read";

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Rincewind",
                            "action": "read",
                            "resource": "book"
                        }
                        """, Decision.PERMIT, PurePolicySetBody.class),

                new PureTestCase("target matches but body fails, continues to next policy", """
                        set "library-access"
                        first-applicable

                        policy "wizards-reading"
                        permit subject == "Rincewind"
                        where
                          action == "read";

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Rincewind",
                            "action": "run",
                            "resource": "away"
                        }
                        """, Decision.DENY, PurePolicySetBody.class));
    }

    // --- Stream evaluation test cases ---

    static Stream<StreamTestCase> streamTestCases() {
        return Stream.of(new StreamTestCase("attribute in body permits", """
                set "time-based-access"
                first-applicable

                policy "time-check"
                permit
                where
                  <test.attr>;

                policy "fallback"
                deny
                """, """
                {
                    "subject": "alice",
                    "action": "read",
                    "resource": "doc"
                }
                """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.PERMIT),

                new StreamTestCase("target matches but body fails, continues to next policy", """
                        set "library-access"
                        first-applicable

                        policy "wizards-reading"
                        permit subject == "Rincewind"
                        where
                          <test.action> == "read";

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Rincewind",
                            "action": "run",
                            "resource": "away"
                        }
                        """, Map.of("test.action", new Value[] { Value.of("run") }), Decision.DENY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pureTestCases")
    @DisplayName("Pure evaluation")
    void pureEvaluation(PureTestCase testCase) {
        val compiled = compilePolicySet(testCase.policySet());

        assertThat(compiled.policies()).as("Expected stratum").isInstanceOf(testCase.expectedStratum());

        val ctx    = subscriptionContext(testCase.subscription());
        val result = evaluatePolicySet(compiled, ctx);

        assertThat(result.decision()).isEqualTo(testCase.expectedDecision());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("streamTestCases")
    @DisplayName("Stream evaluation")
    void streamEvaluation(StreamTestCase testCase) {
        val attrBroker = attributeBroker(testCase.attributes());
        val compiled   = compilePolicySet(testCase.policySet(), attrBroker);

        assertThat(compiled.policies()).as("Expected stream stratum").isInstanceOf(StreamPolicySetBody.class);

        val streamBody   = (StreamPolicySetBody) compiled.policies();
        val subscription = parseSubscription(testCase.subscription());
        val ctx          = evaluationContext(subscription, attrBroker);

        StepVerifier.create(streamBody.stream().contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(testCase.expectedDecision()))
                .verifyComplete();
    }

    // --- Helper methods ---

    private static CompiledPolicySet compilePolicySet(String source) {
        val document = document(source);
        val compiled = PDPCompiler.compileDocument(document, compilationContext());
        assertThat(compiled).isInstanceOf(CompiledPolicySet.class);
        return (CompiledPolicySet) compiled;
    }

    private static CompiledPolicySet compilePolicySet(String source, AttributeBroker attrBroker) {
        val document = document(source);
        val compiled = PDPCompiler.compileDocument(document, compilationContext(attrBroker));
        assertThat(compiled).isInstanceOf(CompiledPolicySet.class);
        return (CompiledPolicySet) compiled;
    }

    private static PolicySetDecision evaluatePolicySet(CompiledPolicySet compiled, EvaluationContext ctx) {
        val body = compiled.policies();
        return switch (body) {
        case PolicySetDecision decision -> decision;
        case PurePolicySetBody pure     -> pure.evaluateBody(ctx);
        default                         -> throw new IllegalStateException("Unexpected body type: " + body.getClass());
        };
    }

}
