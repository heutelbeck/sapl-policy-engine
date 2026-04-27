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
package io.sapl.spring.pep.http.reactive;

import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;

import io.sapl.api.pdp.AuthorizationSubscription;
import reactor.core.publisher.Mono;

/**
 * Builds the {@link AuthorizationSubscription} that
 * {@link ReactiveSaplAuthorizationManager} sends to the PDP for one HTTP
 * exchange.
 * <p>
 * The default implementation
 * ({@link DefaultReactiveAuthorizationSubscriptionFactory}) places the
 * resolved {@link Authentication} on {@code subject} and the serialized
 * request on both {@code action} and {@code resource}, leaving
 * {@code environment} undefined. Applications register their own factory
 * bean of this type, or pass an inline factory through the SAPL reactive
 * configurer:
 *
 * <pre>{@code
 * SaplServerHttpSecurityConfigurer.apply(http, context,
 *         c -> c.subscriptionFactory((auth, exchange) -> Mono.just(AuthorizationSubscription.of(auth.getName(),
 *                 exchange.getRequest().getMethod().name(), exchange.getRequest().getURI().getPath(), mapper))));
 * }</pre>
 * <p>
 * Returning a {@link Mono} allows asynchronous enrichment (looking up
 * additional subject attributes from a reactive store, for example) without
 * blocking the event loop. The {@link Authentication} is never
 * {@code null}: an anonymous token is supplied when the security context
 * had no authentication.
 */
@FunctionalInterface
public interface ReactiveAuthorizationSubscriptionFactory {

    /**
     * Returns the {@link AuthorizationSubscription} to send to the PDP.
     *
     * @param authentication the resolved authentication (anonymous token
     * when the security context had no authentication).
     * @param exchange the inbound exchange.
     * @return a Mono emitting exactly one subscription.
     */
    Mono<AuthorizationSubscription> build(Authentication authentication, ServerWebExchange exchange);
}
