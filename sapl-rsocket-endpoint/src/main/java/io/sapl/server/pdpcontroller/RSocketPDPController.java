/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.pdpcontroller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
@RequiredArgsConstructor
public class RSocketPDPController {
    private final PolicyDecisionPoint pdp;

    /**
     * Delegates to {@link PolicyDecisionPoint#decide(AuthorizationSubscription)}.
     * 
     * @param authzSubscription the authorization subscription to be processed by
     *                          the PDP.
     * @return a flux emitting the current authorization decisions.
     * @see PolicyDecisionPoint#decide(AuthorizationSubscription)
     */
    @MessageMapping("decide")
    Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
        return pdp.decide(authzSubscription).onErrorResume(error -> Flux.just(AuthorizationDecision.INDETERMINATE));
    }

    /**
     * Delegates to {@link PolicyDecisionPoint#decide(AuthorizationSubscription)}.
     *
     * @param authzSubscription the authorization subscription to be processed by
     *                          the PDP.
     * @return a Mono for the initial decision.
     * @see PolicyDecisionPoint#decide(AuthorizationSubscription)
     */
    @MessageMapping("decide-once")
    public Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authzSubscription) {
        return pdp.decide(authzSubscription).onErrorResume(error -> Flux.just(AuthorizationDecision.INDETERMINATE))
                .next();
    }

    /**
     * Delegates to
     * {@link PolicyDecisionPoint#decide(MultiAuthorizationSubscription)}.
     * 
     * @param multiAuthzSubscription the authorization multi-subscription to be
     *                               processed by the PDP.
     * @return a flux emitting authorization decisions related to the individual
     *         subscriptions contained in the given {@code multiAuthzSubscription}
     *         as soon as they are available.
     * @see PolicyDecisionPoint#decide(MultiAuthorizationSubscription)
     */
    @MessageMapping("multi-decide")
    public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription) {
        return pdp.decide(multiAuthzSubscription)
                .onErrorResume(error -> Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE));
    }

    /**
     * Delegates to
     * {@link PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)}.
     * 
     * @param multiAuthzSubscription the authorization multi-subscription to be
     *                               processed by the PDP.
     * @return a flux emitting multi-decisions containing authorization decisions
     *         for all the individual authorization subscriptions contained in the
     *         given {@code multiAuthzSubscription}.
     * @see PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)
     */
    @MessageMapping("multi-decide-all")
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription) {
        return pdp.decideAll(multiAuthzSubscription)
                .onErrorResume(error -> Flux.just(MultiAuthorizationDecision.indeterminate()));
    }

    /**
     * Delegates to
     * {@link PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)}.
     *
     * @param multiAuthzSubscription the authorization multi-subscription to be
     *                               processed by the PDP.
     * @return a Mono emitting the initial multi-decision containing authorization
     *         decisions for all the individual authorization subscriptions
     *         contained in the given {@code multiAuthzSubscription}.
     * @see PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)
     */
    @MessageMapping("multi-decide-all-once")
    public Mono<MultiAuthorizationDecision> decideAllOnce(MultiAuthorizationSubscription multiAuthzSubscription) {
        return pdp.decideAll(multiAuthzSubscription)
                .onErrorResume(error -> Flux.just(MultiAuthorizationDecision.indeterminate())).next();
    }

}
