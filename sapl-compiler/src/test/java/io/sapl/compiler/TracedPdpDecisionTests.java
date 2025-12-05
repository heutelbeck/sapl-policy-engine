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
package io.sapl.compiler;

import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.internal.TraceFields;
import io.sapl.api.pdp.internal.TracedDecision;
import io.sapl.api.pdp.internal.TracedPolicySetDecision;
import io.sapl.api.prp.MatchingDocuments;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.pdp.CompiledPDPConfiguration;
import io.sapl.pdp.DynamicPolicyDecisionPoint;
import io.sapl.pdp.IdFactory;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.api.model.ValueJsonMarshaller.toPrettyString;
import static io.sapl.api.pdp.CombiningAlgorithm.*;
import static io.sapl.api.pdp.internal.TracedPdpDecision.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for TracedPdpDecision emission from the Policy Decision Point.
 * <p>
 * Validates that the PDP produces correct traced output structure with PDP
 * metadata, algorithm, and nested document
 * traces (policies and policy sets).
 */
@DisplayName("TracedPdpDecision")
class TracedPdpDecisionTests {

    private static final DefaultSAPLInterpreter PARSER = new DefaultSAPLInterpreter();
    private static final boolean                DEBUG  = true;

    private static final String TEST_PDP_ID          = "arkham-pdp";
    private static final String TEST_CONFIG_ID       = "necronomicon-config";
    private static final String TEST_SUBSCRIPTION_ID = "eldritch-request-001";

    private CompilationContext context;
    private IdFactory          idFactory;

    @BeforeEach
    @SneakyThrows
    void setup() {
        context   = PolicyDecisionPointBuilder.withoutDefaults().withFunctionLibrary(FilterFunctionLibrary.class)
                .build().compilationContext();
        idFactory = () -> TEST_SUBSCRIPTION_ID;
    }

    @Nested
    @DisplayName("PDP Metadata")
    class PdpMetadataTests {

        @Test
        @DisplayName("trace contains all PDP identifiers")
        void whenPdpDecision_thenTraceContainsPdpMetadata() {
            val subscription = subscriptionOf("investigator", "read", "forbidden-tome");
            val traced       = evaluateWithSinglePolicy("""
                    policy "arkham-archives-access" permit
                    """, subscription);

            printDecision("PDP metadata", traced);

            val trace = getTrace(traced);
            assertThat(trace.get(TraceFields.PDP_ID)).isEqualTo(Value.of(TEST_PDP_ID));
            assertThat(trace.get(TraceFields.CONFIGURATION_ID)).isEqualTo(Value.of(TEST_CONFIG_ID));
            assertThat(trace.get(TraceFields.SUBSCRIPTION_ID)).isEqualTo(Value.of(TEST_SUBSCRIPTION_ID));
        }

        @Test
        @DisplayName("trace contains subscription details")
        void whenPdpDecision_thenTraceContainsSubscription() {
            val subscription = subscriptionOf("cultist", "summon", "shoggoth");
            val traced       = evaluateWithSinglePolicy("""
                    policy "summoning-permit" deny
                    """, subscription);

            printDecision("subscription in trace", traced);

            val trace = getTrace(traced);
            val sub   = (ObjectValue) trace.get(TraceFields.SUBSCRIPTION);
            assertThat(sub.get("subject")).isEqualTo(Value.of("cultist"));
            assertThat(sub.get("action")).isEqualTo(Value.of("summon"));
            assertThat(sub.get("resource")).isEqualTo(Value.of("shoggoth"));
        }

        @Test
        @DisplayName("trace contains timestamp")
        void whenPdpDecision_thenTraceContainsTimestamp() {
            val subscription = subscriptionOf("dreamer", "enter", "dreamlands");
            val traced       = evaluateWithSinglePolicy("""
                    policy "dreamlands-entry" permit
                    """, subscription);

            printDecision("timestamp in trace", traced);

            val trace     = getTrace(traced);
            val timestamp = trace.get(TraceFields.TIMESTAMP);
            assertThat(timestamp).isInstanceOf(Value.class);
            assertThat(timestamp).isNotEqualTo(Value.UNDEFINED);
        }

        @Test
        @DisplayName("trace contains algorithm name in SAPL syntax")
        void whenPdpDecision_thenTraceContainsAlgorithmName() {
            val subscription = subscriptionOf("researcher", "access", "restricted-section");
            val traced       = evaluateWithSinglePolicy("""
                    policy "restricted-access" permit
                    """, subscription);

            printDecision("algorithm name", traced);

            assertThat(getAlgorithm(traced)).isEqualTo("deny-overrides");
        }
    }

    @Nested
    @DisplayName("Single Policy Documents")
    class SinglePolicyDocumentTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("singlePolicyCases")
        @DisplayName("produces correct traced decision")
        void whenSinglePolicy_thenCorrectTracedDecision(String description, String policy,
                AuthorizationSubscription subscription, Decision expectedDecision) {

            val traced = evaluateWithSinglePolicy(policy, subscription);

            printDecision(description, traced);

            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
            assertThat(getDocuments(traced)).hasSize(1);

            val document = (ObjectValue) getDocuments(traced).getFirst();
            assertThat(document.get(TraceFields.TYPE)).isEqualTo(Value.of(TraceFields.TYPE_POLICY));
        }

        static Stream<Arguments> singlePolicyCases() {
            return Stream.of(arguments("simple permit policy", """
                    policy "elder-sign-protection" permit
                    """, subscriptionOf("ward-keeper", "activate", "elder-sign"), Decision.PERMIT),

                    arguments("simple deny policy", """
                            policy "gate-sealed" deny
                            """, subscriptionOf("cultist", "open", "yog-sothoth-gate"), Decision.DENY),

                    arguments("conditional permit", """
                            policy "faculty-only" permit where subject == "professor";
                            """, subscriptionOf("professor", "borrow", "rare-manuscript"), Decision.PERMIT),

                    arguments("conditional not-applicable", """
                            policy "faculty-only" permit where subject == "professor";
                            """, subscriptionOf("student", "borrow", "rare-manuscript"), Decision.NOT_APPLICABLE));
        }

        @Test
        @DisplayName("document trace contains policy name and entitlement")
        void whenSinglePolicy_thenDocumentTraceHasPolicyDetails() {
            val subscription = subscriptionOf("librarian", "catalog", "forbidden-tome");
            val traced       = evaluateWithSinglePolicy("""
                    policy "librarian-duties" permit
                    """, subscription);

            printDecision("policy details in document", traced);

            val document = (ObjectValue) getDocuments(traced).getFirst();
            assertThat(document.get(TraceFields.NAME)).isEqualTo(Value.of("librarian-duties"));
            assertThat(document.get(TraceFields.ENTITLEMENT)).isEqualTo(Value.of("PERMIT"));
            assertThat(document.get(TraceFields.DECISION)).isEqualTo(Value.of("PERMIT"));
        }
    }

    @Nested
    @DisplayName("Policy Set Documents")
    class PolicySetDocumentTests {

        @Test
        @DisplayName("policy set document is marked as set type")
        void whenPolicySet_thenDocumentIsSetType() {
            val subscription = subscriptionOf("archivist", "access", "deep-archives");
            val traced       = evaluateWithSinglePolicy("""
                    set "deep-archive-access" deny-overrides
                    policy "archivist-permit" permit
                    policy "public-deny" deny where subject == "outsider";
                    """, subscription);

            printDecision("policy set document", traced);

            val document = (ObjectValue) getDocuments(traced).getFirst();
            assertThat(TracedPolicySetDecision.isPolicySet(document)).isTrue();
            assertThat(document.get(TraceFields.TYPE)).isEqualTo(Value.of(TraceFields.TYPE_SET));
        }

        @Test
        @DisplayName("policy set contains nested policy traces")
        void whenPolicySet_thenContainsNestedPolicies() {
            val subscription = subscriptionOf("researcher", "read", "elder-scrolls");
            val traced       = evaluateWithSinglePolicy("""
                    set "elder-scroll-access" deny-overrides
                    policy "researcher-permit" permit
                    policy "sanity-check" deny where subject.sanity < 10;
                    """, subscription);

            printDecision("nested policies in set", traced);

            val document = (ObjectValue) getDocuments(traced).getFirst();
            val policies = TracedPolicySetDecision.getPolicies(document);
            assertThat(policies).hasSize(2);

            val firstPolicy = (ObjectValue) policies.get(0);
            assertThat(firstPolicy.get(TraceFields.NAME)).isEqualTo(Value.of("researcher-permit"));
        }
    }

    @Nested
    @DisplayName("Multiple Documents")
    class MultipleDocumentTests {

        @Test
        @DisplayName("multiple matching policies are all traced")
        void whenMultiplePolicies_thenAllTraced() {
            val subscription = subscriptionOf("initiate", "enter", "ritual-chamber");
            val traced       = evaluateWithPolicies(List.of("""
                    policy "initiate-access" permit
                    """, """
                    policy "ritual-rules" permit
                    obligation "wear-robes"
                    """), subscription, DENY_OVERRIDES);

            printDecision("multiple policies", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getDocuments(traced)).hasSize(2);
        }

        @Test
        @DisplayName("mixed policies and policy sets are traced")
        void whenMixedDocuments_thenAllTraced() {
            val subscription = subscriptionOf("high-priest", "access", "inner-sanctum");
            val traced       = evaluateWithPolicies(List.of("""
                    policy "high-priest-entry" permit
                    """, """
                    set "sanctum-rules" deny-overrides
                    policy "ritual-purity" permit where subject.pure == true;
                    policy "contamination-block" deny where subject.corrupted == true;
                    """), subscription, DENY_OVERRIDES);

            printDecision("mixed documents", traced);

            val documents = getDocuments(traced);
            assertThat(documents).hasSize(2);

            val policy = (ObjectValue) documents.get(0);
            assertThat(policy.get(TraceFields.TYPE)).isEqualTo(Value.of(TraceFields.TYPE_POLICY));

            val policySet = (ObjectValue) documents.get(1);
            assertThat(TracedPolicySetDecision.isPolicySet(policySet)).isTrue();
        }
    }

    @Nested
    @DisplayName("Combining Algorithms at PDP Level")
    class CombiningAlgorithmTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("algorithmCases")
        @DisplayName("algorithm produces correct combined decision")
        void whenAlgorithmApplied_thenCorrectDecision(String description, List<String> documents,
                io.sapl.api.pdp.CombiningAlgorithm algorithm, Decision expectedDecision) {

            val subscription = subscriptionOf("test-subject", "test-action", "test-resource");
            val traced       = evaluateWithPolicies(documents, subscription, algorithm);

            printDecision(description, traced);

            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
            assertThat(getAlgorithm(traced)).isEqualTo(toSaplSyntax(algorithm));
        }

        static Stream<Arguments> algorithmCases() {
            return Stream.of(
                    arguments("deny-overrides: deny wins", List.of("policy \"p1\" permit", "policy \"p2\" deny"),
                            DENY_OVERRIDES, Decision.DENY),

                    arguments("permit-overrides: permit wins", List.of("policy \"p1\" deny", "policy \"p2\" permit"),
                            PERMIT_OVERRIDES, Decision.PERMIT),

                    arguments("deny-unless-permit: permit present", List.of("policy \"p1\" permit"), DENY_UNLESS_PERMIT,
                            Decision.PERMIT),

                    arguments("deny-unless-permit: no permit", List.of("policy \"p1\" permit where false;"),
                            DENY_UNLESS_PERMIT, Decision.DENY),

                    arguments("permit-unless-deny: deny present", List.of("policy \"p1\" deny"), PERMIT_UNLESS_DENY,
                            Decision.DENY),

                    arguments("permit-unless-deny: no deny", List.of("policy \"p1\" deny where false;"),
                            PERMIT_UNLESS_DENY, Decision.PERMIT),

                    arguments("only-one-applicable: single match",
                            List.of("policy \"p1\" permit where subject == \"test-subject\";",
                                    "policy \"p2\" deny where subject == \"other\";"),
                            ONLY_ONE_APPLICABLE, Decision.PERMIT));
        }
    }

    @Nested
    @DisplayName("Constraint Handling")
    class ConstraintHandlingTests {

        @Test
        @DisplayName("obligations are merged at PDP level")
        void whenMultiplePoliciesWithObligations_thenMerged() {
            val subscription = subscriptionOf("cultist", "perform", "ritual");
            val traced       = evaluateWithPolicies(List.of("""
                    policy "ritual-preparation" permit
                    obligation "light-candles"
                    """, """
                    policy "ritual-safety" permit
                    obligation "draw-protective-circle"
                    """), subscription, DENY_OVERRIDES);

            printDecision("merged obligations", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getObligations(traced)).hasSize(2).contains(Value.of("light-candles"),
                    Value.of("draw-protective-circle"));
        }

        @Test
        @DisplayName("advice is merged at PDP level")
        void whenMultiplePoliciesWithAdvice_thenMerged() {
            val subscription = subscriptionOf("investigator", "read", "necronomicon");
            val traced       = evaluateWithPolicies(List.of("""
                    policy "reading-permit" permit
                    advice "maintain-sanity"
                    """, """
                    policy "research-permit" permit
                    advice "take-notes"
                    """), subscription, DENY_OVERRIDES);

            printDecision("merged advice", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getAdvice(traced)).hasSize(2).contains(Value.of("maintain-sanity"), Value.of("take-notes"));
        }

        @Test
        @DisplayName("resource transformation is preserved")
        void whenPolicyTransformsResource_thenPreserved() {
            val subscription = subscriptionOf("reader", "view", "secret-text");
            val traced       = evaluateWithSinglePolicy("""
                    policy "redacted-view" permit
                    transform { "content": "REDACTED" }
                    """, subscription);

            printDecision("resource transformation", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getResource(traced)).isEqualTo(json("{\"content\": \"REDACTED\"}"));
        }
    }

    @Nested
    @DisplayName("TracedDecision Record")
    class TracedDecisionRecordTests {

        @Test
        @DisplayName("TracedDecision extracts AuthorizationDecision correctly")
        void whenTracedDecision_thenAuthorizationDecisionExtracted() {
            val subscription = subscriptionOf("test", "test", "test");
            val tracedValue  = evaluateWithSinglePolicy("""
                    policy "simple" permit
                    obligation "log"
                    """, subscription);

            val tracedDecision = new TracedDecision(tracedValue);

            assertThat(tracedDecision.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            assertThat(tracedDecision.currentDecision().decision()).isEqualTo(Decision.PERMIT);
            assertThat(tracedDecision.originalTrace()).isEqualTo(tracedValue);
            assertThat(tracedDecision.modifications()).isEmpty();
        }

        @Test
        @DisplayName("TracedDecision supports modification tracking")
        void whenModified_thenModificationTracked() {
            val subscription = subscriptionOf("test", "test", "test");
            val tracedValue  = evaluateWithSinglePolicy("""
                    policy "simple" permit
                    """, subscription);

            val original = new TracedDecision(tracedValue);
            val modified = original.modified(AuthorizationDecision.DENY, "Interceptor blocked access");

            assertThat(modified.originalTrace()).isEqualTo(tracedValue);
            assertThat(modified.currentDecision().decision()).isEqualTo(Decision.DENY);
            assertThat(modified.modifications()).containsExactly("Interceptor blocked access");
        }

        @Test
        @DisplayName("modifications accumulate correctly")
        void whenMultipleModifications_thenAllTracked() {
            val subscription = subscriptionOf("test", "test", "test");
            val tracedValue  = evaluateWithSinglePolicy("""
                    policy "simple" permit
                    """, subscription);

            val original  = new TracedDecision(tracedValue);
            val modified1 = original.modified(AuthorizationDecision.DENY, "First interceptor");
            val modified2 = modified1.modified(AuthorizationDecision.PERMIT, "Second interceptor override");

            assertThat(modified2.modifications()).containsExactly("First interceptor", "Second interceptor override");
            assertThat(modified2.currentDecision().decision()).isEqualTo(Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("Streaming via DynamicPolicyDecisionPoint")
    class StreamingPdpTests {

        @Test
        @DisplayName("decideTraced emits TracedDecision stream")
        void whenDecideTraced_thenEmitsTracedDecisions() {
            val subscription = subscriptionOf("watcher", "observe", "star-spawn");
            val pdp          = createPdpWithPolicy("""
                    policy "observation-permit" permit
                    """);

            StepVerifier.create(pdp.decideTraced(subscription).take(1)).assertNext(tracedDecision -> {
                printDecision("streaming PDP decision", tracedDecision.originalTrace());
                assertThat(tracedDecision.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                assertThat(getDecision(tracedDecision.originalTrace())).isEqualTo(Decision.PERMIT);
            }).verifyComplete();
        }

        @Test
        @DisplayName("decide emits AuthorizationDecision stream")
        void whenDecide_thenEmitsAuthorizationDecisions() {
            val subscription = subscriptionOf("guardian", "protect", "silver-key");
            val pdp          = createPdpWithPolicy("""
                    policy "guardian-duty" permit
                    obligation "remain-vigilant"
                    """);

            StepVerifier.create(pdp.decide(subscription).take(1)).assertNext(decision -> {
                assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
                assertThat(decision.obligations()).hasSize(1);
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("policy error produces indeterminate at PDP level")
        void whenPolicyError_thenIndeterminate() {
            val subscription = new AuthorizationSubscription(json("{\"divisor\": 0}"), Value.of("test"),
                    Value.of("test"), Value.UNDEFINED);
            val traced       = evaluateWithSinglePolicy("""
                    policy "runtime-error" permit where 1 / subject.divisor > 0;
                    """, subscription);

            printDecision("policy error", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("constraint error produces indeterminate")
        void whenConstraintError_thenIndeterminate() {
            val subscription = new AuthorizationSubscription(json("{\"divisor\": 0}"), Value.of("test"),
                    Value.of("test"), Value.UNDEFINED);
            val traced       = evaluateWithSinglePolicy("""
                    policy "broken-obligation" permit
                    obligation 1 / subject.divisor
                    """, subscription);

            printDecision("constraint error", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("Evaluation Paths")
    class EvaluationPathTests {

        @Test
        @DisplayName("empty documents produce PureExpression for traced PDP decision building")
        void whenNoDocuments_thenProducesPureExpression() {
            // Even empty document lists require PureExpression because building the traced
            // PDP decision requires access to EvaluationContext for pdpId, configurationId,
            // subscriptionId, timestamp, etc.
            val subscription = subscriptionOf("cultist", "summon", "shoggoth");
            val combined     = CombiningAlgorithmCompiler.denyOverridesPreMatched("deny-overrides", List.of());

            assertThat(combined).isInstanceOf(PureExpression.class);

            val evalCtx = createEvaluationContext(subscription);
            val traced  = ((PureExpression) combined).evaluate(evalCtx);
            printDecision("empty documents (PureExpression path)", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.NOT_APPLICABLE);
            assertThat(getDocuments(traced)).isEmpty();
        }

        @Test
        @DisplayName("constant policy produces PureExpression for runtime combining")
        void whenConstantPolicy_thenProducesPureExpression() {
            // Even constant policies require PureExpression because combining
            // happens at runtime (iterate, accumulate, build traced PDP decision)
            val subscription = subscriptionOf("cultist", "summon", "shoggoth");

            val compiled = compilePolicy("""
                    policy "constant-deny" deny
                    """);
            val combined = CombiningAlgorithmCompiler.denyOverridesPreMatched("deny-overrides", List.of(compiled));

            assertThat(combined).isInstanceOf(PureExpression.class);

            val evalCtx = createEvaluationContext(subscription);
            val traced  = ((PureExpression) combined).evaluate(evalCtx);
            printDecision("constant policy (PureExpression path)", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.DENY);
            assertThat(getDocuments(traced)).hasSize(1);
        }

        @Test
        @DisplayName("streaming policy produces StreamExpression at PDP level")
        void whenStreamingPolicy_thenProducesStreamExpression() {
            val compiled = compilePolicy("""
                    policy "dynamic-permit" permit
                    obligation subject.dynamicObligation
                    """);
            val combined = CombiningAlgorithmCompiler.denyOverridesPreMatched("deny-overrides", List.of(compiled));

            assertThat(combined).isInstanceOf(StreamExpression.class);

            val subject = json("{\"dynamicObligation\": \"maintain-sanity\"}");
            val evalCtx = EvaluationContext.of(TEST_PDP_ID, TEST_CONFIG_ID, TEST_SUBSCRIPTION_ID,
                    new AuthorizationSubscription(subject, Value.of("read"), Value.of("necronomicon"), Value.UNDEFINED),
                    Map.of(), context.getFunctionBroker(), context.getAttributeBroker());

            val streamExpr = (StreamExpression) combined;
            StepVerifier.create(streamExpr.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalCtx)))
                    .assertNext(traced -> {
                        printDecision("streaming policy (StreamExpression path)", traced);
                        assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
                        assertThat(getObligations(traced)).hasSize(1).first().isEqualTo(Value.of("maintain-sanity"));
                    }).thenCancel().verify();
        }

        private CompiledPolicy compilePolicy(String policyText) {
            val sapl = PARSER.parse(policyText);
            return SaplCompiler.compileDocument(sapl, context);
        }
    }

    // ========== Helper Methods ==========

    private Value evaluateWithSinglePolicy(String policyText, AuthorizationSubscription subscription) {
        return evaluateWithPolicies(List.of(policyText), subscription, DENY_OVERRIDES);
    }

    private Value evaluateWithPolicies(List<String> policyTexts, AuthorizationSubscription subscription,
            io.sapl.api.pdp.CombiningAlgorithm algorithm) {
        val compiledPolicies = policyTexts.stream().map(PARSER::parse)
                .map(sapl -> SaplCompiler.compileDocument(sapl, context)).toList();

        val algorithmName = toSaplSyntax(algorithm);

        val combinedExpression = switch (algorithm) {
        case DENY_OVERRIDES      -> CombiningAlgorithmCompiler.denyOverridesPreMatched(algorithmName, compiledPolicies);
        case PERMIT_OVERRIDES    ->
            CombiningAlgorithmCompiler.permitOverridesPreMatched(algorithmName, compiledPolicies);
        case DENY_UNLESS_PERMIT  ->
            CombiningAlgorithmCompiler.denyUnlessPermitPreMatched(algorithmName, compiledPolicies);
        case PERMIT_UNLESS_DENY  ->
            CombiningAlgorithmCompiler.permitUnlessDenyPreMatched(algorithmName, compiledPolicies);
        case ONLY_ONE_APPLICABLE ->
            CombiningAlgorithmCompiler.onlyOneApplicablePreMatched(algorithmName, compiledPolicies);
        };

        val evaluationContext = createEvaluationContext(subscription);
        return evaluateExpression(combinedExpression, evaluationContext);
    }

    private Value evaluateExpression(CompiledExpression expression, EvaluationContext evaluationContext) {
        return switch (expression) {
        case Value value                              -> value;
        case PureExpression pureExpression            -> pureExpression.evaluate(evaluationContext);
        case StreamExpression(Flux<Value> fluxStream) ->
            fluxStream.contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext)).blockFirst();
        };
    }

    private EvaluationContext createEvaluationContext(AuthorizationSubscription subscription) {
        return EvaluationContext.of(TEST_PDP_ID, TEST_CONFIG_ID, TEST_SUBSCRIPTION_ID, subscription, Map.of(),
                context.getFunctionBroker(), context.getAttributeBroker());
    }

    private DynamicPolicyDecisionPoint createPdpWithPolicy(String policyText) {
        val sapl           = PARSER.parse(policyText);
        val compiled       = SaplCompiler.compileDocument(sapl, context);
        val retrievalPoint = new TestPolicyRetrievalPoint(compiled);
        val config         = new CompiledPDPConfiguration(TEST_PDP_ID, TEST_CONFIG_ID, DENY_OVERRIDES, Map.of(),
                context.getFunctionBroker(), context.getAttributeBroker(), retrievalPoint);
        val configSource   = new TestConfigurationSource(config);
        return new DynamicPolicyDecisionPoint(configSource, idFactory);
    }

    private static AuthorizationSubscription subscriptionOf(String subject, String action, String resource) {
        return new AuthorizationSubscription(Value.of(subject), Value.of(action), Value.of(resource), Value.UNDEFINED);
    }

    private static String toSaplSyntax(io.sapl.api.pdp.CombiningAlgorithm algorithm) {
        return algorithm.name().toLowerCase().replace('_', '-');
    }

    private static void printDecision(String testName, Value traced) {
        if (!DEBUG) {
            return;
        }
        System.err.println("=== " + testName + " ===");
        System.err.println(toPrettyString(traced));
        System.err.println();
    }

    // ========== Test Support Classes ==========

    private record TestPolicyRetrievalPoint(CompiledPolicy policy) implements io.sapl.api.prp.PolicyRetrievalPoint {

        @Override
        public io.sapl.api.prp.PolicyRetrievalResult getMatchingDocuments(AuthorizationSubscription subscription,
                io.sapl.api.model.EvaluationContext context) {
            return new MatchingDocuments(List.of(policy));
        }
    }

    private record TestConfigurationSource(CompiledPDPConfiguration config)
            implements io.sapl.pdp.CompiledPDPConfigurationSource {

        @Override
        public reactor.core.publisher.Flux<java.util.Optional<CompiledPDPConfiguration>> getPDPConfigurations(
                String pdpId) {
            return reactor.core.publisher.Flux.just(java.util.Optional.of(config));
        }

        @Override
        public java.util.Optional<CompiledPDPConfiguration> getCurrentConfiguration(String pdpId) {
            return java.util.Optional.of(config);
        }
    }
}
