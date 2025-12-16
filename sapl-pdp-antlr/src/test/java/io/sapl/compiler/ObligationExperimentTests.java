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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import reactor.test.StepVerifier;

class ObligationExperimentTests {

    private static final String SINGLE_POLICY = """
            policy "test"
            permit
            obligation "A"
            """;

    private static final String POLICY_SET = """
            set "experiment"
            first-applicable

            policy "policy 1"
            permit action == "read"
            where
              subject == "WILLI";
            obligation "A"
            """;

    private SingleDocumentPolicyDecisionPoint pdp;

    @BeforeEach
    void setUp() {
        pdp = new SingleDocumentPolicyDecisionPoint();
    }

    @Test
    void singlePolicyReturnsObligation() {
        pdp.loadDocument(SINGLE_POLICY);

        var subscription = new AuthorizationSubscription(Value.of("WILLI"), Value.of("read"), Value.of("something"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription)).expectNextMatches(
                decision -> decision.decision() == Decision.PERMIT && decision.obligations().contains(Value.of("A")))
                .verifyComplete();
    }

    @Test
    void policySetReturnsObligation() {
        pdp.loadDocument(POLICY_SET);

        var subscription = new AuthorizationSubscription(Value.of("WILLI"), Value.of("read"), Value.of("something"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription)).expectNextMatches(
                decision -> decision.decision() == Decision.PERMIT && decision.obligations().contains(Value.of("A")))
                .verifyComplete();
    }

    @Test
    void policySetWithStreamingAttributeReturnsObligation() {
        var policySetWithStreaming = """
                set "experiment"
                first-applicable

                policy "policy 1"
                permit action == "read"
                where
                  subject == "WILLI";
                  time.secondOf(<time.now>) < 60;
                obligation "A"
                """;

        pdp.loadDocument(policySetWithStreaming);

        var subscription = new AuthorizationSubscription(Value.of("WILLI"), Value.of("read"), Value.of("something"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription).take(1)).expectNextMatches(
                decision -> decision.decision() == Decision.PERMIT && decision.obligations().contains(Value.of("A")))
                .verifyComplete();
    }

    @Test
    void firstApplicableWithMultipleStreamingPoliciesReturnsCorrectObligation() {
        var policy = """
                set "experiment"
                first-applicable

                policy "policy 1"
                permit action == "read"
                where
                  subject == "WILLI";
                  time.secondOf(<time.now>) < 20;
                obligation "A"

                policy "policy 2"
                permit action == "read"
                where
                  subject == "WILLI";
                  time.secondOf(<time.now>) < 40;
                obligation "B"

                policy "policy 3"
                permit action == "read"
                where
                  subject == "WILLI";
                  time.secondOf(<time.now>) < 60;
                obligation "C"
                """;

        pdp.loadDocument(policy);

        var subscription = new AuthorizationSubscription(Value.of("WILLI"), Value.of("read"), Value.of("something"),
                Value.UNDEFINED);

        StepVerifier.create(pdp.decide(subscription).take(1))
                .expectNextMatches(
                        decision -> decision.decision() == Decision.PERMIT && !decision.obligations().isEmpty())
                .verifyComplete();
    }
}
