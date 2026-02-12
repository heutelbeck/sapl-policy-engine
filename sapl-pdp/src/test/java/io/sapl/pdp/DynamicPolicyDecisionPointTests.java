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
package io.sapl.pdp;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.document.TimestampedVote;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.sapl.pdp.PdpTestHelper.configuration;
import static io.sapl.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * End-to-end tests for the DynamicPolicyDecisionPoint. These tests set up a
 * complete PDP with all dependencies, load policies, and verify authorization
 * decisions.
 */
@DisplayName("DynamicPolicyDecisionPoint")
class DynamicPolicyDecisionPointTests {

    // Commonly used combining algorithm configurations
    private static final CombiningAlgorithm DENY_UNLESS_PERMIT  = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.DENY, ErrorHandling.ABSTAIN);
    private static final CombiningAlgorithm PERMIT_UNLESS_DENY  = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.PERMIT, ErrorHandling.ABSTAIN);
    private static final CombiningAlgorithm DENY_OVERRIDES      = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm PERMIT_OVERRIDES    = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm ONLY_ONE_APPLICABLE = new CombiningAlgorithm(VotingMode.UNIQUE,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    private PdpVoterSource             pdpVoterSource;
    private DynamicPolicyDecisionPoint pdp;

    @BeforeEach
    void setUp() throws Exception {
        val components = PolicyDecisionPointBuilder.withoutDefaults().build();
        pdpVoterSource = components.pdpVoterSource();
        pdp            = (DynamicPolicyDecisionPoint) components.pdp();
    }

    @Test
    @DisplayName("no configuration loaded returns INDETERMINATE")
    void whenNoConfigurationLoadedThenReturnIndeterminate() {
        val subscription = subscription("Nyarlathotep", "invoke", "outer_gods_council");

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.INDETERMINATE))
                .verifyComplete();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void whenCombiningAlgorithmThenReturnsExpectedDecision(String description, CombiningAlgorithm algorithm,
            List<String> policies, AuthorizationSubscription subscription, Decision expectedDecision) {
        loadConfiguration(algorithm, policies.toArray(new String[0]));

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(expectedDecision)).verifyComplete();
    }

    private static Stream<Arguments> whenCombiningAlgorithmThenReturnsExpectedDecision() {
        val cultistSubscription      = subscription("cultist", "summon", "deep_one");
        val investigatorSubscription = subscription("investigator", "investigate", "innsmouth");

        return Stream.of(arguments("simple permit policy returns PERMIT", DENY_UNLESS_PERMIT, List.of("""
                policy "allow all"
                permit
                """), cultistSubscription, Decision.PERMIT),

                arguments("simple deny policy returns DENY", PERMIT_UNLESS_DENY, List.of("""
                        policy "deny all"
                        deny
                        """), cultistSubscription, Decision.DENY),

                arguments("deny-unless-permit with permit returns PERMIT", DENY_UNLESS_PERMIT, List.of("""
                        policy "permit summoning"
                        permit
                        """), cultistSubscription, Decision.PERMIT),

                arguments("deny-unless-permit with no matching policies returns DENY", DENY_UNLESS_PERMIT, List.of("""
                        policy "never matches"
                        permit
                            subject == "elder_thing";
                        """), cultistSubscription, Decision.DENY),

                arguments("permit-unless-deny with deny returns DENY", PERMIT_UNLESS_DENY, List.of("""
                        policy "deny investigation"
                        deny
                        """), investigatorSubscription, Decision.DENY),

                arguments("permit-unless-deny with no matching policies returns PERMIT", PERMIT_UNLESS_DENY, List.of("""
                        policy "never matches"
                        deny
                            subject == "mi_go";
                        """), investigatorSubscription, Decision.PERMIT),

                arguments("deny-overrides with deny and permit returns DENY", DENY_OVERRIDES, List.of("""
                        policy "permit access"
                        permit
                        """, """
                        policy "deny access"
                        deny
                        """), cultistSubscription, Decision.DENY),

                arguments("permit-overrides with deny and permit returns PERMIT", PERMIT_OVERRIDES, List.of("""
                        policy "permit access"
                        permit
                        """, """
                        policy "deny access"
                        deny
                        """), cultistSubscription, Decision.PERMIT),

                arguments("only-one-applicable with single matching policy returns that decision", ONLY_ONE_APPLICABLE,
                        List.of("""
                                policy "permit cultists"
                                permit
                                    subject == "cultist";
                                """, """
                                policy "permit investigators"
                                permit
                                    subject == "investigator";
                                """), cultistSubscription, Decision.PERMIT),

                arguments("only-one-applicable with multiple matching policies returns INDETERMINATE",
                        ONLY_ONE_APPLICABLE, List.of("""
                                policy "permit all"
                                permit
                                """, """
                                policy "also permit all"
                                permit
                                """), cultistSubscription, Decision.INDETERMINATE));
    }

    @Test
    @DisplayName("target expressions restrict policy applicability")
    void whenPolicyWithTargetExpressionThenOnlyMatchingPoliciesApply() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit investigators"
                permit
                    subject == "investigator";
                """, """
                policy "deny cultists"
                deny
                    subject == "cultist";
                """);

        val investigatorSubscription = subscription("investigator", "read", "necronomicon");
        val cultistSubscription      = subscription("cultist", "read", "necronomicon");

        StepVerifier.create(pdp.decide(investigatorSubscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        StepVerifier.create(pdp.decide(cultistSubscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();
    }

    @Test
    @DisplayName("obligations are included in the decision")
    void whenPolicyWithObligationsThenObligationsIncludedInDecision() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit with obligation"
                permit
                obligation {"type": "log_access", "entity": "shoggoth"}
                """);

        val subscription = subscription("scientist", "observe", "shoggoth");

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision).satisfies(d -> {
                    assertThat(d.decision()).isEqualTo(Decision.PERMIT);
                    assertThat(d.obligations()).hasSize(1).first()
                            .satisfies(first -> assertThat((ObjectValue) first)
                                    .containsEntry("type", Value.of("log_access"))
                                    .containsEntry("entity", Value.of("shoggoth")));
                })).verifyComplete();
    }

    @Test
    @DisplayName("advice is included in the decision")
    void whenPolicyWithAdviceThenAdviceIncludedInDecision() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit with advice"
                permit
                advice {"warning": "sanity_check_recommended"}
                """);

        val subscription = subscription("student", "study", "forbidden_tome");

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision).satisfies(d -> {
                    assertThat(d.decision()).isEqualTo(Decision.PERMIT);
                    assertThat(d.advice()).hasSize(1).first().satisfies(first -> assertThat((ObjectValue) first)
                            .containsEntry("warning", Value.of("sanity_check_recommended")));
                })).verifyComplete();
    }

    @Test
    @DisplayName("transformation replaces the resource in the decision")
    void whenPolicyWithTransformationThenResourceTransformed() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit with transformation"
                permit
                transform {"sanitized": true, "original_resource": resource}
                """);

        val subscription = subscription("archivist", "retrieve", "cursed_artifact");

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision).satisfies(d -> {
                    assertThat(d.decision()).isEqualTo(Decision.PERMIT);
                    assertThat(d.resource()).isInstanceOf(ObjectValue.class);
                    assertThat((ObjectValue) d.resource()).containsEntry("sanitized", Value.TRUE)
                            .containsEntry("original_resource", Value.of("cursed_artifact"));
                })).verifyComplete();
    }

    @Test
    @DisplayName("body conditions filter applicable policies")
    void whenPolicyWithWhereClauseThenConditionEvaluated() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit only for specific action"
                permit
                    action == "read";
                """);

        val readSubscription  = subscription("reader", "read", "scroll");
        val writeSubscription = subscription("reader", "write", "scroll");

        StepVerifier.create(pdp.decide(readSubscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        StepVerifier.create(pdp.decide(writeSubscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();
    }

    @Test
    @DisplayName("environment fields are accessible in policy conditions")
    void whenPolicyAccessesSubscriptionFieldsThenFieldsAvailable() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit based on environment"
                permit
                    environment.location == "miskatonic_university";
                """);

        val universityEnvironment = ObjectValue.builder().put("location", Value.of("miskatonic_university")).build();
        val subscription          = new AuthorizationSubscription(Value.of("student"), Value.of("access"),
                Value.of("restricted_archive"), universityEnvironment, Value.EMPTY_OBJECT);

        val outsideSubscription = new AuthorizationSubscription(Value.of("student"), Value.of("access"),
                Value.of("restricted_archive"), ObjectValue.builder().put("location", Value.of("arkham")).build(),
                Value.EMPTY_OBJECT);

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        StepVerifier.create(pdp.decide(outsideSubscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();
    }

    @Test
    @DisplayName("obligations from multiple matching policies are combined")
    void whenMultiplePoliciesWithObligationsThenObligationsCombined() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit with first obligation"
                permit
                obligation {"step": 1, "action": "verify_identity"}
                """, """
                policy "permit with second obligation"
                permit
                obligation {"step": 2, "action": "log_access"}
                """);

        val subscription = subscription("researcher", "access", "specimen");

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision).satisfies(d -> {
                    assertThat(d.decision()).isEqualTo(Decision.PERMIT);
                    assertThat(d.obligations()).hasSize(2);
                })).verifyComplete();
    }

    @Test
    @DisplayName("hot-reloaded configuration is used for subsequent decisions")
    void whenConfigurationUpdatedThenNewConfigurationUsed() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "initial deny"
                deny
                """);

        val subscription = subscription("explorer", "enter", "tomb");

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();

        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "updated permit"
                permit
                """);

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();
    }

    @Test
    @DisplayName("local variables are available in conditions and transformations")
    void whenPolicyWithVariableDefinitionThenVariableUsedInDecision() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit with local variable"
                permit
                    var threshold = 5;
                    threshold > 3;
                transform {"threshold_used": threshold}
                """);

        val subscription = subscription("analyst", "query", "data");

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision).satisfies(d -> {
                    assertThat(d.decision()).isEqualTo(Decision.PERMIT);
                    assertThat((ObjectValue) d.resource()).containsEntry("threshold_used", Value.of(5));
                })).verifyComplete();
    }

    @Test
    @DisplayName("object subject fields are accessible in policy conditions")
    void whenComplexSubscriptionWithObjectSubjectThenObjectFieldsAccessible() {
        loadConfiguration(DENY_UNLESS_PERMIT, """
                policy "permit based on subject role"
                permit
                    subject.role == "elder_sign_bearer";
                """);

        val subject      = ObjectValue.builder().put("name", Value.of("Randolph Carter"))
                .put("role", Value.of("elder_sign_bearer")).build();
        val subscription = new AuthorizationSubscription(subject, Value.of("banish"), Value.of("horror"),
                Value.UNDEFINED, Value.EMPTY_OBJECT);

        val unauthorizedSubject      = ObjectValue.builder().put("name", Value.of("Herbert West"))
                .put("role", Value.of("reanimator")).build();
        val unauthorizedSubscription = new AuthorizationSubscription(unauthorizedSubject, Value.of("banish"),
                Value.of("horror"), Value.UNDEFINED, Value.EMPTY_OBJECT);

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        StepVerifier.create(pdp.decide(unauthorizedSubscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();
    }

    @Test
    @DisplayName("lifecycle callbacks fire on subscribe and unsubscribe")
    void whenStreamSubscribedAndCancelledThenLifecycleCallbacksFire() {
        val subscribedIds   = new ArrayList<String>();
        val unsubscribedIds = new ArrayList<String>();

        val lifecycleInterceptor = new VoteInterceptor() {
            @Override
            public int priority() {
                return 0;
            }

            @Override
            public void intercept(TimestampedVote vote, String subscriptionId,
                    AuthorizationSubscription authorizationSubscription) {
                // no-op
            }

            @Override
            public void onSubscribe(String subscriptionId, AuthorizationSubscription authorizationSubscription) {
                subscribedIds.add(subscriptionId);
            }

            @Override
            public void onUnsubscribe(String subscriptionId) {
                unsubscribedIds.add(subscriptionId);
            }
        };

        val components     = PolicyDecisionPointBuilder.withoutDefaults().withInterceptor(lifecycleInterceptor).build();
        val interceptedPdp = (DynamicPolicyDecisionPoint) components.pdp();
        val voterSource    = components.pdpVoterSource();

        voterSource.loadConfiguration(configuration(DENY_UNLESS_PERMIT, """
                policy "allow all"
                permit
                """), false);

        val subscription = subscription("test", "read", "data");

        StepVerifier.create(interceptedPdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        assertThat(subscribedIds).hasSize(1);
        assertThat(unsubscribedIds).hasSize(1).first().isEqualTo(subscribedIds.getFirst());
    }

    private void loadConfiguration(CombiningAlgorithm algorithm, String... policies) {
        pdpVoterSource.loadConfiguration(configuration(algorithm, policies), false);
    }
}
