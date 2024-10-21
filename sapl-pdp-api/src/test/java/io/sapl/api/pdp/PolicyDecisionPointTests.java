/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PolicyDecisionPointTests {

    @Test
    void decideOnce() {
        class SomePDP implements PolicyDecisionPoint {

            @Override
            public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
                return Flux.just(AuthorizationDecision.DENY, AuthorizationDecision.PERMIT);
            }

            @Override
            public Flux<IdentifiableAuthorizationDecision> decide(
                    MultiAuthorizationSubscription multiAuthzSubscription) {
                return Flux.empty();
            }

            @Override
            public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription) {
                return Flux.empty();
            }

        }

        final var pdp = new SomePDP();
        StepVerifier.create(pdp.decideOnce(mock(AuthorizationSubscription.class)))
                .expectNext(AuthorizationDecision.DENY).verifyComplete();
    }
}
