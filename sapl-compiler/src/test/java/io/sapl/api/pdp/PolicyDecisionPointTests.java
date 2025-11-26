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
package io.sapl.api.pdp;

import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the default methods in the PolicyDecisionPoint interface. Uses a
 * simple mock PDP that returns configurable
 * decisions per subscription.
 */
class PolicyDecisionPointTests {

    @Test
    void whenDecideOnce_thenReturnFirstDecision() {
        val pdp          = new MockPDP(AuthorizationDecision.PERMIT);
        val subscription = subscription("Cthulhu", "awaken", "rlyeh");

        StepVerifier.create(pdp.decideOnce(subscription)).expectNext(AuthorizationDecision.PERMIT).verifyComplete();
    }

    @Test
    void whenDecideMultiWithEmptySubscription_thenReturnIndeterminate() {
        val pdp               = new MockPDP(AuthorizationDecision.PERMIT);
        val multiSubscription = new MultiAuthorizationSubscription();

        StepVerifier.create(pdp.decide(multiSubscription)).expectNext(IdentifiableAuthorizationDecision.INDETERMINATE)
                .verifyComplete();
    }

    @Test
    void whenDecideAllWithEmptySubscription_thenReturnIndeterminate() {
        val pdp               = new MockPDP(AuthorizationDecision.PERMIT);
        val multiSubscription = new MultiAuthorizationSubscription();

        StepVerifier.create(pdp.decideAll(multiSubscription)).assertNext(decision -> {
            assertThat(decision.size()).isEqualTo(1);
            assertThat(decision.getDecision("")).isEqualTo(AuthorizationDecision.INDETERMINATE);
        }).verifyComplete();
    }

    @Test
    void whenDecideMultiWithSingleSubscription_thenReturnIdentifiableDecision() {
        val pdp               = new MockPDP(AuthorizationDecision.PERMIT);
        val multiSubscription = new MultiAuthorizationSubscription().addSubscription("read-necronomicon",
                subscription("investigator", "read", "necronomicon"));

        StepVerifier.create(pdp.decide(multiSubscription).take(1)).assertNext(decision -> {
            assertThat(decision.subscriptionId()).isEqualTo("read-necronomicon");
            assertThat(decision.decision()).isEqualTo(AuthorizationDecision.PERMIT);
        }).verifyComplete();
    }

    @Test
    void whenDecideMultiWithMultipleSubscriptions_thenReturnAllDecisions() {
        val pdp = new ConfigurableMockPDP().withDecision("read-tome", AuthorizationDecision.PERMIT)
                .withDecision("write-tome", AuthorizationDecision.DENY);

        val multiSubscription = new MultiAuthorizationSubscription()
                .addSubscription("read-tome", subscription("scholar", "read", "forbidden_tome"))
                .addSubscription("write-tome", subscription("scholar", "write", "forbidden_tome"));

        StepVerifier.create(pdp.decide(multiSubscription).take(2).collectList()).assertNext(decisions -> {
            assertThat(decisions).hasSize(2);
            val decisionMap = decisions.stream().collect(java.util.stream.Collectors.toMap(
                    IdentifiableAuthorizationDecision::subscriptionId, IdentifiableAuthorizationDecision::decision));
            assertThat(decisionMap.get("read-tome")).isEqualTo(AuthorizationDecision.PERMIT);
            assertThat(decisionMap.get("write-tome")).isEqualTo(AuthorizationDecision.DENY);
        }).verifyComplete();
    }

    @Test
    void whenDecideAllWithSingleSubscription_thenReturnMultiDecision() {
        val pdp               = new MockPDP(AuthorizationDecision.DENY);
        val multiSubscription = new MultiAuthorizationSubscription().addSubscription("summon-shoggoth",
                subscription("cultist", "summon", "shoggoth"));

        StepVerifier.create(pdp.decideAll(multiSubscription).take(1)).assertNext(multiDecision -> {
            assertThat(multiDecision.size()).isEqualTo(1);
            assertThat(multiDecision.getDecision("summon-shoggoth")).isEqualTo(AuthorizationDecision.DENY);
            assertThat(multiDecision.isPermitted("summon-shoggoth")).isFalse();
        }).verifyComplete();
    }

    @Test
    void whenDecideAllWithMultipleSubscriptions_thenWaitForAllAndBundle() {
        val pdp = new ConfigurableMockPDP().withDecision("enter-dunwich", AuthorizationDecision.PERMIT)
                .withDecision("enter-innsmouth", AuthorizationDecision.DENY)
                .withDecision("enter-arkham", AuthorizationDecision.PERMIT);

        val multiSubscription = new MultiAuthorizationSubscription()
                .addSubscription("enter-dunwich", subscription("traveler", "enter", "dunwich"))
                .addSubscription("enter-innsmouth", subscription("traveler", "enter", "innsmouth"))
                .addSubscription("enter-arkham", subscription("traveler", "enter", "arkham"));

        StepVerifier.create(pdp.decideAll(multiSubscription).take(1)).assertNext(multiDecision -> {
            assertThat(multiDecision.size()).isEqualTo(3);
            assertThat(multiDecision.isPermitted("enter-dunwich")).isTrue();
            assertThat(multiDecision.isPermitted("enter-innsmouth")).isFalse();
            assertThat(multiDecision.isPermitted("enter-arkham")).isTrue();
            assertThat(multiDecision.getDecisionType("enter-innsmouth")).isEqualTo(Decision.DENY);
        }).verifyComplete();
    }

    @Test
    void whenDecideAllWithUpdatingDecisions_thenEmitNewBundles() {
        val pdp               = new UpdatingMockPDP();
        val multiSubscription = new MultiAuthorizationSubscription().addSubscription("watch-stars",
                subscription("astronomer", "observe", "stars"));

        StepVerifier.create(pdp.decideAll(multiSubscription).take(2))
                .assertNext(decision -> assertThat(decision.getDecisionType("watch-stars")).isEqualTo(Decision.DENY))
                .assertNext(decision -> assertThat(decision.getDecisionType("watch-stars")).isEqualTo(Decision.PERMIT))
                .verifyComplete();
    }

    @Test
    void whenDecideMultiWithUpdatingDecisions_thenEmitEachUpdate() {
        val pdp               = new UpdatingMockPDP();
        val multiSubscription = new MultiAuthorizationSubscription().addSubscription("gaze-abyss",
                subscription("philosopher", "gaze", "abyss"));

        StepVerifier.create(pdp.decide(multiSubscription).take(2)).assertNext(decision -> {
            assertThat(decision.subscriptionId()).isEqualTo("gaze-abyss");
            assertThat(decision.decision().decision()).isEqualTo(Decision.DENY);
        }).assertNext(decision -> {
            assertThat(decision.subscriptionId()).isEqualTo("gaze-abyss");
            assertThat(decision.decision().decision()).isEqualTo(Decision.PERMIT);
        }).verifyComplete();
    }

    private static AuthorizationSubscription subscription(String subject, String action, String resource) {
        return new AuthorizationSubscription(Value.of(subject), Value.of(action), Value.of(resource), Value.UNDEFINED);
    }

    /**
     * Simple mock PDP that returns the same decision for all subscriptions.
     */
    private static class MockPDP implements PolicyDecisionPoint {
        private final AuthorizationDecision decision;

        MockPDP(AuthorizationDecision decision) {
            this.decision = decision;
        }

        @Override
        public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
            return Flux.just(decision);
        }
    }

    /**
     * Mock PDP that returns different decisions based on subscription ID. The
     * subscription ID is derived from the
     * resource field for simplicity.
     */
    private static class ConfigurableMockPDP implements PolicyDecisionPoint {
        private final Map<String, AuthorizationDecision> decisions = new ConcurrentHashMap<>();

        ConfigurableMockPDP withDecision(String subscriptionId, AuthorizationDecision decision) {
            decisions.put(subscriptionId, decision);
            return this;
        }

        @Override
        public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
            return Flux.just(AuthorizationDecision.INDETERMINATE);
        }

        @Override
        public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription) {
            return Flux.fromIterable(multiSubscription)
                    .map(sub -> new IdentifiableAuthorizationDecision(sub.subscriptionId(),
                            decisions.getOrDefault(sub.subscriptionId(), AuthorizationDecision.INDETERMINATE)));
        }

        @Override
        public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription) {
            var multiDecision = new MultiAuthorizationDecision();
            for (var sub : multiSubscription) {
                multiDecision.setDecision(sub.subscriptionId(),
                        decisions.getOrDefault(sub.subscriptionId(), AuthorizationDecision.INDETERMINATE));
            }
            return Flux.just(multiDecision);
        }
    }

    /**
     * Mock PDP that emits multiple decisions over time (DENY then PERMIT).
     */
    private static class UpdatingMockPDP implements PolicyDecisionPoint {
        @Override
        public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
            return Flux.just(AuthorizationDecision.DENY, AuthorizationDecision.PERMIT)
                    .delayElements(Duration.ofMillis(10));
        }
    }
}
