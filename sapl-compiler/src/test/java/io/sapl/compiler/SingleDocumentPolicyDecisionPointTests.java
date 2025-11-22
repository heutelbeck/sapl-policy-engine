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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SingleDocumentPolicyDecisionPointTests {

    private SingleDocumentPolicyDecisionPoint pdp;

    @BeforeEach
    void setUp() throws InitializationException {
        pdp = new SingleDocumentPolicyDecisionPoint();
    }

    @Test
    void decide_shouldThrowWhenNoDocumentLoaded() {
        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        assertThatThrownBy(() -> pdp.decide(subscription).blockFirst()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No policy document loaded");
    }

    @Test
    void decide_shouldReturnPermitForMatchingPolicy() {
        pdp.loadDocument("policy \"test\" permit");

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription)).expectNextMatches(decision -> {
            return decision.decision() == Decision.PERMIT && decision.obligations().isEmpty()
                    && decision.advice().isEmpty() && decision.resource() == Value.UNDEFINED;
        }).verifyComplete();
    }

    @Test
    void decide_shouldReturnDenyForMatchingPolicy() {
        pdp.loadDocument("policy \"test\" deny");

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription))
                .expectNextMatches(decision -> decision.decision() == Decision.DENY).verifyComplete();
    }

    @Test
    void decide_shouldReturnNotApplicableWhenTargetDoesNotMatch() {
        pdp.loadDocument("policy \"test\" permit subject == \"admin\"");

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription))
                .expectNextMatches(decision -> decision.decision() == Decision.NOT_APPLICABLE).verifyComplete();
    }

    @Test
    void decide_shouldReturnPermitWhenTargetMatches() {
        pdp.loadDocument("policy \"test\" permit subject == \"admin\"");

        val subscription = new AuthorizationSubscription(Value.of("admin"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription))
                .expectNextMatches(decision -> decision.decision() == Decision.PERMIT).verifyComplete();
    }

    @Test
    void decide_shouldReturnNotApplicableWhenWhereClauseIsFalse() {
        pdp.loadDocument("policy \"test\" permit where subject == \"admin\";");

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription))
                .expectNextMatches(decision -> decision.decision() == Decision.NOT_APPLICABLE).verifyComplete();
    }

    @Test
    void decide_shouldReturnPermitWhenWhereClauseIsTrue() {
        pdp.loadDocument("policy \"test\" permit where subject == \"admin\";");

        val subscription = new AuthorizationSubscription(Value.of("admin"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription))
                .expectNextMatches(decision -> decision.decision() == Decision.PERMIT).verifyComplete();
    }

    @Test
    void decide_shouldIncludeObligations() {
        pdp.loadDocument("policy \"test\" permit obligation \"log\"");

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription)).expectNextMatches(decision -> {
            return decision.decision() == Decision.PERMIT && decision.obligations().size() == 1
                    && decision.obligations().get(0).equals(Value.of("log"));
        }).verifyComplete();
    }

    @Test
    void decide_shouldIncludeAdvice() {
        pdp.loadDocument("policy \"test\" permit advice \"notify\"");

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription)).expectNextMatches(decision -> {
            return decision.decision() == Decision.PERMIT && decision.advice().size() == 1
                    && decision.advice().get(0).equals(Value.of("notify"));
        }).verifyComplete();
    }

    @Test
    void decide_shouldIncludeTransformation() {
        pdp.loadDocument("policy \"test\" permit transform \"transformed\"");

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription))
                .expectNextMatches(decision -> decision.decision() == Decision.PERMIT
                        && decision.resource().equals(Value.of("transformed")))
                .verifyComplete();
    }

    @Test
    void decide_shouldHandleComplexPolicy() {
        pdp.loadDocument("""
                policy "complex"
                permit action == "read"
                where subject.role == "admin";
                obligation "log_access"
                advice "notify_admin"
                transform resource.data
                """);

        val subject      = ObjectValue.builder().put("role", Value.of("admin")).build();
        val resource     = ObjectValue.builder().put("data", Value.of("sensitive")).build();
        val subscription = new AuthorizationSubscription(subject, Value.of("read"), resource, Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription)).expectNextMatches(decision -> {
            return decision.decision() == Decision.PERMIT && decision.obligations().contains(Value.of("log_access"))
                    && decision.advice().contains(Value.of("notify_admin"))
                    && decision.resource().equals(Value.of("sensitive"));
        }).verifyComplete();
    }

    @Test
    void decide_shouldReturnNotApplicableForComplexPolicyWithoutMatch() {
        pdp.loadDocument("""
                policy "complex"
                permit action == "write"
                where subject.role == "admin";
                """);

        val subject      = ObjectValue.builder().put("role", Value.of("admin")).build();
        val subscription = new AuthorizationSubscription(subject, Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription))
                .expectNextMatches(decision -> decision.decision() == Decision.NOT_APPLICABLE).verifyComplete();
    }

    @Test
    void decide_shouldHandleMultipleObligationsAndAdvice() {
        pdp.loadDocument("""
                policy "test"
                permit
                obligation "obl1"
                obligation "obl2"
                advice "adv1"
                advice "adv2"
                """);

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription)).expectNextMatches(decision -> {
            return decision.decision() == Decision.PERMIT && decision.obligations().size() == 2
                    && decision.advice().size() == 2;
        }).verifyComplete();
    }

    @Test
    void decide_shouldUseStandardFunctions() {
        pdp.loadDocument("""
                policy "test"
                permit action == "read"
                where filter.replace(subject, "admin") == "admin";
                """);

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription))
                .expectNextMatches(decision -> decision.decision() == Decision.PERMIT).verifyComplete();
    }

    @Test
    void decide_shouldUsePolicyInformationPoints() {
        pdp.loadDocument("""
                policy "test"
                permit action == "read"
                where
                    var currentTime = <time.now>;
                    currentTime != null;
                """);

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        // The time.now PIP emits a stream of values, so we expect multiple PERMIT
        // decisions
        StepVerifier.create(pdp.decide(subscription).take(1))
                .expectNextMatches(decision -> decision.decision() == Decision.PERMIT).verifyComplete();
    }

    @Test
    @Disabled
    void decide_shouldUsePolicyInformationPointsTimeTest() {
        pdp.loadDocument("""
                policy "test"
                permit action == "read"
                where
                    var currentTime = <time.now>;
                    time.secondOf(currentTime) % 2 == 0;
                """);

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        pdp.decide(subscription).take(10).doOnNext(System.err::println).blockLast();
        // The time.now PIP emits a stream of values, so we expect multiple PERMIT
        // decisions
        StepVerifier.create(pdp.decide(subscription).take(1))
                .expectNextMatches(decision -> decision.decision() == Decision.PERMIT).verifyComplete();
    }

    @Test
    @Disabled
    void decide_shouldUsePolicyInformationPointsTimeTest2() throws InterruptedException {
        pdp.loadDocument("""
                policy "test"
                permit action == "read"
                where
                    var x = subject;
                    var y = <time.now>;
                    time.secondOf(y) % 2 == 0;
                obligation
                    x
                """);

        val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);

        pdp.decide(subscription).take(10).doOnNext(System.err::println).subscribe();
        Thread.sleep(2000);
        val subscription2 = new AuthorizationSubscription(Value.of("user2"), Value.of("read"), Value.of("resource"),
                Value.UNDEFINED);
        pdp.decide(subscription2).take(10).doOnNext(System.err::println).blockLast();
    }
}
