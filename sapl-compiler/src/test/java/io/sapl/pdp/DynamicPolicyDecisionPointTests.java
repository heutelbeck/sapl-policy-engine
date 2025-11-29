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
package io.sapl.pdp;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.Decision;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.pdp.PdpTestHelper.configuration;
import static io.sapl.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * End-to-end tests for the DynamicPolicyDecisionPoint. These tests set up a
 * complete PDP with all dependencies, load
 * policies, and verify authorization decisions.
 */
class DynamicPolicyDecisionPointTests {

    private static final String DEFAULT_PDP_ID = "default";

    private ConfigurationRegister      configurationRegister;
    private DynamicPolicyDecisionPoint pdp;

    @BeforeEach
    void setUp() throws Exception {
        val components = PolicyDecisionPointBuilder.withoutDefaults().build();
        configurationRegister = components.configurationRegister();
        pdp                   = (DynamicPolicyDecisionPoint) components.pdp();
    }

    @Test
    void whenNoConfigurationLoaded_thenReturnIndeterminate() {
        val subscription = subscription("Nyarlathotep", "invoke", "outer_gods_council");

        StepVerifier.create(pdp.decide(subscription).take(1)).expectNext(AuthorizationDecision.INDETERMINATE)
                .verifyComplete();
    }

    @Test
    void whenSimplePermitPolicy_thenReturnPermit() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "allow all"
                permit
                """);

        val subscription = subscription("Cthulhu", "awaken", "rlyeh");

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();
    }

    @Test
    void whenSimpleDenyPolicy_thenReturnDeny() {
        loadConfiguration(CombiningAlgorithm.PERMIT_UNLESS_DENY, """
                policy "deny all"
                deny
                """);

        val subscription = subscription("Azathoth", "dream", "universe");

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_combiningAlgorithm_then_returnsExpectedDecision(String description, CombiningAlgorithm algorithm,
            List<String> policies, AuthorizationSubscription subscription, Decision expectedDecision) {
        loadConfiguration(algorithm, policies.toArray(new String[0]));

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(expectedDecision)).verifyComplete();
    }

    private static Stream<Arguments> when_combiningAlgorithm_then_returnsExpectedDecision() {
        val cultistSubscription      = subscription("cultist", "summon", "deep_one");
        val investigatorSubscription = subscription("investigator", "investigate", "innsmouth");

        return Stream.of(
                arguments("deny-unless-permit with permit returns PERMIT", CombiningAlgorithm.DENY_UNLESS_PERMIT,
                        List.of("policy \"permit summoning\" permit"), cultistSubscription, Decision.PERMIT),

                arguments("deny-unless-permit with no matching policies returns DENY",
                        CombiningAlgorithm.DENY_UNLESS_PERMIT,
                        List.of("policy \"never matches\" permit subject == \"elder_thing\""), cultistSubscription,
                        Decision.DENY),

                arguments("permit-unless-deny with deny returns DENY", CombiningAlgorithm.PERMIT_UNLESS_DENY,
                        List.of("policy \"deny investigation\" deny"), investigatorSubscription, Decision.DENY),

                arguments("permit-unless-deny with no matching policies returns PERMIT",
                        CombiningAlgorithm.PERMIT_UNLESS_DENY,
                        List.of("policy \"never matches\" deny subject == \"mi_go\""), investigatorSubscription,
                        Decision.PERMIT),

                arguments("deny-overrides with deny and permit returns DENY", CombiningAlgorithm.DENY_OVERRIDES,
                        List.of("policy \"permit access\" permit", "policy \"deny access\" deny"), cultistSubscription,
                        Decision.DENY),

                arguments("permit-overrides with deny and permit returns PERMIT", CombiningAlgorithm.PERMIT_OVERRIDES,
                        List.of("policy \"permit access\" permit", "policy \"deny access\" deny"), cultistSubscription,
                        Decision.PERMIT),

                arguments("only-one-applicable with single matching policy returns that decision",
                        CombiningAlgorithm.ONLY_ONE_APPLICABLE,
                        List.of("policy \"permit cultists\" permit subject == \"cultist\"",
                                "policy \"permit investigators\" permit subject == \"investigator\""),
                        cultistSubscription, Decision.PERMIT),

                arguments("only-one-applicable with multiple matching policies returns INDETERMINATE",
                        CombiningAlgorithm.ONLY_ONE_APPLICABLE,
                        List.of("policy \"permit all\" permit", "policy \"also permit all\" permit"),
                        cultistSubscription, Decision.INDETERMINATE));
    }

    @Test
    void whenPolicyWithTargetExpression_thenOnlyMatchingPoliciesApply() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT,
                "policy \"permit investigators\" permit subject == \"investigator\"",
                "policy \"deny cultists\" deny subject == \"cultist\"");

        val investigatorSubscription = subscription("investigator", "read", "necronomicon");
        val cultistSubscription      = subscription("cultist", "read", "necronomicon");

        StepVerifier.create(pdp.decide(investigatorSubscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        StepVerifier.create(pdp.decide(cultistSubscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();
    }

    @Test
    void whenPolicyWithObligations_thenObligationsIncludedInDecision() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "permit with obligation"
                permit
                obligation {"type": "log_access", "entity": "shoggoth"}
                """);

        val subscription = subscription("scientist", "observe", "shoggoth");

        StepVerifier.create(pdp.decide(subscription).take(1)).assertNext(decision -> {
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(decision.obligations()).hasSize(1);
            val obligation = (ObjectValue) decision.obligations().getFirst();
            assertThat(obligation.get("type")).isEqualTo(Value.of("log_access"));
            assertThat(obligation.get("entity")).isEqualTo(Value.of("shoggoth"));
        }).verifyComplete();
    }

    @Test
    void whenPolicyWithAdvice_thenAdviceIncludedInDecision() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "permit with advice"
                permit
                advice {"warning": "sanity_check_recommended"}
                """);

        val subscription = subscription("student", "study", "forbidden_tome");

        StepVerifier.create(pdp.decide(subscription).take(1)).assertNext(decision -> {
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(decision.advice()).hasSize(1);
            val advice = (ObjectValue) decision.advice().getFirst();
            assertThat(advice.get("warning")).isEqualTo(Value.of("sanity_check_recommended"));
        }).verifyComplete();
    }

    @Test
    void whenPolicyWithTransformation_thenResourceTransformed() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "permit with transformation"
                permit
                transform {"sanitized": true, "original_resource": resource}
                """);

        val subscription = subscription("archivist", "retrieve", "cursed_artifact");

        StepVerifier.create(pdp.decide(subscription).take(1)).assertNext(decision -> {
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(decision.resource()).isInstanceOf(ObjectValue.class);
            val resource = (ObjectValue) decision.resource();
            assertThat(resource.get("sanitized")).isEqualTo(Value.TRUE);
            assertThat(resource.get("original_resource")).isEqualTo(Value.of("cursed_artifact"));
        }).verifyComplete();
    }

    @Test
    void whenPolicyWithWhereClause_thenConditionEvaluated() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "permit only for specific action"
                permit
                where
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
    void whenPolicyAccessesSubscriptionFields_thenFieldsAvailable() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "permit based on environment"
                permit environment.location == "miskatonic_university"
                """);

        val universityEnvironment = ObjectValue.builder().put("location", Value.of("miskatonic_university")).build();
        val subscription          = new AuthorizationSubscription(Value.of("student"), Value.of("access"),
                Value.of("restricted_archive"), universityEnvironment);

        val outsideSubscription = new AuthorizationSubscription(Value.of("student"), Value.of("access"),
                Value.of("restricted_archive"), ObjectValue.builder().put("location", Value.of("arkham")).build());

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        StepVerifier.create(pdp.decide(outsideSubscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();
    }

    @Test
    void whenMultiplePoliciesWithObligations_thenObligationsCombined() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "permit with first obligation"
                permit
                obligation {"step": 1, "action": "verify_identity"}
                """, """
                policy "permit with second obligation"
                permit
                obligation {"step": 2, "action": "log_access"}
                """);

        val subscription = subscription("researcher", "access", "specimen");

        StepVerifier.create(pdp.decide(subscription).take(1)).assertNext(decision -> {
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(decision.obligations()).hasSize(2);
        }).verifyComplete();
    }

    @Test
    void whenConfigurationUpdated_thenNewConfigurationUsed() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "initial deny"
                deny
                """);

        val subscription = subscription("explorer", "enter", "tomb");

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();

        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "updated permit"
                permit
                """);

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();
    }

    @Test
    void whenPolicyWithVariableDefinition_thenVariableUsedInDecision() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "permit with local variable"
                permit
                where
                    var threshold = 5;
                    threshold > 3;
                transform {"threshold_used": threshold}
                """);

        val subscription = subscription("analyst", "query", "data");

        StepVerifier.create(pdp.decide(subscription).take(1)).assertNext(decision -> {
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            val resource = (ObjectValue) decision.resource();
            assertThat(resource.get("threshold_used")).isEqualTo(Value.of(5));
        }).verifyComplete();
    }

    @Test
    void whenComplexSubscriptionWithObjectSubject_thenObjectFieldsAccessible() {
        loadConfiguration(CombiningAlgorithm.DENY_UNLESS_PERMIT, """
                policy "permit based on subject role"
                permit subject.role == "elder_sign_bearer"
                """);

        val subject      = ObjectValue.builder().put("name", Value.of("Randolph Carter"))
                .put("role", Value.of("elder_sign_bearer")).build();
        val subscription = new AuthorizationSubscription(subject, Value.of("banish"), Value.of("horror"),
                Value.UNDEFINED);

        val unauthorizedSubject      = ObjectValue.builder().put("name", Value.of("Herbert West"))
                .put("role", Value.of("reanimator")).build();
        val unauthorizedSubscription = new AuthorizationSubscription(unauthorizedSubject, Value.of("banish"),
                Value.of("horror"), Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        StepVerifier.create(pdp.decide(unauthorizedSubscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();
    }

    private void loadConfiguration(CombiningAlgorithm algorithm, String... policies) {
        configurationRegister.loadConfiguration(configuration(algorithm, policies), false);
    }
}
